# 🌼 Projeto Margarida

**O cuidado que chega antes.**

Aplicativo de bem-estar para idosos, nascido de uma história real: a avó do
fundador, Margarida, caía muito e morreu dormindo. O projeto ataca exatamente
esses dois riscos — **quedas** (SOS com ligações sequenciais + sensores de casa)
e **noite** (monitoramento passivo de FC/respiração sob o colchão, em
desenvolvimento).

> ⚠️ **Aviso honesto:** o Margarida é bem-estar e alerta a cuidadores.
> **Não é dispositivo médico**, não diagnostica e não substitui atendimento
> profissional.

## O que existe hoje (tudo testado)

| Módulo | Onde | Estado |
|---|---|---|
| SOS: ligações em sequência + SMS com mapa + contexto de saúde | `app-android/` (Kotlin) + `monitor-idoso/panico.py` | ✅ testado em celular real (S23) |
| Rotina por voz: FC pela câmera (rPPG), ortostática sentada→pé, urina com cartão de cor | `monitor-idoso/` (Python) | ✅ 17/17 testes |
| Regras de sensores de casa (radar de queda estilo FALLR1, gás, porta, remédio) e inatividade da manhã | `monitor-idoso/sensores.py` + `app-android/core` | ✅ testado nas duas linguagens |
| Noite: FC/FR sob o colchão (BCG) + saída do leito | `monitor-idoso/bcg_noturno.py` | ✅ DSP testado (hardware = v2) |
| Núcleo de negócio do app (diário de eventos, regras, contratos JSON) | `app-android/core/` (Kotlin/JVM) | ✅ 19/19 testes JVM |

## Rodar os testes

```bash
# Python (3.9+; precisa de numpy e opencv-python)
cd monitor-idoso && python3 verificar_tudo.py     # 19/19 itens

# Núcleo Kotlin (qualquer JDK 11+ e kotlinc)
cd app-android && kotlinc core/src/main/kotlin/*.kt core/src/test/kotlin/TestesCore.kt \
    -include-runtime -d core-testes.jar && java -jar core-testes.jar   # 19/19
```

## Compilar o app

Abra `app-android/` no Android Studio (AGP 8.5, Gradle 8.10, JDK 17, minSdk 26)
→ Build APK(s). Veja `app-android/GUIA_INSTALACAO.md` para o passo a passo
completo em português leigo.

## Documentação

- `monitor-idoso/especificacao_android.md` — arquitetura do app (2 modos, wake
  word, rotas de SOS A/B, permissões, LGPD);
- `monitor-idoso/margarida_mapa_mundial.md` — pesquisa mundial de detecção de
  queda e sensores replicáveis;
- `monitor-idoso/piloto_cuidadores.md` — plano de piloto com cuidadores (CEP);
- `identidade/` — logo, paleta e voz da marca.

## Roadmap

1. ✅ MVP SOS (testado em aparelho real);
2. 🔨 Wake word "socorro" (Porcupine pt-BR);
3. 🔨 Rotina por voz dentro do APK;
4. 🔨 Integração de sensores Matter/Tuya (radar de queda, porta, gás);
5. 🔲 Pad noturno BCG (hardware próprio);
6. 🔲 Piloto com cuidadores + validação.

## Licença

MIT — use, estude, melhore. Se salvar um idoso no caminho, conte pra gente. 🌼
