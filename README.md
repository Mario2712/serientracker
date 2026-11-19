# Serientracker

Android-App, um geschaute Serien zu tracken: Staffeln, Folgen, Status, Bewertung.
Kotlin · Jetpack Compose · Material 3 · Room. Alles offline, alles lokal.

## App aufs Handy (Google Pixel)

Jeder Push baut die App über GitHub Actions und stellt sie zum Download bereit.

1. Am Pixel die Seite **Releases → „Neueste Version“** öffnen
   (`https://github.com/mario2712/serientracker/releases/tag/latest-build`).
2. `serientracker.apk` herunterladen und öffnen.
3. Beim ersten Mal fragt Android, ob der Browser Apps installieren darf: **Zulassen**.
4. Weitere Builds lassen sich einfach drüber installieren, die Daten bleiben erhalten.
   Alle Builds sind mit demselben Debug-Key (`app/debug.keystore`) signiert.

Alternativ: Unter **Actions → letzter Lauf → Artifacts** liegt die APK als ZIP.

## Erster Start: Excel-Liste + TMDB

1. Beim ersten Start lädt die App automatisch deine Excel-Liste (Blatt „Serien“, 361 Serien)
   aus `app/src/main/assets/seed/serien.json`.
2. Für exakte Staffeln und Folgen einen kostenlosen **TMDB-API-Key** holen:
   Account auf [themoviedb.org](https://www.themoviedb.org/signup) anlegen →
   Einstellungen → API → „API-Schlüssel“ (oder „API-Lesezugriffstoken“) kopieren.
3. In der App: **Menü (⋮) → Einstellungen** → Key einfügen → „Speichern & prüfen“.
4. **Menü → TMDB-Abgleich → Abgleich starten.** Eindeutige Treffer werden automatisch übernommen
   und bis zu deinem Excel-Stand abgehakt. Den Rest unter „Bitte prüfen“ ordnest du selbst zu.

### Excel aktualisieren

```bash
pip install openpyxl
python3 tools/convert_excel.py ~/Downloads/Serienliste.xlsx   # schreibt serien.json neu
```
Danach committen und pushen. In der App holt **Einstellungen → Auf Excel-Stand zurücksetzen**
die neue Liste. Achtung: Dabei geht der Fortschritt verloren, der nur in der App erfasst wurde.

## Lokal bauen

Voraussetzung: JDK 17+ und ein Android SDK (Android Studio installiert beides).

```bash
./gradlew assembleDebug          # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # Unit-Tests
./gradlew installDebug           # direkt aufs per USB verbundene Handy
```

## Doku

- [Architektur & Datenmodell](docs/ARCHITECTURE.md)
- [Feature-Liste & Roadmap](docs/FEATURES.md)
