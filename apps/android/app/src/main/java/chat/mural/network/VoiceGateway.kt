package chat.mural.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Shared surface for WebRTC (OpenAI Live) and OpenRouter pipeline voice.
 * [MuralViewModel] wires one implementation per session.
 */
interface VoiceGateway {
    var onEvent: ((JsonObject) -> Unit)?
    var onFailure: ((String) -> Unit)?
    var onLevels: ((Double, Double) -> Unit)?
    val started: Boolean
    val isMuted: Boolean

    suspend fun connect(
        instructions: String,
        history: JsonArray = JsonArray(emptyList()),
        language: String? = null,
        homeLanguageId: String? = null,
        targetLanguageId: String? = null,
    )

    fun send(event: JsonObject): Boolean
    fun mute(muted: Boolean)
    fun close()
    fun disconnect()
}
