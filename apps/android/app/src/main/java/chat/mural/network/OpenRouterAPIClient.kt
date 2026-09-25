package chat.mural.network

import android.util.Base64
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer

/**
 * OpenRouter-backed teaching, STT (Qwen3 ASR Flash) and TTS (Kokoro 82M).
 * Uses the same credential store as BYOK; keys must start with sk-or-.
 */
class OpenRouterAPIClient private constructor(
    private val readCredential: () -> String?,
    private val client: OkHttpClient = defaultClient(),
    private val cache: PhraseAudioCache? = null,
) : TeachingClient {
    constructor(credentials: CredentialStore, cache: PhraseAudioCache) : this(credentials::read, defaultClient(), cache)
    internal constructor(key: String?, client: OkHttpClient, cache: PhraseAudioCache? = null) :
        this({ key }, client, cache)

    override suspend fun respond(
        instructions: String,
        input: String,
        schema: JsonObject?,
        search: Boolean,
        purpose: HelperPurpose?,
    ): APIResult {
        if (search) {
            // Web search tools are not wired for OpenRouter in this build; callers should degrade gracefully.
            throw APIClient.APIException.Refused
        }
        val body = buildJsonObject {
            put("model", OpenRouterModels.LLM)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", instructions)
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", input)
                })
            })
            put("max_tokens", if (schema == null) 1_400 else 2_200)
            if (schema != null) {
                put("response_format", buildJsonObject {
                    put("type", "json_schema")
                    put("json_schema", buildJsonObject {
                        put("name", "mural_result")
                        put("strict", true)
                        put("schema", schema)
                    })
                })
            }
        }
        return decodeOpenRouterChat(post("chat/completions", body))
    }

    /** Voice turn: DeepSeek V4 Pro with brevity system overlay and prior transcript messages. */
    suspend fun voiceReply(
        systemInstructions: String,
        overlay: String,
        history: List<Pair<String, String>>,
        locale: String?,
    ): String {
        val body = buildJsonObject {
            put("model", OpenRouterModels.LLM)
            put("max_tokens", 220)
            put("temperature", 0.7)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", buildString {
                        append(systemInstructions)
                        append("\n\n")
                        append(OpenRouterModels.VOICE_BREVITY_PROMPT)
                        if (overlay.isNotBlank()) {
                            append("\n\nAktuelle Anweisung (nicht vorlesen): ")
                            append(overlay.take(1200))
                        }
                    })
                })
                history.takeLast(24).forEach { (role, content) ->
                    add(buildJsonObject {
                        put("role", role)
                        put("content", content.take(4_000))
                    })
                }
            })
        }
        return decodeOpenRouterChat(post("chat/completions", body)).text.trim()
    }

    suspend fun transcribe(pcm16Mono: ByteArray, sampleRate: Int): String {
        val wav = WavEncoder.encodePcm16Mono(pcm16Mono, sampleRate)
        val base64 = Base64.encodeToString(wav, Base64.NO_WRAP)
        val body = buildJsonObject {
            put("model", OpenRouterModels.STT)
            put("data", base64)
        }
        val response = post("audio/transcriptions", body)
        val text = (response["text"] as? JsonPrimitive)?.contentOrNull?.trim()
            ?: (response["transcript"] as? JsonPrimitive)?.contentOrNull?.trim()
        if (text.isNullOrBlank()) throw APIClient.APIException.InvalidResponse
        return text
    }

    suspend fun synthesizeSpeech(text: String, locale: String?): ByteArray {
        val voice = OpenRouterModels.ttsVoiceForLocale(locale)
        cache?.lookup(OpenRouterModels.TTS, voice, text)?.let { return it }
        val body = buildJsonObject {
            put("model", OpenRouterModels.TTS)
            put("input", text.take(900))
            put("voice", voice)
        }
        val bytes = postBytes("audio/speech", body)
        cache?.store(OpenRouterModels.TTS, voice, text, bytes)
        return bytes
    }

    private suspend fun post(path: String, body: JsonObject): JsonObject {
        val payload = postBytes(path, body)
        return try {
            Json.parseToJsonElement(payload.decodeToString()).jsonObject
        } catch (_: Exception) {
            throw APIClient.APIException.InvalidResponse
        }
    }

    private suspend fun postBytes(path: String, body: JsonObject): ByteArray {
        if (!VALID_PATH.matches(path) || path.contains("..") || path.startsWith('/')) {
            throw APIClient.APIException.InvalidResponse
        }
        val key = readCredential() ?: throw APIClient.APIException.MissingKey
        val request = Request.Builder()
            .url(OPENROUTER_BASE.newBuilder().addPathSegments(path).build())
            .header("Authorization", "Bearer $key")
            .header("Content-Type", JSON_MEDIA_TYPE.toString())
            .header("HTTP-Referer", "https://mural.chat/")
            .header("X-OpenRouter-Title", "Mural Android")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val value = response.use {
                            if (it.code !in 200..299) throw APIClient.APIException.Http(it.code)
                            it.readBoundedBody()
                        }
                        if (continuation.isActive) continuation.resume(value)
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            })
        }
    }

    private fun Response.readBoundedBody(): ByteArray {
        val responseBody = body ?: throw APIClient.APIException.InvalidResponse
        if (responseBody.contentLength() > MAX_RESPONSE_BYTES) throw APIClient.APIException.InvalidResponse
        val source = responseBody.source()
        val buffer = Buffer()
        var total = 0L
        while (true) {
            val count = source.read(buffer, minOf(8_192L, MAX_RESPONSE_BYTES + 1L - total))
            if (count == -1L) break
            total += count
            if (total > MAX_RESPONSE_BYTES) throw APIClient.APIException.InvalidResponse
        }
        return buffer.readByteArray()
    }

    companion object {
        private val OPENROUTER_BASE = HttpUrl.Builder()
            .scheme("https")
            .host(OpenRouterModels.BASE_HOST)
            .addPathSegment("api")
            .addPathSegment("v1")
            .addPathSegment("")
            .build()
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val VALID_PATH = Regex("[a-z0-9][a-z0-9_/-]*")
        private const val MAX_RESPONSE_BYTES = 2_000_000L
        private val Json = Json { ignoreUnknownKeys = true }

        private fun defaultClient() = OkHttpClient.Builder()
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .cookieJar(CookieJar.NO_COOKIES)
            .cache(null)
            .build()
    }
}

