package chat.mural.network

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaPlayer
import android.os.Build
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Turn-based voice loop: microphone → Qwen ASR → DeepSeek V4 Pro → Kokoro TTS.
 * Emits the same transcript events [MuralViewModel] already understands.
 */
class OpenRouterVoiceTransport(
    context: Context,
    private val scope: CoroutineScope,
    private val api: OpenRouterAPIClient,
) : VoiceGateway {
    override var onEvent: ((JsonObject) -> Unit)? = null
    override var onFailure: ((String) -> Unit)? = null
    override var onLevels: ((Double, Double) -> Unit)? = null

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val closing = AtomicBoolean(false)
    private var sessionJob: Job? = null
    private var systemInstructions = ""
    private var overlay = ""
    private var locale: String? = null
    private val transcript = mutableListOf<Pair<String, String>>()
    private var voiceSeconds = 0.0
    private var listenGate = CompletableDeferred<Unit>()

    @Volatile override var started: Boolean = false
    @Volatile override var isMuted: Boolean = false

    override suspend fun connect(instructions: String, history: JsonArray, language: String?) {
        disconnect()
        closing.set(false)
        systemInstructions = instructions
        locale = language
        overlay = ""
        transcript.clear()
        voiceSeconds = 0.0
        listenGate = CompletableDeferred()
        seedHistory(history)
        if (appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw IOException("Microphone permission is required.")
        }
        started = false
        sessionJob = scope.launch(Dispatchers.IO) { runSession() }
    }

    override fun send(event: JsonObject): Boolean {
        if (!started || closing.get()) return false
        val type = event["type"]?.jsonPrimitive?.contentOrNull ?: return false
        val content = event["content"]?.jsonPrimitive?.contentOrNull ?: return false
        scope.launch(Dispatchers.IO) {
            when {
                type == "session.instructions.append" -> {
                    overlay = (overlay + "\n" + content).take(4_000)
                    assistantTurn(trigger = "instructions")
                    if (!listenGate.isCompleted) listenGate.complete(Unit)
                }
                type == "session.thinking.append" -> {
                    overlay = (overlay + "\n" + content).take(4_000)
                }
                type == "session.commentary.append" -> speakAssistant(content)
            }
        }
        return true
    }

    override fun mute(muted: Boolean) {
        isMuted = muted
    }

    override fun close() {
        closing.set(true)
        emit(buildJsonObject {
            put("type", "session.closed")
            put("usage", buildJsonObject { put("seconds", voiceSeconds) })
        })
    }

    override fun disconnect() {
        closing.set(true)
        sessionJob?.cancel()
        sessionJob = null
        started = false
        isMuted = false
    }

    private suspend fun runSession() {
        try {
            configureAudioRoute()
            emit(buildJsonObject {
                put("type", "mural.session.created")
                put("session", buildJsonObject { put("id", UUID.randomUUID().toString()) })
            })
            emit(buildJsonObject {
                put("type", "session.started")
                put("session", buildJsonObject { put("id", "openrouter") })
            })
            started = true
            // Greeting is injected by the ViewModel after session.started; do not listen over it.
            listenGate.await()
            while (currentCoroutineContext().isActive && !closing.get()) {
                if (isMuted) {
                    delay(200)
                    continue
                }
                val pcm = recordUtterance() ?: continue
                if (closing.get()) break
                val text = api.transcribe(pcm, SAMPLE_RATE)
                if (text.isBlank()) continue
                emitTranscript("session.input_transcript.delta", text)
                transcript += "user" to text
                assistantTurn(trigger = "user")
            }
        } catch (_: CancellationException) {
            // Expected on disconnect.
        } catch (error: Exception) {
            onFailure?.invoke(error.message ?: "Voice connection failed.")
        } finally {
            started = false
            restoreAudioRoute()
        }
    }

    private suspend fun assistantTurn(trigger: String) {
        if (closing.get()) return
        val reply = api.voiceReply(systemInstructions, overlay, transcript, locale)
        overlay = ""
        if (reply.isBlank()) return
        speakAssistant(reply)
        transcript += "assistant" to reply
    }

    private suspend fun speakAssistant(text: String) {
        val audio = api.synthesizeSpeech(text, locale)
        emitTranscript("session.output_transcript.delta", text)
        playAudio(audio)
        voiceSeconds += maxOf(1.0, text.length / 14.0)
        emit(buildJsonObject {
            put("type", "session.usage.updated")
            put("usage", buildJsonObject { put("seconds", voiceSeconds) })
        })
    }

    private suspend fun playAudio(bytes: ByteArray) = withContext(Dispatchers.Main) {
        val file = File.createTempFile("mural-tts", ".audio", appContext.cacheDir)
        file.writeBytes(bytes)
        val player = MediaPlayer()
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            player.setDataSource(file.absolutePath)
            player.prepare()
            val durationMs = player.duration.coerceAtLeast(1)
            player.start()
            var elapsed = 0
            while (player.isPlaying && elapsed < durationMs + 500 && !closing.get()) {
                onLevels?.invoke(0.0, 0.35)
                delay(80)
                elapsed += 80
            }
            player.stop()
        } finally {
            player.release()
            file.delete()
            onLevels?.invoke(0.0, 0.0)
        }
    }

    private suspend fun recordUtterance(): ByteArray? {
        val minBytes = SAMPLE_RATE * 2 // 1 second minimum
        val maxBytes = SAMPLE_RATE * 2 * 25 // 25 seconds cap
        val buffer = ByteArray(SAMPLE_RATE / 10 * 2) // 100ms
        val record = AudioRecord(
            MediaRecorderSource(),
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(buffer.size * 4),
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) return null
        val collected = java.io.ByteArrayOutputStream()
        var silentChunks = 0
        var heardSpeech = false
        try {
            record.startRecording()
            while (currentCoroutineContext().isActive && !closing.get() && !isMuted && collected.size() < maxBytes) {
                val read = record.read(buffer, 0, buffer.size)
                if (read <= 0) continue
                val level = rms(buffer, read)
                onLevels?.invoke(level, 0.0)
                if (level > 0.02) {
                    heardSpeech = true
                    silentChunks = 0
                    collected.write(buffer, 0, read)
                } else if (heardSpeech) {
                    silentChunks++
                    collected.write(buffer, 0, read)
                    if (silentChunks >= 12) break // ~1.2s silence ends turn
                } else {
                    delay(40)
                }
            }
        } finally {
            try { record.stop() } catch (_: Exception) { }
            record.release()
        }
        return if (collected.size() >= minBytes) collected.toByteArray() else null
    }

    private fun rms(buffer: ByteArray, length: Int): Double {
        var sum = 0.0
        var index = 0
        while (index + 1 < length) {
            val sample = (buffer[index].toInt() and 0xff) or (buffer[index + 1].toInt() shl 8)
            val signed = if (sample > 32767) sample - 65536 else sample
            sum += signed * signed.toDouble()
            index += 2
        }
        val mean = sum / (length / 2).coerceAtLeast(1)
        return sqrt(mean) / 32768.0
    }

    private fun seedHistory(history: JsonArray) {
        history.forEach { element ->
            val item = element.jsonObject
            if (item["type"]?.jsonPrimitive?.contentOrNull != "message") return@forEach
            val role = item["role"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val text = item["content"]?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                ?: return@forEach
            val mapped = if (role == "assistant") "assistant" else "user"
            transcript += mapped to text
        }
    }

    private fun emitTranscript(type: String, text: String) {
        val now = (voiceSeconds * 1000).toInt()
        emit(buildJsonObject {
            put("type", type)
            put("event_id", UUID.randomUUID().toString())
            put("delta", text)
            put("start_ms", now)
            put("end_ms", now + text.length.coerceAtLeast(1) * 45)
        })
    }

    private fun emit(event: JsonObject) {
        scope.launch(Dispatchers.Main.immediate) { onEvent?.invoke(event) }
    }

    private fun configureAudioRoute() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT < 31) {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true
        }
    }

    private fun restoreAudioRoute() {
        audioManager.mode = AudioManager.MODE_NORMAL
    }

    /** Avoid coupling to android.media.MediaRecorder for the audio source constant. */
    private fun MediaRecorderSource(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION
        } else {
            android.media.MediaRecorder.AudioSource.MIC
        }

    companion object {
        private const val SAMPLE_RATE = 16_000
    }
}
