package chat.mural.network

import chat.mural.core.LanguageModule
import chat.mural.core.LearnerState
import chat.mural.core.SessionRecord
import chat.mural.core.nowSeconds

/**
 * Enterprise-style prefetch ordering: recent dialogue → spaced recall → high-frequency chunks → greeting.
 * Avoids caching thousands of full sentences; prefers lemmas and short reusable phrases.
 */
object CachePrioritizer {
    private const val MAX_RECENT_PASSAGE_CHARS = 120
    private const val MAX_RECENT_PHRASES = 8
    private const val MAX_DUE_LEMMAS = 15
    private const val MAX_HIGH_FREQUENCY = 24

    fun phrasesForWarmup(
        language: LanguageModule,
        learner: LearnerState,
        sessions: List<SessionRecord>,
        interests: String,
    ): List<String> {
        val out = linkedSetOf<String>()
        out.add(language.greeting)
        out.addAll(StandardVoicePhrases.forLanguage(language.id))
        recentAssistantSnippets(language.id, sessions).forEach { out.add(it) }
        learner.words
            .sortedWith(compareBy<chat.mural.core.WordState> { it.dueAt }.thenByDescending { it.lastSeen })
            .take(MAX_DUE_LEMMAS)
            .mapTo(out) { it.lemma }
        HighFrequencyChunks.forLanguage(language.id).take(MAX_HIGH_FREQUENCY).forEach { out.add(it) }
        interests.split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.length in 2..40 }
            .take(6)
            .forEach { interest ->
                out.add(interest)
                out.add("${language.greetingWord} $interest")
            }
        return out.filter { it.isNotBlank() }.take(64)
    }

    private fun recentAssistantSnippets(languageId: String, sessions: List<SessionRecord>): List<String> {
        val snippets = mutableListOf<String>()
        for (session in sessions.filter { it.languageID == languageId }.sortedByDescending { it.startedAt }.take(3)) {
            for (passage in session.passages.takeLast(6)) {
                if (passage.speaker.name != "assistant") continue
                val trimmed = passage.text.trim().replace('\n', ' ')
                if (trimmed.length in 8..MAX_RECENT_PASSAGE_CHARS) snippets.add(trimmed)
                else if (trimmed.length > MAX_RECENT_PASSAGE_CHARS) {
                    trimmed.split('.', '!', '?').map { it.trim() }.firstOrNull { it.length in 8..MAX_RECENT_PASSAGE_CHARS }?.let { snippets.add(it) }
                }
            }
        }
        return snippets.distinct().take(MAX_RECENT_PHRASES)
    }
}
