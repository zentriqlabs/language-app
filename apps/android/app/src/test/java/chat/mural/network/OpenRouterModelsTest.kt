package chat.mural.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterModelsTest {
    @Test fun modelSlugsAreStable() {
        assertEquals("deepseek/deepseek-v4-pro-0813", OpenRouterModels.LLM)
        assertEquals("qwen/qwen3-asr-flash-2026-02-10", OpenRouterModels.STT)
        assertEquals("hexgrad/kokoro-82m", OpenRouterModels.TTS)
    }

    @Test fun detectsOpenRouterKeys() {
        assertTrue(OpenRouterModels.isOpenRouterKey("sk-or-v1-" + "a".repeat(40)))
        assertFalse(OpenRouterModels.isOpenRouterKey("sk-proj-" + "a".repeat(40)))
    }

    @Test fun credentialStoreAcceptsBothProviders() {
        assertTrue(CredentialStore.isValidApiKey("sk-or-v1-test-key-1234567890"))
        assertTrue(CredentialStore.isValidApiKey("sk-proj-test-key-1234567890"))
        assertFalse(CredentialStore.isValidApiKey("bad"))
    }
}
