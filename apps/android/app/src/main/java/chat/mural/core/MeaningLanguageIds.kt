package chat.mural.core

/** Maps UI meaning-language labels to BCP-47-ish ids for STT/bridge detection. */
object MeaningLanguageIds {
    fun idFor(display: String): String = when (display.trim()) {
        "Norwegian" -> "nb"
        "German" -> "de"
        "French" -> "fr"
        "Spanish" -> "es"
        "Italian" -> "it"
        "Portuguese" -> "pt"
        "Chinese (Simplified)", "Chinese" -> "zh"
        "Polish" -> "pl"
        "Arabic" -> "ar"
        "Ukrainian" -> "uk"
        "Russian" -> "ru"
        else -> "en"
    }
}