/** Minimal PCM16 mono WAV encoder for ASR upload. */
internal object WavEncoder {
    fun encodePcm16Mono(pcm: ByteArray, sampleRate: Int): ByteArray {
        val channels = 1
        val bits = 16
        val byteRate = sampleRate * channels * bits / 8
        val blockAlign = channels * bits / 8
        val dataSize = pcm.size
        val buffer = java.io.ByteArrayOutputStream(44 + dataSize)
        buffer.write("RIFF".toByteArray())
        buffer.write(intLe(36 + dataSize))
        buffer.write("WAVE".toByteArray())
        buffer.write("fmt ".toByteArray())
        buffer.write(intLe(16))
        buffer.write(shortLe(1))
        buffer.write(shortLe(channels))
        buffer.write(intLe(sampleRate))
        buffer.write(intLe(byteRate))
        buffer.write(shortLe(blockAlign))
        buffer.write(shortLe(bits))
        buffer.write("data".toByteArray())
        buffer.write(intLe(dataSize))
        buffer.write(pcm)
        return buffer.toByteArray()
    }

    private fun intLe(value: Int): ByteArray = byteArrayOf(
        (value and 0xff).toByte(),
        (value shr 8 and 0xff).toByte(),
        (value shr 16 and 0xff).toByte(),
        (value shr 24 and 0xff).toByte(),
    )

    private fun shortLe(value: Int): ByteArray = byteArrayOf(
        (value and 0xff).toByte(),
        (value shr 8 and 0xff).toByte(),
    )
}
