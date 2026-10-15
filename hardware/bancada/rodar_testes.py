#!/usr/bin/env python3
"""
Bancada de simulação do nó de leito.

Compila o firmware REAL (hardware/firmware_leito_esp32.ino) contra stubs das
bibliotecas do Arduino, alimenta com uma série de peso sintética e confere o
que a placa publica. Serve para duas coisas:

1. provar que as janelas de tempo e o cálculo de rpm batem com a especificação
   sem precisar de placa, célula de carga ou peso real;
2. servir de teste de regressão: se alguém mexer na matemática do firmware e
   quebrar o alerta, este script falha.

Uso:
    python3 hardware/bancada/rodar_testes.py

Opcional: passe o caminho de outro .ino para comparar com uma versão antiga:
    python3 hardware/bancada/rodar_testes.py caminho/do/firmware_antigo.ino
"""
import math
import os
import re
import subprocess
import sys
import tempfile

AQUI = os.path.dirname(os.path.abspath(__file__))
HARDWARE = os.path.dirname(AQUI)
FW_NOVO = os.path.join(HARDWARE, "firmware_leito_esp32.ino")

# Período de amostragem do driver e atraso simulado da leitura do HX711.
# O laço real dura leitura + delay(100), então uns 250 ms.
# O laço do firmware dura leitura do HX711 + delay(100). A série sintética
# precisa ser gerada no MESMO período, senão o relógio virtual anda mais
# rápido que o tempo da série e toda a conta de atraso sai errada.
LEITURA_MS = 150
PASSO_MS = LEITURA_MS + 100

LIMIAR_KG = 20.0          # igual ao firmware
JANELA_LEVANTOU_MS = 15000
JANELA_RESPIRACAO_MS = 20000
TOLERANCIA_S = 5.0        # folga sobre a especificação, para rede e jitter


def serie(rpm, segundos, levantar_em=None, rede_sempre=True):
    """Gera a série de peso sintética: 70 kg com oscilação de respiração."""
    n = int(segundos * 1000 / PASSO_MS)
    freq = rpm / 60.0
    amostras = []
    for i in range(n):
        t_ms = i * PASSO_MS
        peso = 70.0 + 0.6 * math.sin(2 * math.pi * freq * t_ms / 1000.0)
        if levantar_em is not None and t_ms >= levantar_em:
            peso = 0.0
        rede = 1 if rede_sempre else (0 if 120000 <= t_ms < 150000 else 1)
        amostras.append((t_ms, peso, rede, LEITURA_MS))
    return amostras


def compilar(fw, destino):
    fonte = fw
    with open(fw, encoding="utf-8") as f:
        corpo = f.read()
    if "protótipos" not in corpo and "prototipos" not in corpo:
        # o Arduino IDE gera os protótipos sozinho; o g++ não. Para
        # conseguir comparar com uma versão antiga do firmware, cria
        # uma cópia temporária com os protótipos injetados.
        protos = (
            "void reconectar();\n"
            "void publicar(const char* tipo, float conf, float rpmAtual);\n"
            "void publicarRespiracao(float rpmAtual);\n"
            "void publicarStatus();\n"
            "void aoReceberConfig(char* topico, byte* carga, unsigned int n);\n"
        )
        corpo = corpo.replace("void setup() {", protos + "\nvoid setup() {", 1)
        fonte = os.path.join(os.path.dirname(destino), "firmware_comparacao.cpp")
        with open(fonte, "w", encoding="utf-8") as f:
            f.write(corpo)

    # -x c++ porque o g++ não reconhece a extensão .ino
    cmd = ["g++", "-std=c++17", "-O1", "-w",
           "-I", os.path.join(AQUI, "stubs"),
           os.path.join(AQUI, "main.cpp"), "-x", "c++", fonte, "-o", destino]
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        print("FALHA NA COMPILACAO:")
        print(r.stderr[:3000])
        sys.exit(1)


