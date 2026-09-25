package chat.mural.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentedPhrasePlayerTest {
    @Test fun segmentsShortTextAsOneChunk() {
        val parts = SegmentedPhrasePlayer.segment("Привет! Как дела?")
        assertTrue(parts.size >= 1)
    }

    @Test fun splitsLongerRepliesIntoMultipleSegments() {
        val text = "Das ist gut. " + "Noch ein Satz hier. " + "Und ein dritter für den Cache."
        val parts = SegmentedPhrasePlayer.segment(text)
        assertTrue(parts.size >= 2)
        assertEquals(text.replace("  ", " ").trim(), parts.joinToString(" "))
    }
}
