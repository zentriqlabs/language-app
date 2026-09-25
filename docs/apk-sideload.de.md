# APK ohne Play Store installieren & aktualisieren

## Woher kommt die APK?

1. **Selbst bauen** (empfohlen für dich als Entwickler):
   ```sh
   cd apps/android
   ./gradlew :app:assembleDebug
   ```
   Datei: `apps/android/app/build/outputs/apk/debug/app-debug.apk`

2. **Vom Agent / CI:** Nach jedem Push auf den Feature-Branch kann dieselbe Datei aus dem Build-Artefakt oder GitHub Actions (falls eingerichtet) geladen werden.

**Niemals** den OpenRouter-Schlüssel in die APK packen — nur in der App unter Einstellungen eingeben (Keystore).

## Schritt für Schritt (Android)

1. **Entwickleroptionen** aktivieren (Build-Nummer 7× tippen).
2. **USB-Debugging** oder **Drahtlos debuggen** (Android 11+) einschalten.
3. APK aufs Handy kopieren (USB, Cloud, E-Mail) **oder** vom PC:
   ```sh
   adb install -r apps/android/app/build/outputs/apk/debug/app-debug.apk
   ```
4. Beim ersten Mal: „Aus dieser Quelle installieren“ erlauben.
5. App öffnen → Onboarding → **OpenRouter-Schlüssel** in Einstellungen → erweitert speichern.
6. Schalter **„Use OpenRouter“** kann KI jederzeit abschalten, ohne den Schlüssel zu löschen.

## Updates ohne Play Store

| Weg | Vorgehen |
|-----|----------|
| **Neue APK installieren** | Gleiche App-ID (`chat.mural.android`), `adb install -r` — **Daten bleiben** (Lernhistorie, Schlüssel). |
| **GitHub Release** | Optional: Release mit `app-debug.apk` oder `app-release.apk` — du lädst die neue Datei und installierst mit `-r`. |
| **Automatisch OTA** | Nicht eingebaut (bewusst für Privatnutzung). Play Store oder eigener Update-Server wären separater Aufwand. |

Nach jedem Code-Update: erneut bauen, `-r` installieren — fertig.

## Sicherheit des Schlüssels

- Verschlüsselt mit **Android Keystore** (AES-GCM), nicht im Quellcode.
- Nicht in Backups der Lern-JSON enthalten.
- Ausschalten über den Schalter stoppt alle API-Aufrufe (`read()` liefert dann nichts).
