import Foundation

/// Mirrors shared/openrouter/models.json — replace slugs when your housing park IDs are final.
enum OpenRouterModels {
    static let baseURL = URL(string: "https://openrouter.ai/api/v1")!
    static let llm = "deepseek/deepseek-v4-pro-0813"
    static let stt = "qwen/qwen3-asr-flash-2026-02-10"
    static let tts = "hexgrad/kokoro-82m"
    static let defaultVoice = "af_bella"
    static let voiceBrevity = "Antworte im Sprachmodus stets kurz, direkt und in maximal zwei kurzen Sätzen."

    static func isOpenRouterKey(_ key: String) -> Bool {
        let trimmed = key.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.hasPrefix("sk-or-") && trimmed.count >= 24 && !trimmed.contains(where: \.isWhitespace)
    }
}
