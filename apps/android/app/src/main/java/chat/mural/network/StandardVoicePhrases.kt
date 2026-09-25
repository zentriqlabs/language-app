package chat.mural.network

/**
 * Kurze, häufig wiederkehrende Sätze für Offline-TTS-Cache.
 * Fokus auf Feedback und Gesprächsführung — nicht jedes Lemma vorab laden.
 */
object StandardVoicePhrases {
    fun forLanguage(languageId: String): List<String> = when (languageId) {
        "ru" -> listOf("Отлично!", "Попробуй ещё раз.", "Скажи по-другому.", "Понятно.")
        "de" -> listOf("Sehr gut!", "Versuch es noch einmal.", "Sag es anders.", "Verstanden.")
        "es" -> listOf("¡Muy bien!", "Inténtalo otra vez.", "Dímelo de otra forma.", "Entendido.")
        "fr" -> listOf("Très bien !", "Essaie encore.", "Dis-le autrement.", "D'accord.")
        "it" -> listOf("Molto bene!", "Riprova.", "Dillo in un altro modo.", "Capito.")
        "pt" -> listOf("Muito bem!", "Tenta outra vez.", "Diz de outro jeito.", "Entendi.")
        "nb" -> listOf("Veldig bra!", "Prøv igjen.", "Si det på en annen måte.", "Skjønner.")
        "zh" -> listOf("很好！", "再试一次。", "换种说法。", "明白了。")
        else -> listOf("Very good!", "Try again.", "Say it another way.", "Got it.")
    }
}
