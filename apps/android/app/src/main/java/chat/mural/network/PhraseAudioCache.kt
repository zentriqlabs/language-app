package chat.mural.network

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * On-device TTS cache for repeated phrases (greetings, feedback, grammar snippets).
 * Keys are stable hashes of model + voice + normalized text; values are raw audio bytes.
 */
class PhraseAudioCache(context: Context) {
    private val directory = File(context.cacheDir, "mural_tts_cache").apply { mkdirs() }

    fun lookup(model: String, voice: String, text: String): ByteArray? {
        val file = fileFor(model, voice, text)
        return if (file.isFile && file.length() in 1..MAX_BYTES) file.readBytes() else null
    }

    fun store(model: String, voice: String, text: String, audio: ByteArray) {
        if (audio.isEmpty() || audio.size > MAX_BYTES) return
        fileFor(model, voice, text).writeBytes(audio)
    }

    private fun fileFor(model: String, voice: String, text: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
        val payload = "$model\u0000$voice\u0000${text.trim()}"
        val name = digest.digest(payload.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) } + ".audio"
        return File(directory, name)
    }

    companion object {
        private const val MAX_BYTES = 2_000_000
    }
}