def rodar(binario, amostras, injetar=None):
    entrada = "\n".join(f"{t} {p:.4f} {r} {a}" for t, p, r, a in amostras) + "\n"
    try:
        r = subprocess.run([binario], input=entrada, capture_output=True,
                           text=True, timeout=60)
    except subprocess.TimeoutExpired:
        # o firmware entrou num laço bloqueante e o relógio virtual parou
        return [], [], "BLOQUEADO firmware nao devolveu o controle (timeout)", []
    linhas = r.stdout.splitlines()
    eventos, debugs, bloqueado = [], [], None
    for linha in linhas:
        if linha.startswith("BLOQUEADO"):
            bloqueado = linha
        m = re.match(r"PUB (\d+) \S+/evento (.+)$", linha)
        if m:
            eventos.append((int(m.group(1)), m.group(2)))
        m = re.match(r"PUB (\d+) \S+/debug (.+)$", linha)
        if m:
            debugs.append((int(m.group(1)), m.group(2)))
    return eventos, debugs, bloqueado, linhas


def campo(carga, chave):
    m = re.search(r'"%s":([-0-9.]+|"[^"]*"|true|false)' % chave, carga)
    if not m:
        return None
    v = m.group(1)
    if v.startswith('"'):
        return v.strip('"')
    if v in ("true", "false"):
        return v == "true"
    return float(v)


def tipo(carga):
    m = re.search(r'"tipo":"([^"]+)"', carga)
    return m.group(1) if m else None


class Resultado:
    def __init__(self, nome):
        self.nome = nome
        self.itens = []

    def check(self, descricao, ok, detalhe=""):
        self.itens.append((descricao, ok, detalhe))
        marca = "OK  " if ok else "FALHOU"
        print(f"   [{marca}] {descricao}" + (f"  ({detalhe})" if detalhe else ""))

    @property
    def passou(self):
        return all(ok for _, ok, _ in self.itens)


