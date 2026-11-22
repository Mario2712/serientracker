# Changelog

Neue Version: `version.txt` anheben und hier einen Abschnitt `## x.y.z` ergänzen.
Nach dem Merge auf `main` erstellt CI daraus automatisch das Release `vx.y.z` mit APK.

## 1.2.2

- Bewertungs-Schieberegler (Folgen und Serien): Der Wert springt nicht mehr gelegentlich eine Stufe zurück
  (z. B. 8 → 7), sondern bleibt dort, wo du loslässt.

## 1.2.1

- Rückfrage „Serie schon vorhanden“: Die drei Buttons („Vorhandene öffnen“, „Trotzdem neu anlegen“, „Abbrechen“)
  stehen jetzt untereinander in voller Breite und sind nicht mehr abgeschnitten.

## 1.2.0

- **Doppelte Serien:** Beim Anlegen fragt die App nach, wenn es die Serie schon gibt (gleiche TMDB-Serie oder
  gleicher Titel): „Vorhandene öffnen“, „Trotzdem neu anlegen“ oder „Abbrechen“. Neu angelegt wird mit allen
  Staffeln, Folgen und Infos des vorhandenen Eintrags, aber ohne Fortschritt.
- **Zusatzname** (z. B. „Rewatch 2026“ oder „Mario alleine“) unterscheidet doppelte Einträge in Liste, Detail,
  Suche und Excel-Export. In „Demnächst“ und bei Benachrichtigungen erscheint dieselbe Serie nur einmal.
- **„Als neuen Durchgang anlegen“** im Menü einer Serie, z. B. für einen gemeinsamen Rewatch.
- **Mehrfach gesehen:** Im Folgen-Detailfenster gibt es „Nochmal gesehen“ und die Liste aller Sichtungen mit Datum
  (ändern, löschen); die Kachel zeigt „2×“; „Staffel nochmal gesehen“ in der Staffelkarte.
  Sehzeit, Verlauf, Binge, Heatmap und Jahresrückblick zählen jede Sichtung, Fortschritt und Excel jede Folge einmal.
  Neue Statistik „Meistgesehene Folgen“.
- **Zeitzone pro Eintrag:** Beim Abhaken wird die Zeitzone mitgespeichert. Uhrzeiten und Statistiken zeigen die
  Ortszeit zum Zeitpunkt des Schauens, auch wenn du später auf Reisen nachsiehst. Bisherige Einträge gelten als
  Europe/Vienna. Eintragen funktioniert in jeder Zeitzone.

## 1.1.0

- **Nachtragen-Modus** (Menü der Serienliste oder Einstellungen): Solange er an ist, zählt Abhaken als „früher gesehen“.
  Solche Folgen zählen für Fortschritt, Status und Sehzeit, aber nicht für Verlauf, aktivsten Tag, Binge-Rekorde,
  Heatmap, Jahresrückblick und „Wie aktuell schaust du?“. Ein Banner zeigt an, dass der Modus aktiv ist.
- **Statistik korrigieren** (Einstellungen → Nachtragen): Die Tage mit den meisten Abhakungen werden aufgelistet;
  ein Tipp markiert die Abhakungen eines Tages nachträglich als nachgetragen. Ein beliebiger Tag lässt sich auch wählen.
- Im Folgen-Detailfenster heißt es bei solchen Folgen jetzt „früher gesehen (ohne genaues Datum)“.

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
