# Serientracker

Android-App, um geschaute Serien zu tracken: Staffeln, Folgen, Status, Bewertung.
Kotlin · Jetpack Compose · Material 3 · Room. Alles offline, alles lokal.

Aktuelle Version: siehe [`version.txt`](version.txt) · Änderungen: [`CHANGELOG.md`](CHANGELOG.md)

## App aufs Handy (Google Pixel)

Es gibt zwei Download-Links:

| | Link | Inhalt |
|---|---|---|
| **Stabile Version** | `https://github.com/mario2712/serientracker/releases/latest` | Das Release der aktuellen Version (z. B. v1.0.0), entsteht beim Merge auf `main` |
| **Neuester Testbuild** | `https://github.com/mario2712/serientracker/releases/tag/latest-build` | Wird bei jedem Push neu gebaut |

1. Am Pixel einen der Links öffnen.
2. Die APK herunterladen und öffnen.
3. Beim ersten Mal fragt Android, ob der Browser Apps installieren darf: **Zulassen**.
4. Weitere Builds lassen sich einfach drüber installieren, die Daten bleiben erhalten.
   Alle Builds sind mit demselben Debug-Key (`app/debug.keystore`) signiert.

Alternativ: Unter **Actions → letzter Lauf → Artifacts** liegt die APK als ZIP.

### Neue Version veröffentlichen
1. `version.txt` anheben (z. B. `1.1.0`) und in `CHANGELOG.md` einen Abschnitt `## 1.1.0` ergänzen.
2. Auf `main` mergen. CI baut, testet und erstellt das Release `v1.1.0` mit APK und den Changelog-Notizen.

## Erster Start: Excel-Liste + TMDB

1. Beim ersten Start lädt die App automatisch deine Excel-Liste (Blatt „Serien“, 361 Serien)
   aus `app/src/main/assets/seed/serien.json`.
2. Für exakte Staffeln und Folgen einen kostenlosen **TMDB-API-Key** holen:
   Account auf [themoviedb.org](https://www.themoviedb.org/signup) anlegen →
   Einstellungen → API → „API-Schlüssel“ (oder „API-Lesezugriffstoken“) kopieren.
3. In der App: **Menü (⋮) → Einstellungen** → Key einfügen → „Speichern & prüfen“.
4. **Menü → TMDB-Abgleich → Abgleich starten.** Eindeutige Treffer werden automatisch übernommen
   und bis zu deinem Excel-Stand abgehakt. Den Rest unter „Bitte prüfen“ ordnest du selbst zu.

## Export & Archiv

In der App unter **⋮ → Einstellungen → Export & Archiv**:
- **Excel exportieren:** schreibt `serienliste_yyyymmdd.xlsx` im Format der ursprünglichen Serienliste
  (gleiche Spalten und Logik, Anteil als Formel, orange Markierung in „nächste“) plus ein Blatt „Info“
  mit App-Version und Entwickler. CI prüft bei jedem Build, dass der Export der Original-Excel
  feldgenau entspricht (Round-Trip).
- **Archiv exportieren:** `serientracker_backup_yyyymmdd.json` mit allen Serien, Folgen und Abhak-Zeitpunkten.
- **Aus Archiv wiederherstellen:** ersetzt den aktuellen Stand durch ein Archiv (z. B. am neuen Handy).

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
