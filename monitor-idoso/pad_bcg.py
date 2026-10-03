"""
Quadro do pad noturno (ESP32 + ADXL345) — espelho de PadBcg.kt.

0xAA 0x55 | versão u8 = 1 | flags u8 | seq u16 | fs_hz u16 | n u16
| n amostras int16 do eixo Z, 256 LSB = 1 g.
"""
from __future__ import annotations

import struct


def codificar_quadro(seq: int, fs_hz: int, amostras_g) -> bytes:
    crus = [max(-32768, min(32767, int(round(g * 256.0)))) for g in amostras_g]
    cab = struct.pack("<BBBBHHH", 0xAA, 0x55, 1, 0, seq, fs_hz, len(crus))
    return cab + struct.pack("<" + "h" * len(crus), *crus)


def decodificar_quadro(dados: bytes) -> dict | None:
    if len(dados) < 10 or dados[0:2] != b"\xaa\x55" or dados[2] != 1:
        return None
    seq, fs, n = struct.unpack_from("<HHH", dados, 4)
    if n <= 0 or fs <= 0 or len(dados) < 10 + n * 2:
        return None
    crus = struct.unpack_from("<" + "h" * n, dados, 10)
    return {"seq": seq, "fs_hz": fs, "amostras_g": [v / 256.0 for v in crus]}


def detectar_saida_leito(sinal, fs: float = 100.0, janela_s: float = 20.0) -> dict:
    """Mesma regra de sensores.detectar_saida_leito, sobre o eixo já em g."""
    sinal = [float(v) for v in sinal]
    n = int(janela_s * fs)
    if n <= 0 or len(sinal) < n * 2:
        return {"silencios": [], "maior_silencio_s": 0.0}
    quadros = len(sinal) // n
    energia = []
    for i in range(quadros):
        trecho = sinal[i * n:(i + 1) * n]
        energia.append((sum(v * v for v in trecho) / n) ** 0.5)
    limiar = 0.05 * max(energia)
    silencioso = [e < limiar for e in energia]
    silencios, ini = [], None
    for i, s in enumerate(silencioso):
        if s and ini is None:
            ini = i
        elif not s and ini is not None:
            silencios.append((round(ini * janela_s, 1), round(i * janela_s, 1)))
            ini = None
    if ini is not None:
        silencios.append((round(ini * janela_s, 1), round(quadros * janela_s, 1)))
    maior = max((f - i for i, f in silencios), default=0.0)
    return {"silencios": silencios, "maior_silencio_s": float(maior)}
