package chat.mural.network

/**
 * Spielt längere Antworten aus gecachten Segmenten (Sätze / kurze Phrasen).
 * Fehlende Segmente werden einmalig per TTS geholt und danach im Cache liegen.
 */
class SegmentedPhrasePlayer(
    private val api: OpenRouterAPIClient,
    private val cache: PhraseAudioCache,
    private val playBytes: suspend (ByteArray) -> Unit,
) {
    suspend fun speak(text: String, locale: String?) {
        val segments = segment(text)
        if (segments.isEmpty()) return
        val config = ProviderModelConfig.current
        val voice = config.ttsVoice
        for (segment in segments) {
            val cached = cache.lookup(config.tts, voice, segment)
            val audio = cached ?: api.synthesizeSpeech(segment, locale)
            playBytes(audio)
        }
    }

    companion object {
        fun segment(text: String): List<String> {
            val normalized = text.trim().replace(Regex("\\s+"), " ")
            if (normalized.isEmpty()) return emptyList()
            val sentences = normalized.split(Regex("(?<=[.!?…])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
            if (sentences.size <= 1 && normalized.length <= 90) return listOf(normalized)
            val out = mutableListOf<String>()
            for (sentence in sentences) {
                if (sentence.length <= 90) out.add(sentence)
                else {
                    sentence.split(Regex(",\\s+")).map { it.trim() }.filter { it.isNotEmpty() }.forEach { chunk ->
                        if (chunk.length <= 90) out.add(chunk) else out.add(chunk.take(90))
                    }
                }
            }
            return out.ifEmpty { listOf(normalized.take(180)) }
        }
    }
}
