package chat.mural.network

/**
 * Kurze, häufige Bausteine (~Kernwortschatz). TTS-Cache reiht sie für längere Antworten aneinander;
 * nicht jedes einzelne Wort wird vorab synthetisiert.
 */
object HighFrequencyChunks {
    fun forLanguage(languageId: String): List<String> = when (languageId) {
        "ru" -> listOf("да", "нет", "спасибо", "пожалуйста", "извините", "хорошо", "понятно", "как дела?", "я хочу", "мне нужно")
        "de" -> listOf("ja", "nein", "danke", "bitte", "entschuldigung", "gut", "verstanden", "wie geht's?", "ich möchte", "ich brauche")
        "es" -> listOf("sí", "no", "gracias", "por favor", "perdón", "bien", "entendido", "¿qué tal?", "quiero", "necesito")
        "fr" -> listOf("oui", "non", "merci", "s'il vous plaît", "pardon", "bien", "d'accord", "ça va ?", "je voudrais", "j'ai besoin")
        else -> listOf("yes", "no", "thanks", "please", "sorry", "good", "got it", "how are you?", "I want", "I need")
    }
}
