package chat.mural.network

/**
 * Schnittstelle für spätere Modell-/Stimmen-IDs (Housing-Park, Anbieter).
 * Werte kommen vorerst aus [OpenRouterModels]; kann später aus JSON/Remote Config geladen werden.
 */
data class ProviderModelConfig(
    val llm: String = OpenRouterModels.LLM,
    val stt: String = OpenRouterModels.STT,
    val tts: String = OpenRouterModels.TTS,
    /** Aktuelles Thema / Recherche — Modell-Slug später austauschbar (z. B. :online). */
    val topicResearch: String = "deepseek/deepseek-v4-pro-0813:online",
    val ttsVoice: String = OpenRouterModels.DEFAULT_TTS_VOICE,
    val voiceBrevityPrompt: String = OpenRouterModels.VOICE_BREVITY_PROMPT,
) {
    companion object {
        /** Einzige Produktionskonfiguration bis du konkrete Slugs lieferst. */
        val current = ProviderModelConfig()
    }
}
