"""
BCG noturno (balistocardiografia) — o sinal que o celular sozinho NÃO captura.

Por que existe: à noite ninguém segura celular nem fone; um pad sob o colchão
(acelerômetro/load cell barato, ex.: ESP32 + ADXL345) capta o recuo mecânico do
corpo a cada batimento (BCG) e o vaivém da respiração. Tendência de FC/FR
noturna é o sinal clássico de descompensação de insuficiência cardíaca e de
infecção no idoso ANTES dos sintomas.

Este módulo valida a MECÂNICA do processamento de sinal (o que viveria no app
ou no firmware do pad): separar FR (0,1-0,5 Hz) e FC (0,7-3 Hz) de um sinal
com template de complexo IJK + deriva + ruído. Precisão clínica exige pad real
(calibração mecânica do colchão), não este simulador.
"""
from __future__ import annotations

import numpy as np

FC_BAND = (0.7, 3.0)    # 42-180 bpm
FR_BAND = (0.10, 0.50)  # 6-30 irpm


def template_bcg(t: np.ndarray) -> np.ndarray:
    """Forma de onda do complexo I-J do BCG como chega sob o colchão.

    Um BCG "cru" tem J-wave afiada (σ≈30 ms), mas colchão+espuma atuam como
    filtro passa-baixa mecânico: o pad sob o colchão vê o batimento ALARGADO.
    Simulamos isso com Gaussianas largas — assim o fundamental domina, como no
    pad real (BCG cru no ar teria 2a harmônica mais forte que o fundamental).
    """
    return (np.exp(-(((t - 0.14) / 0.075) ** 2)) -
            0.5 * np.exp(-(((t - 0.30) / 0.110) ** 2)))


def gerar_sinal_bcg(fc_bpm: float = 64.0, fr_irpm: float = 14.0,
                    segundos: float = 300.0, fs: float = 100.0,
                    ruido: float = 0.15, semente: int = 1) -> np.ndarray:
    rng = np.random.default_rng(semente)
    n = int(segundos * fs)
    t = np.arange(n) / fs
    sinal = 0.8 * np.sin(2 * np.pi * fr_irpm / 60 * t)            # respiração
    sinal += 0.15 * np.sin(2 * np.pi * 0.02 * t)                  # deriva lenta
    periodo = 60.0 / fc_bpm
    tb = 0.0
    while tb < segundos:
        idx = (t >= tb) & (t < tb + 0.6)
        sinal[idx] += template_bcg(t[idx] - tb)
        tb += periodo * (1 + 0.02 * np.sin(2 * np.pi * 0.05 * tb))  # VFC leve
    return sinal + rng.normal(0, ruido, n)


def _pico_banda(sinal: np.ndarray, fs: float, banda: tuple[float, float],
                subharmonico: bool = False) -> tuple[float, float]:
    n = len(sinal)
    sinal = sinal - np.mean(sinal)
    esp = np.abs(np.fft.rfft(sinal * np.hanning(n))) ** 2
    f = np.fft.rfftfreq(n, 1 / fs)
    b = (f >= banda[0]) & (f <= banda[1])
    if b.sum() < 4:
        return 0.0, 0.0
    i = int(np.argmax(esp[b]))
    fb, pb = f[b], esp[b]
    if 0 < i < len(pb) - 1:
        y0, y1, y2 = np.log(pb[i - 1] + 1e-12), np.log(pb[i] + 1e-12), np.log(pb[i + 1] + 1e-12)
        den = y0 - 2 * y1 + y2
        d = 0.5 * (y0 - y2) / den if abs(den) > 1e-12 else 0.0
        f_pico = fb[i] + d * (fb[1] - fb[0])
    else:
        f_pico = fb[i]
    # ambiguidade clássica do BCG: o pico pode ser a 2a harmônica (FC 116 quando
    # a real é 58). Se f/2 está dentro da faixa e tem >=40% da potência do pico,
    # preferimos a fundamental.
    if subharmonico and f_pico / 2 >= banda[0]:
        pot_pico = float(pb[np.abs(fb - f_pico) <= 0.08].sum())
        pot_sub = float(pb[np.abs(fb - f_pico / 2) <= 0.08].sum())
        if pot_sub >= 0.4 * pot_pico:
            f_pico = f_pico / 2
    perto = np.abs(fb - f_pico) <= 0.08
    # ruído de referência = MEDIANA do espectro, não a soma fora do pico: o BCG
    # tem harmônicos esparsos dentro da banda (2x, 3x o batimento) que, somados,
    # inflavam o "ruído" e derrubavam o SNR de um sinal perfeitamente periódico.
    snr = float(pb[perto].sum() / (np.median(pb) * max(1, perto.sum()) + 1e-12))
    return float(f_pico), snr


def estimar_noturno(sinal: np.ndarray, fs: float = 100.0, min_segundos: float = 120.0) -> dict:
    if len(sinal) / fs < min_segundos:
        return {"ok": False, "motivo": f"janela curta demais (<{min_segundos:.0f} s)"}
    f_fc, snr_fc = _pico_banda(sinal, fs, FC_BAND, subharmonico=True)
    f_fr, snr_fr = _pico_banda(sinal, fs, FR_BAND)
    ok = snr_fc >= 3 and snr_fr >= 3 and f_fc > 0 and f_fr > 0
    return {
        "ok": bool(ok),
        "fc_bpm": round(f_fc * 60, 1),
        "fr_irpm": round(f_fr * 60, 1),
        "snr_fc": round(snr_fc, 2),
        "snr_fr": round(snr_fr, 2),
        "motivo": None if ok else "SNR insuficiente (colchão/posição sem sinal útil)",
    }
