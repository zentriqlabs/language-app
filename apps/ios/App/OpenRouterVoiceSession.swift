import AVFoundation
import Foundation
import MuralCore

/// Turn-based OpenRouter voice (STT → LLM → TTS). Emits the same events as LiveTransport.
@MainActor final class OpenRouterVoiceSession {
    var onEvent: (([String: Any]) -> Void)?
    var onLevels: ((Double, Double) -> Void)?
    var onFailure: ((String) -> Void)?
    private(set) var started = false
    private(set) var isMuted = false
    private var task: Task<Void, Never>?
    private var overlay = ""
    private var history: [(String, String)] = []
    private var listenGate = false

    func connect(instructions: String, history: [[String: Any]], client: OpenRouterAPIClient) async throws {
        disconnect()
        self.history = []
        for item in history {
            guard item["type"] as? String == "message",
                  let role = item["role"] as? String,
                  let content = (item["content"] as? [[String: Any]])?.first?["text"] as? String else { continue }
            self.history.append((role == "assistant" ? "assistant" : "user", content))
        }
        started = false
        listenGate = false
        task = Task {
            do {
                onEvent?(["type": "mural.session.created", "session": ["id": UUID().uuidString]])
                onEvent?(["type": "session.started", "session": ["id": "openrouter"]])
                started = true
                while !listenGate { try await Task.sleep(for: .milliseconds(100)) }
                while !Task.isCancelled {
                    if isMuted { try await Task.sleep(for: .milliseconds(200)); continue }
                    guard let utterance = try await recordUtterance() else { continue }
                    let transcript = try await transcribe(pcm: utterance, client: client)
                    emitTranscript(type: "session.input_transcript.delta", text: transcript)
                    history.append(("user", transcript))
                    let reply = try await client.voiceReply(system: instructions, overlay: overlay, history: history)
                    overlay = ""
                    try await speak(reply, client: client)
                    history.append(("assistant", reply))
                    emitTranscript(type: "session.output_transcript.delta", text: reply)
                }
            } catch is CancellationError { }
            catch { onFailure?(error.localizedDescription) }
            started = false
        }
    }

    func send(_ event: [String: Any]) -> Bool {
        guard started else { return false }
        let type = event["type"] as? String ?? ""
        let content = event["content"] as? String ?? ""
        if type == "session.instructions.append" {
            overlay = (overlay + "\n" + content).prefix(4000).description
            listenGate = true
        } else if type == "session.thinking.append" {
            overlay = (overlay + "\n" + content).prefix(4000).description
        } else if type == "session.commentary.append" {
            Task { try? await speak(content, client: OpenRouterAPIClient()) }
        }
        return true
    }

    func mute(_ muted: Bool) { isMuted = muted }
    func close() { onEvent?(["type": "session.closed", "usage": ["seconds": 0]]) }
    func disconnect() { task?.cancel(); task = nil; started = false }

    private func speak(_ text: String, client: OpenRouterAPIClient) async throws {
        let data = try await client.synthesizeSpeech(text)
        let player = try AVAudioPlayer(data: data)
        player.prepareToPlay()
        player.play()
        while player.isPlaying { try await Task.sleep(for: .milliseconds(80)) }
    }

    private func emitTranscript(type: String, text: String) {
        onEvent?([
            "type": type,
            "event_id": UUID().uuidString,
            "delta": text,
            "start_ms": 0,
            "end_ms": max(1, text.count * 45),
        ])
    }

    private func recordUtterance() async throws -> Data? {
        // Minimal placeholder: real device builds should mirror Android PCM capture.
        try await Task.sleep(for: .milliseconds(300))
        return nil
    }

    private func transcribe(pcm: Data, client: OpenRouterAPIClient) async throws -> String {
        _ = pcm
        _ = client
        return ""
    }
}
