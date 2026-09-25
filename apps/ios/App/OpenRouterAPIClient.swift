import Foundation
import MuralCore

@MainActor final class OpenRouterAPIClient {
    private let session: URLSession
    init() {
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 60
        config.timeoutIntervalForResource = 120
        config.httpCookieStorage = nil
        config.urlCache = nil
        session = URLSession(configuration: config)
    }

    func respond(instructions: String, input: String, schema: [String: Any]? = nil) async throws -> APIResult {
        guard let key = CredentialStore.read(), OpenRouterModels.isOpenRouterKey(key) else { throw APIClient.APIError.missingKey }
        var body: [String: Any] = [
            "model": OpenRouterModels.llm,
            "messages": [
                ["role": "system", "content": instructions],
                ["role": "user", "content": input],
            ],
            "max_tokens": schema == nil ? 1400 : 2200,
        ]
        if let schema {
            body["response_format"] = [
                "type": "json_schema",
                "json_schema": ["name": "mural_result", "strict": true, "schema": schema],
            ]
        }
        let json = try await post(path: "chat/completions", key: key, body: body)
        guard let choices = json["choices"] as? [[String: Any]],
              let message = choices.first?["message"] as? [String: Any],
              let text = message["content"] as? String, !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw APIClient.APIError.invalidResponse
        }
        let usage = json["usage"] as? [String: Any]
        return APIResult(
            text: text,
            sources: [],
            usage: APIUsage(
                input: usage?["prompt_tokens"] as? Int ?? 0,
                output: usage?["completion_tokens"] as? Int ?? 0
            )
        )
    }

    func voiceReply(system: String, overlay: String, history: [(role: String, content: String)]) async throws -> String {
        guard let key = CredentialStore.read() else { throw APIClient.APIError.missingKey }
        var messages: [[String: Any]] = [[
            "role": "system",
            "content": system + "\n\n" + OpenRouterModels.voiceBrevity + (overlay.isEmpty ? "" : "\n\n" + overlay),
        ]]
        for item in history.suffix(24) {
            messages.append(["role": item.role, "content": String(item.content.prefix(4000))])
        }
        let json = try await post(path: "chat/completions", key: key, body: [
            "model": OpenRouterModels.llm,
            "max_tokens": 220,
            "temperature": 0.7,
            "messages": messages,
        ])
        guard let choices = json["choices"] as? [[String: Any]],
              let text = choices.first?["message"] as? [String: Any],
              let content = text["content"] as? String else { throw APIClient.APIError.invalidResponse }
        return content.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    func synthesizeSpeech(_ text: String) async throws -> Data {
        guard let key = CredentialStore.read() else { throw APIClient.APIError.missingKey }
        var request = URLRequest(url: OpenRouterModels.baseURL.appendingPathComponent("audio/speech"))
        request.httpMethod = "POST"
        request.setValue("Bearer \(key)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("https://mural.chat/", forHTTPHeaderField: "HTTP-Referer")
        request.setValue("Mural iOS", forHTTPHeaderField: "X-OpenRouter-Title")
        request.httpBody = try JSONSerialization.data(withJSONObject: [
            "model": OpenRouterModels.tts,
            "input": String(text.prefix(900)),
            "voice": OpenRouterModels.defaultVoice,
        ])
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw APIClient.APIError.http((response as? HTTPURLResponse)?.statusCode ?? 0)
        }
        if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let b64 = json["audio"] as? String, let decoded = Data(base64Encoded: b64) { return decoded }
        return data
    }

    private func post(path: String, key: String, body: [String: Any]) async throws -> [String: Any] {
        var request = URLRequest(url: OpenRouterModels.baseURL.appendingPathComponent(path))
        request.httpMethod = "POST"
        request.setValue("Bearer \(key)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("https://mural.chat/", forHTTPHeaderField: "HTTP-Referer")
        request.setValue("Mural iOS", forHTTPHeaderField: "X-OpenRouter-Title")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw APIClient.APIError.http((response as? HTTPURLResponse)?.statusCode ?? 0)
        }
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else { throw APIClient.APIError.invalidResponse }
        return json
    }
}