def teste_normal(fw, etiqueta):
    print(f"\n=== {etiqueta}: respiração normal a 12 por minuto ===")
    r = Resultado("normal")
    ev, db, bloqueado, _ = rodar(fw, serie(12, 120))
    respiracao = [t for t, c in ev if tipo(c) == "RESPIRACAO"]
    rpms = [campo(c, "rpm") for _, c in db if campo(c, "rpm") is not None]
    dts = [campo(c, "dt_ms") for _, c in db if campo(c, "dt_ms") is not None]
    r.check("nenhum alerta RESPIRACAO com a pessoa respirando normal",
            len(respiracao) == 0, f"{len(respiracao)} alertas")
    # usa só as janelas depois de 60 s: antes disso a média ainda está subindo
    estaveis = [campo(c, "rpm") for t, c in db
                if t >= 60000 and campo(c, "rpm") is not None]
    if estaveis:
        ordenado = sorted(estaveis)
        mediana = ordenado[len(ordenado) // 2]
        r.check("rpm lido bate com 12 (tolerancia 2, regime estavel)",
                abs(mediana - 12) <= 2, f"mediana {mediana:.0f}")
    if dts:
        med = sum(dts) / len(dts)
        r.check("periodo do laco reportado perto de 250 ms",
                abs(med - 250) <= 50, f"medio {med:.0f} ms")
    r.check("firmware nao travou", bloqueado is None, bloqueado or "")
    return r


def teste_lento(fw, etiqueta):
    print(f"\n=== {etiqueta}: respiração lenta a 7 por minuto ===")
    r = Resultado("lento")
    ev, db, bloqueado, _ = rodar(fw, serie(7, 120))
    alertas = [(t, campo(c, "rpm")) for t, c in ev if tipo(c) == "RESPIRACAO"]
    alertas_carga = [c for t, c in ev if tipo(c) == "RESPIRACAO"]
    r.check("alerta RESPIRACAO foi gerado", len(alertas) > 0,
            f"{len(alertas)} alertas")
    if alertas:
        # 10 s para a primeira janela fechar + ~12 s para a média suavizada
        # cair abaixo do mínimo + 20 s de acumulação = ~42 s
        r.check("alerta chegou em ate 50 s", alertas[0][0] <= 50000,
                f"em {alertas[0][0] / 1000:.1f} s")
        r.check("rpm reportado no alerta perto de 7",
                alertas[0][1] is not None and abs(alertas[0][1] - 7) <= 2,
                f"reportou {alertas[0][1]}")
        r.check("alerta leva tambem a media que decidiu",
                "rpm_medio" in alertas_carga[0])
    r.check("firmware nao travou", bloqueado is None, bloqueado or "")
    return r


def teste_levantou(fw, etiqueta):
    print(f"\n=== {etiqueta}: saída do leito ===")
    r = Resultado("levantou")
    sai_em = 30000
    ev, db, bloqueado, _ = rodar(fw, serie(12, 120, levantar_em=sai_em))
    alertas = [t for t, c in ev if tipo(c) == "LEVANTOU"]
    r.check("evento LEVANTOU foi gerado", len(alertas) > 0,
            f"{len(alertas)} eventos")
    if alertas:
        atraso = (alertas[0] - sai_em) / 1000.0
        limite = (JANELA_LEVANTOU_MS / 1000.0) + TOLERANCIA_S
        r.check(f"LEVANTOU chegou em ate {limite:.0f} s depois da saida",
                atraso <= limite, f"chegou em {atraso:.1f} s")
    deitou = [t for t, c in ev if tipo(c) == "DEITOU"]
    r.check("evento DEITOU foi gerado antes", len(deitou) > 0,
            f"{len(deitou)} eventos")
    r.check("firmware nao travou", bloqueado is None, bloqueado or "")
    return r


def teste_rede(fw, etiqueta):
    print(f"\n=== {etiqueta}: queda de rede de 30 s ===")
    r = Resultado("rede")
    ev, db, bloqueado, linhas = rodar(fw, serie(12, 200, rede_sempre=False))
    r.check("firmware continuou rodando durante a queda", bloqueado is None,
            bloqueado or "continuou")
    fim = [l for l in linhas if l.startswith("FIM")]
    if fim:
        m = re.search(r"relogio (\d+) ms", fim[0])
        if m:
            esperado = 200 * 1000
            r.check("relogio virtual percorreu a serie inteira",
                    int(m.group(1)) >= esperado, fim[0])
    return r


def rodar_todos(fw, etiqueta):
    resultados = [teste_normal(fw, etiqueta), teste_lento(fw, etiqueta),
                  teste_levantou(fw, etiqueta), teste_rede(fw, etiqueta)]
    ok = sum(1 for r in resultados if r.passou)
    print(f"\n--- {etiqueta}: {ok}/{len(resultados)} cenarios passaram")
    return all(r.passou for r in resultados)


def main():
    comparacao = sys.argv[1] if len(sys.argv) > 1 else None
    tmp = tempfile.mkdtemp(prefix="bancada-")

    novo = os.path.join(tmp, "bancada_novo")
    compilar(FW_NOVO, novo)
    passou = rodar_todos(novo, "firmware 0.2 (o do repositorio)")

    if comparacao:
        antigo = os.path.join(tmp, "bancada_antigo")
        compilar(comparacao, antigo)
        print("\n" + "=" * 62)
        print("COMPARACAO com a versao anterior")
        print("=" * 62)
        rodar_todos(antigo, os.path.basename(comparacao))
        print("\nSe a versao anterior falhou onde a nova passou, a diferenca")
        print("esta documentada no cabecalho do firmware 0.2.")

    print()
    if passou:
        print("RESULTADO: firmware 0.2 passou em todos os cenarios.")
        return 0
    print("RESULTADO: firmware 0.2 FALHOU. Nao instale em leito.")
    return 1


if __name__ == "__main__":
    sys.exit(main())
