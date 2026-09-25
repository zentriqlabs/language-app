package chat.mural.network

import chat.mural.core.LanguageModule
import chat.mural.core.LearnerState
import chat.mural.core.SessionRecord

/**
 * Lädt bei Netzwerkverbindung priorisierte TTS-Snippets vor (siehe [CachePrioritizer]).
 */
object OfflinePhrasePrefetch {
    suspend fun warm(
        api: OpenRouterAPIClient,
        language: LanguageModule,
        learner: LearnerState,
        sessions: List<SessionRecord>,
        interests: String,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ) {
        val list = CachePrioritizer.phrasesForWarmup(language, learner, sessions, interests)
        var done = 0
        for (phrase in list) {
            try {
                api.synthesizeSpeech(phrase, language.locale)
            } catch (_: Exception) {
                // Einzelne Fehler blockieren nicht den Rest des Warmups.
            }
            done++
            onProgress(done, list.size)
        }
    }
}
