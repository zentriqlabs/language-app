# Private Nutzung (Android) — Speicher, Offline, Algorithmus

## Brauchst du Firebase?

**Nein** für deinen privaten Gebrauch. Mural speichert:

| Daten | Wo |
| --- | --- |
| Gespräche, Wörter, Einstellungen | Lokal auf dem Handy (`LearningRepository` / JSON-Archiv) |
| OpenRouter-Schlüssel | Android Keystore (verschlüsselt) |
| TTS-Cache (Phrasen) | App-Cache (~50 MB LRU, `PhraseAudioCache`) |
| Optional: Backend | Nur wenn du `services/api` selbst hostest (Konten/Minuten — für privat **aus**) |

Firebase würde Sync/Konten bringen, ist aber **nicht nötig** für Offline-Lernhistorie und BYOK.

## Onboarding

Zwei Schritte in `OnboardingScreen`: Lernsprache → Bedeutungssprache → KI-Einwilligung (`hasOnboarded`, `aiConsentVersion`).  
Interessen trägst du unter **Einstellungen** ein; sie fließen in `TeachingPolicy.voice` (Personalisation).

## Fortschritts-Algorithmus („2 vor, 1 zurück“)

`LearningEngine.project` (Android/iOS Core):

- **Level 0–5** aus Bewertungen (`Outcome.success` / `breakdown`).
- Bei **2 Erfolgen in Folge**: Level steigt (max. vorgeschlagenes Level).
- Bei **breakdown**: Level −1 (min. 0) — das ist der „Schritt zurück“.
- **Wörter**: Recall-Balken 0–3, fällige Wörter (`dueAt`) werden im System-Prompt zur Wiederholung genannt.
- Kein CEFR-Zertifikat — heuristische App-Logik.

## Segment-Cache (längere Antworten)

Antworten werden in **Sätze/kurze Phrasen** zerlegt (`SegmentedPhrasePlayer`). Jede Einheit wird einzeln gecacht und **aneinander abgespielt** — ähnlich wie ein Satzbaukasten aus Kernwortschatz + zuletzt gehörten Snippets.

**Prefetch-Priorität** (`CachePrioritizer`):

1. Zuletzt gehörte kurze Assistenten-Sätze (letzte Sessions)
2. Fällige / fragile Lemmas („2 vor, 1 zurück“)
3. High-Frequency-Chunks + Begrüßung + Standard-Feedback
4. Einträge aus deinen **Interessen** (kurze Stichworte)

## Mikrofon: wann an / aus?

| Zustand | Mikrofon |
|---------|----------|
| Talk **idle** | Aus |
| **connecting** | Berechtigung nötig; Aufnahme startet mit aktiver Session |
| **active**, nicht stumm | An zwischen den Turns (VAD: ~1,2 s Stille beendet deinen Turn) |
| **active**, stumm | Aufnahme pausiert |
| **KI spricht** (TTS) | Aufnahme **aus** (kein Echo in STT) |
| **Heimatsprache erkannt** | Hilfe-Bridge per Sprache, kein normaler Zielsprachen-Turn |
| **end / Hintergrund / closing** | Aus, Session beendet |

## Offline & Cache

- **Ohne Internet:** Gesprächsverlauf, Wörterbuch, exportierter Backup = nutzbar.
- **Stimme live:** braucht OpenRouter (STT/LLM/TTS) — **nicht** voll offline.
- **TTS-Cache:** Begrüßung, Standard-Feedback, bis zu 12 fällige **Lemmas** werden bei Schlüssel-Speicherung vorgeladen (`OfflinePhrasePrefetch`).
- **Speicher:** LRU begrenzt Cache; nicht jedes Wort vorab speichern — nur häufige Phrasen + priorisierte Lemmas.

## Heimatsprache → Zielsprache

In **Talk → Tippen** Text mit `::` beginnen, z. B. `::Wie sage ich „Bahnhof“?` → `bridgeFromHomeLanguage`.

**Per Mikrofon:** Während einer aktiven Talk-Session erkennt die App (über `LanguageDetector`) deine **Bedeutungssprache**. Dann wird automatisch die Hilfe-Bridge ausgelöst — ohne `::`.

## Current Topic (OpenRouter)

`currentTopic()` nutzt wieder `TeachingPolicy.currentTopic` plus `OpenRouterAPIClient.researchTopic()` (Modell in `shared/openrouter/models.json` → `topicResearch`, Platzhalter `:online` bis du den exakten Slug nennst).

## Gehostete Stimme / Minuten / Websuche (entfernt für privat)

- **Gehostete Stimme:** `api.mural.chat` + OpenAI **GPT-Live** (WebRTC) für Konten mit Minutenpaket — bei deinem Build **aus** (`MANAGED_API_ORIGIN` leer).
- **Nutzen früher:** Kein eigener OpenAI-Key nötig, zentrale Abrechnung, niedrige Latenz durch Live-Audio.
- **Privat:** BYOK OpenRouter (STT/LLM/TTS); Topic-Recherche über OpenRouter statt Mural-Websuche.

## Android installieren

```sh
cd apps/android && ./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

OpenRouter-Schlüssel in der App hinterlegen.
