package chat.mural.network

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * On-device TTS cache for repeated phrases (greetings, feedback, lemmas).
 * LRU eviction keeps disk use bounded (~50 MB default).
 */
class PhraseAudioCache(context: Context) {
    private val directory = File(context.cacheDir, "mural_tts_cache").apply { mkdirs() }
    private val index = File(directory, "index.properties")

    fun lookup(model: String, voice: String, text: String): ByteArray? {
        val file = fileFor(model, voice, text)
        if (!file.isFile || file.length() !in 1..MAX_ENTRY_BYTES) return null
        touch(file.name)
        return file.readBytes()
    }

    fun store(model: String, voice: String, text: String, audio: ByteArray) {
        if (audio.isEmpty() || audio.size > MAX_ENTRY_BYTES) return
        evictUntil(audio.size.toLong())
        val file = fileFor(model, voice, text)
        file.writeBytes(audio)
        touch(file.name)
        trimIndex()
    }

    /** Geschätzter Speicherverbrauch für Einstellungen/Diagnose. */
    fun usedBytes(): Long = directory.listFiles()?.filter { it.extension == "audio" }?.sumOf { it.length() } ?: 0L

    private fun fileFor(model: String, voice: String, text: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
        val payload = "$model\u0000$voice\u0000${text.trim()}"
        val name = digest.digest(payload.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) } + ".audio"
        return File(directory, name)
    }

    private fun touch(fileName: String) {
        val lines = readIndex().toMutableMap()
        lines[fileName] = System.currentTimeMillis().toString()
        writeIndex(lines)
    }

    private fun trimIndex() {
        val files = directory.listFiles()?.filter { it.extension == "audio" }?.associateBy { it.name } ?: return
        val index = readIndex().filterKeys { files.containsKey(it) }.toMutableMap()
        files.keys.forEach { name -> if (!index.containsKey(name)) index[name] = System.currentTimeMillis().toString() }
        writeIndex(index)
    }

    private fun evictUntil(incoming: Long) {
        var total = directory.listFiles()?.filter { it.extension == "audio" }?.sumOf { it.length() } ?: 0L
        if (total + incoming <= MAX_CACHE_BYTES) return
        val index = readIndex()
        val files = directory.listFiles()?.filter { it.extension == "audio" } ?: return
        val ordered = files.sortedBy { index[it.name]?.toLongOrNull() ?: it.lastModified() }
        for (file in ordered) {
            if (total + incoming <= MAX_CACHE_BYTES) break
            total -= file.length()
            file.delete()
            index.remove(file.name)
        }
        writeIndex(index)
    }

    private fun readIndex(): MutableMap<String, String> {
        if (!index.isFile) return mutableMapOf()
        return index.readLines()
            .mapNotNull { line ->
                val parts = line.split('=', limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }
            .toMap()
            .toMutableMap()
    }

    private fun writeIndex(map: Map<String, String>) {
        index.writeText(map.entries.sortedByDescending { it.value.toLongOrNull() ?: 0L }.joinToString("\n") { "${it.key}=${it.value}" })
    }

    companion object {
        private const val MAX_ENTRY_BYTES = 2_000_000L
        /** ~50 MB — genug für Begrüßungen, Feedback und die wichtigsten Lemmas. */
        private const val MAX_CACHE_BYTES = 50L * 1024L * 1024L
    }
}
