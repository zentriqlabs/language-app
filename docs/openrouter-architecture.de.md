# Mural-Architektur mit OpenRouter (Überblick)

## Gesamtaufbau

| Schicht | Rolle |
| --- | --- |
| **Android/iOS App** | UI, lokale Lernhistorie, Mikrofon/WebRTC oder Pipeline-Voice |
| **OpenAI (Standard-BYOK)** | `gpt-live-1` per WebRTC, `gpt-5.6-luna` für Hilfen |
| **OpenRouter (neu)** | DeepSeek V4 Pro (LLM), Qwen3 ASR Flash (STT), Kokoro 82M (TTS) |
| **services/api** | Konten, Minuten, optional gehostete Stimme; TTS-Cache unter `/v1/openrouter/tts` |
| **mural.chat** | Marketing/Website (separates Repo) |

## Datenfluss Android (OpenRouter, lokal auf dem Handy)

1. OpenRouter-API-Schlüssel (`sk-or-…`) in **Einstellungen** speichern (Keystore).
2. **Talk** startet die Pipeline: Aufnahme → STT → LLM → TTS (mit lokalem Phrasen-Cache).
3. Hilfen (Bedeutung, Bewertung, Wortsuche) nutzen dieselbe OpenRouter-LLM-Route.
4. Lern-Daten bleiben auf dem Gerät; Audio geht an OpenRouter, nicht an Mural-Server.

## Kostenoptimierung

- **Kurzantworten:** Systemzusatz `VOICE_BREVITY_PROMPT` begrenzt gesprochene Antworten auf 1–2 Sätze (weniger TTS-Zeichen).
- **TTS-Cache:** Identischer Text + Stimme + Modell → wiederverwendete Audiodatei (App-Cache + API-Cache auf Disk; Redis als spätere Front-Layer möglich).

## Russisch

Neues Modul `ru` / Locale `ru-RU` in den Sprachregistries (iOS + exportiertes Android `Languages.kt`).

## Lokale Nutzung

```sh
cd apps/android
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

OpenRouter-Schlüssel in der App hinterlegen; gehostete Minuten bleiben unverändert an `api.mural.chat` gebunden.
