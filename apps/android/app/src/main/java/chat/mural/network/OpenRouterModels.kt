package chat.mural.network

/**
 * Central OpenRouter model routing for Mural (mirrors [shared/openrouter/models.json]).
 * LLM tutoring, STT and TTS each use a dedicated model slug on OpenRouter.
 */
object OpenRouterModels {
    const val BASE_HOST = "openrouter.ai"
    const val LLM = "deepseek/deepseek-v4-pro-0813"
    const val STT = "qwen/qwen3-asr-flash-2026-02-10"
    const val TTS = "hexgrad/kokoro-82m"
    const val DEFAULT_TTS_VOICE = "af_bella"

    /** Cost control: keep spoken replies short so Kokoro bills fewer characters per turn. */
    const val VOICE_BREVITY_PROMPT =
        "Antworte im Sprachmodus stets kurz, direkt und in maximal zwei kurzen Sätzen. Keine Aufzählungen, keine Meta-Kommentare."

    fun ttsVoiceForLocale(locale: String?): String = when (locale?.lowercase()) {
        "ru-ru", "de-de", "es-es", "en-us", "fr-fr", "it-it", "pt-br", "zh-cn", "nb-no" -> DEFAULT_TTS_VOICE
        else -> DEFAULT_TTS_VOICE
    }

    fun isOpenRouterKey(key: String): Boolean {
        val trimmed = key.trim()
        return trimmed.startsWith("sk-or-") && trimmed.length >= 24 && trimmed.none(Char::isWhitespace)
    }
}
