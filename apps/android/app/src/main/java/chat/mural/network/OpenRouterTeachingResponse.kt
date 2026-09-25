package chat.mural.network

import chat.mural.core.SourceLink
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** Decodes OpenRouter chat completion payloads for teaching helpers. */
internal fun decodeOpenRouterChat(response: JsonObject): APIResult {
    val choices = response["choices"] as? kotlinx.serialization.json.JsonArray ?: throw APIClient.APIException.InvalidResponse
    val message = (choices.firstOrNull() as? JsonObject)?.get("message") as? JsonObject
        ?: throw APIClient.APIException.InvalidResponse
    val text = (message["content"] as? JsonPrimitive)?.contentOrNull?.trim()
        ?: throw APIClient.APIException.InvalidResponse
    if (text.isEmpty()) throw APIClient.APIException.InvalidResponse
    val usage = response["usage"]?.jsonObject
    return APIResult(
        text = text,
        sources = emptyList<SourceLink>(),
        usage = APIUsage(
            input = ((usage?.get("prompt_tokens") as? JsonPrimitive)?.content?.toIntOrNull() ?: 0).coerceIn(0, 1_000_000_000),
            output = ((usage?.get("completion_tokens") as? JsonPrimitive)?.content?.toIntOrNull() ?: 0).coerceIn(0, 1_000_000_000),
        ),
    )
}
