"""
Verificação completa do projeto: roda TODAS as suítes de teste e checa os
entregáveis no disco (áudios, JSON de calibração, docs). Um comando único:

    python3 verificar_tudo.py

Saída: tabela ✅/❌ por componente + resumo final. Exit code 0 = tudo verde.
"""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).parent
OLHO = ROOT.parent / "olho-hidratacao"

SUITES = [
    # (nome, diretório, comando)
    ("Protótipo olho (8 testes)", OLHO, [sys.executable, "testes_pipeline.py"]),
    ("FC por câmera rPPG (4 testes)", ROOT, [sys.executable, "testes_fc.py"]),
    ("Urina com cartão de cor (4 testes)", ROOT, [sys.executable, "testes_urina.py"]),
    ("Fluxo guiado por voz (5 testes)", ROOT, [sys.executable, "testes_fluxo.py"]),
    ("BCG noturno — DSP (3 testes)", ROOT, [sys.executable, "testes_bcg.py"]),
    ("Botão de pânico — SOS (5 testes)", ROOT, [sys.executable, "testes_panico.py"]),
    ("Orquestrador — jornada completa (7 testes)", ROOT, [sys.executable, "testes_orquestrador.py"]),
    ("Sensores — queda/gás/porta/remédio/leito (6 testes)", ROOT, [sys.executable, "testes_sensores.py"]),
    ("Validador de dataset (--selftest)", ROOT, [sys.executable, "validar_dataset.py", "--selftest"]),
    ("Núcleo Kotlin do app Android (8 testes JVM)", ROOT.parent / "app-android",
     ["java", "-Dfile.encoding=UTF-8", "-jar", "core-testes.jar"]),
]

ARQUIVOS = [
    ("9 áudios pt-BR (8 do fluxo + mensagem de pânico)", ROOT / "audio",
     lambda p: len(list(p.glob("*.mp3"))) == 9),
    ("Calibração da rampa de cor", ROOT / "rampa_calibrada.json", None),
    ("Mapa de ideias", ROOT / "mapa-ideias.md", None),
    ("README (paridade de recursos)", ROOT / "README.md", None),
    ("Plano do piloto com cuidadores", ROOT / "piloto_cuidadores.md", None),
    ("Decisão hardware vs. smartphone", ROOT / "equipamento_vs_smartphone.md", None),
    ("Checklist de status", ROOT / "checklist_status.md", None),
    ("Especificação Android", ROOT / "especificacao_android.md", None),
    ("Mapa mundial Margarida (queda/sensores)", ROOT / "margarida_mapa_mundial.md", None),
]


def main() -> int:
    falhas = 0
    print("=" * 62)
    print(" TESTES")
    print("=" * 62)
    for nome, cwd, cmd in SUITES:
        r = subprocess.run(cmd, cwd=str(cwd), capture_output=True, text=True)
        ok = r.returncode == 0
        if not ok:
            falhas += 1
        marca = "✅" if ok else "❌"
        print(f" {marca} {nome}")
        if not ok:
            print((r.stdout + r.stderr).strip()[:400])

    print("=" * 62)
    print(" ARQUIVOS")
    print("=" * 62)
    for nome, path, extra in ARQUIVOS:
        ok = path.exists() and (extra is None or extra(path))
        if not ok:
            falhas += 1
        print(f" {'✅' if ok else '❌'} {nome}  ({path.name})")

    print("=" * 62)
    total = len(SUITES) + len(ARQUIVOS)
    if falhas:
        print(f" {falhas}/{total} itens FALHARAM")
    else:
        print(f" TUDO VERDE: {total}/{total} itens OK")
    return 1 if falhas else 0


if __name__ == "__main__":
    sys.exit(main())
