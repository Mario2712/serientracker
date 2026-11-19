# Features

✅ = im Grundgerüst enthalten · 🔜 = nächster Schritt · 💡 = Idee

## Kern (MVP)
- ✅ Serien anlegen, bearbeiten, löschen (Titel, Status, Staffeln mit Folgenanzahl, Streamingdienst, Bewertung, Notizen)
- ✅ Übersicht mit Fortschrittsbalken und „als Nächstes S01E05“
- ✅ Filter nach Status (Schaue ich / Geplant / Pausiert / Fertig / Abgebrochen) mit Anzahl
- ✅ Suche nach Titel
- ✅ „Nächste Folge gesehen“ mit einem Tipp, direkt aus der Liste
- ✅ Detailansicht: Folgen einzeln oder ganze Staffel abhaken
- ✅ Automatischer Statuswechsel (begonnen → Schaue ich, alles gesehen → Fertig)
- ✅ Material You (Farben vom Pixel-Wallpaper), Dark Mode
- ✅ Android-Backup der Datenbank (Google-Konto)
- ✅ **Excel-Liste als Startdaten** (361 Serien, Status-Mapping, Wer, Genre)
- ✅ Status „Aufgeholt“ (alles gesehen, wartet auf neue Folgen)
- ✅ Filter „Wer“: Meine / Johannas / Gemeinsam
- ✅ **Excel-Export** `serienliste_yyyymmdd.xlsx` mit Original-Logik + Blatt „Info“
- ✅ **Archiv** (JSON) exportieren und wiederherstellen
- ✅ Folgendetails per langem Drücken: Titel, Erstausstrahlung, abgehakt am (änderbar)
- ✅ „Über“-Screen: Version, Entwickler, Datenstand
- 🔜 Rückgängig-Snackbar nach dem Abhaken oder Löschen

## Komfort
- 🔜 Favoriten markieren und oben anheften
- 🔜 Sortierung: zuletzt geschaut, Titel, Fortschritt, Bewertung
- 💡 Swipe-Gesten in der Liste (rechts = nächste Folge, links = pausieren)
- 💡 Home-Screen-Widget „Weiterschauen“ (Jetpack Glance)
- 💡 App Shortcuts (lange auf das Icon drücken → zuletzt geschaute Serie)
- 💡 „Bis hier gesehen“: lange auf eine Folge drücken → alle davor abhaken

## Online-Daten (TMDB, kostenloser API-Key)
- ✅ Massenabgleich: exakte Staffeln/Folgen, Excel-Stand wird abgehakt, Prüfliste + Abweichungen
- ✅ Serie per TMDB-Suche anlegen, Poster, Episodentitel und Ausstrahlungsdaten
- ✅ Einzelne oder alle Serien aktualisieren (neue Folgen)
- 💡 „Neue Folgen verfügbar“: wöchentliche Prüfung per WorkManager, mit Benachrichtigung
- 💡 Wo läuft's? (Streaming-Anbieter für AT/DE)

## Statistik
- 💡 Gesehene Folgen und geschätzte Stunden (gesamt, pro Monat, pro Jahr)
- 💡 Heatmap / Verlauf nach `watchedAt`
- 💡 „Jahresrückblick“: Top-Serien, Binge-Rekorde

## Sync & Teilen
- 💡 Sync zwischen Geräten (Google Drive App-Folder oder Firebase)
- 💡 Liste mit Freunden teilen (Export als Bild/Link)

## Technik / Qualität
- ✅ CI: Build und Unit-Tests bei jedem Push, APK als Release-Download
- 🔜 Room-Migrationstests, UI-Tests (Compose Testing)
- 💡 Release-Signing mit eigenem Keystore (GitHub Secrets) für Play Store / F-Droid
- 💡 Baseline Profiles für schnelleren App-Start
