package chat.mural.network

import chat.mural.core.LanguageModule
import chat.mural.core.LearnerState
import chat.mural.core.nowSeconds

/**
 * Lädt bei Netzwerkverbindung die wichtigsten TTS-Snippets vor:
 * Begrüßung, Standard-Feedback und fällige Vokabeln (Lemma, nicht ganze Sätze).
 */
object OfflinePhrasePrefetch {
    /** Max. Anzahl Lemmas pro Lauf — Speicher und API-Kosten begrenzen. */
    private const val MAX_LEMMAS = 12

    suspend fun warm(
        api: OpenRouterAPIClient,
        language: LanguageModule,
        learner: LearnerState,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ) {
        val phrases = linkedSetOf<String>()
        phrases.add(language.greeting)
        phrases.addAll(StandardVoicePhrases.forLanguage(language.id))
        learner.words
            .filter { it.dueAt < nowSeconds() || it.bars <= 1 }
            .sortedBy { it.dueAt }
            .take(MAX_LEMMAS)
            .mapTo(phrases) { it.lemma }
        val list = phrases.filter { it.isNotBlank() }
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
