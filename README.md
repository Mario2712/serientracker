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
