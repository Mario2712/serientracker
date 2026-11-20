# Changelog

Neue Version: `version.txt` anheben und hier einen Abschnitt `## x.y.z` ergänzen.
Nach dem Merge auf `main` erstellt CI daraus automatisch das Release `vx.y.z` mit APK.

## 1.0.1

- Neue Einstellung „Excel-Vergleiche anzeigen“: lässt sich ausschalten, sobald nur noch die App genutzt wird.
  Blendet die Abweichungen zur Excel (Serie, Statistik, TMDB-Abgleich), den Excel-Stand noch nicht abgeglichener
  Serien und „Auf Excel-Stand zurücksetzen“ aus. Der Excel-Export bleibt verfügbar.

## 1.0.0

Erste stabile Version des Serientrackers.

### Serien & Folgen
- Deine Excel-Serienliste (361 Serien) ist als Startdaten eingebaut, inklusive Status, Wer und Genre.
- Abgleich mit TMDB: exakte Staffeln und Folgen, deutscher Titel und Originaltitel, Genre, Poster.
  Dein Excel-Stand wird beim Abgleich automatisch abgehakt.
- Status: Schaue ich, Aufgeholt, Pausiert, Geplant, Fertig, Abgebrochen; wechselt automatisch beim Abhaken.
- Folgen antippen = abhaken. Lange drücken öffnet die Details: Titel, Erstausstrahlung, abgehakt am (änderbar),
  Bewertung 1–10 und den Typ (Folge / Special / ausgeblendet).
- Specials chronologisch mit Titel; eigene Specials für Filme oder Extras, die TMDB nicht in der Serie führt.
- Abweichungen zur Excel werden in der Serie und in der Statistik angezeigt.
- „Über die Serie“: Beschreibung, TMDB-Bewertung, Besetzung, Trailer.

### Übersicht
- Filter nach Status, Wer (Meine / Johannas / Gemeinsam) und Genre, Suche (auch nach Originaltitel).
- Sortierung: zuletzt geschaut, Titel, Fortschritt, Bewertung, nächste Ausstrahlung.
- „Demnächst“: neu erschienene und angekündigte Folgen.
- Benachrichtigung bei neuen Folgen (täglich, für laufende, pausierte, aufgeholte und fertige Serien).

### Statistik
- Genres, Top-Folgen, -Staffeln und -Serien, Sehzeit, Verlauf, Binge-Rekorde, Wer & Status, Stapel der Schande.
- Jahresrückblick, Ich vs. TMDB, „Wie aktuell schaust du?“, Wochentag/Uhrzeit-Heatmap.

### Daten
- Excel-Export `serienliste_yyyymmdd.xlsx` im Format der ursprünglichen Liste (+ Spalte Originaltitel, Blatt „Info“).
- Archiv als JSON exportieren und wiederherstellen; automatisches wöchentliches Backup in einen Ordner deiner Wahl.

### Hinweise
- Für den TMDB-Abgleich braucht die App einen kostenlosen TMDB-API-Key (Einstellungen).
- Die APK ist mit einem Debug-Schlüssel signiert: gut zum Installieren am eigenen Handy, nicht für den Play Store.
- Serien- und Folgendaten von TMDB; diese App nutzt die TMDB-API, ist aber nicht von TMDB zertifiziert.
