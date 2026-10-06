package br.com.monitoridoso

/** Motor 24/7 para detectar "socorro" — escolha no modo cuidador (teste bateria S23). */
enum class MotorWake {
    ESCUTA,
    PORCUPINE,
    VOSK;

    companion object {
        fun fromStored(raw: String?): MotorWake =
            entries.find { it.name == raw } ?: ESCUTA
    }
}
