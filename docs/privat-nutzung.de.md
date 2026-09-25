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

## Offline & Cache

- **Ohne Internet:** Gesprächsverlauf, Wörterbuch, exportierter Backup = nutzbar.
- **Stimme live:** braucht OpenRouter (STT/LLM/TTS) — **nicht** voll offline.
- **TTS-Cache:** Begrüßung, Standard-Feedback, bis zu 12 fällige **Lemmas** werden bei Schlüssel-Speicherung vorgeladen (`OfflinePhrasePrefetch`).
- **Speicher:** LRU begrenzt Cache; nicht jedes Wort vorab speichern — nur häufige Phrasen + priorisierte Lemmas.

## Heimatsprache → Zielsprache

In **Talk → Tippen** Text mit `::` beginnen, z. B. `::Wie sage ich „Bahnhof“?` → `bridgeFromHomeLanguage`.

## Gehostete Stimme / Minuten / Websuche (entfernt für privat)

- **Gehostete Stimme:** Mural-Server führt OpenAI Live für zahlende Nutzer aus — bei dir **deaktiviert** (`MANAGED_API_ORIGIN` leer).
- **Websuche/Delegation:** Früher Live-Themen & Fakten über OpenAI — jetzt **ohne Websuche** (`currentTopicOffline`, Delegation ohne `search`).

## Android installieren

```sh
cd apps/android && ./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

OpenRouter-Schlüssel in der App hinterlegen.
