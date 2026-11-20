# Architektur

## Stack

| Bereich | Wahl | Warum |
|---|---|---|
| Sprache | Kotlin 2.0 | Standard für Android |
| UI | Jetpack Compose + Material 3 | Deklarativ, Material You (dynamische Farben am Pixel) |
| Persistenz | Room (SQLite) | Offline, typsicher, Flows für reaktive UI |
| Online-Daten | TMDB v3 über Retrofit + kotlinx.serialization | Exakte Staffeln/Folgen, deutsche Titel, Poster |
| Einstellungen | DataStore Preferences | TMDB-Key, Flag „Startdaten importiert“ |
| Bilder | Coil | Poster |
| Navigation | Navigation Compose | Ein Activity, mehrere Screens |
| DI | Manuell (`AppContainer`) | Reicht bei 1 Repository; Hilt erst, wenn Netzwerk/Worker dazukommen |
| Build/CI | Gradle Version Catalog, GitHub Actions | APK bei jedem Push, Download per Release-Link |
| minSdk / target | 26 / 35 | Android 8+ deckt praktisch alles ab |

## Schichten (MVVM + Repository, Unidirectional Data Flow)

```
┌──────────── UI (Compose) ────────────┐
│  Screen  ──events──▶  ViewModel      │   Screens sind zustandslos,
│     ▲                    │           │   bekommen UiState als StateFlow
│     └──── UiState ◀──────┘           │
└──────────────────┬───────────────────┘
                   ▼
           SeriesRepository            ◀── einziger Zugang zu Daten;
                   │                       hier docken später TMDB,
       ┌───────────┼────────────┐          Excel-Import und Backup an
       ▼           ▼            ▼
   Room DAOs   TMDB API    Excel-Seed (assets)
       │
    SQLite
```

- **`domain/`**: reine Kotlin-Logik ohne Android-Abhängigkeit (`WatchStatus`, `Progress`), voll unit-testbar.
- **`data/local/`**: Room-Entities, DAOs, `AppDatabase`.
- **`data/repository/`**: `SeriesRepository` (manuelle Änderungen), `TmdbSyncRepository` (Folgen von TMDB holen,
  Excel-Position anwenden), `TmdbSyncCoordinator` (Massenabgleich im App-Scope).
- **`data/remote/`**: `TmdbApi` (Retrofit), `TmdbClient` (Auth, Staffeln per `append_to_response`), Modelle.
- **`data/seed/`**: `SeedImporter` lädt die Excel-Liste beim ersten Start.
- **`ui/<feature>/`**: pro Screen ein `Screen.kt` und ein `ViewModel.kt`.

### Paketstruktur

```
app/src/main/java/io/github/mario2712/serientracker/
├── SerienTrackerApp.kt        Application + AppContainer (DI)
├── MainActivity.kt
├── domain/                    WatchStatus, Progress (Regeln)
├── data/
│   ├── local/                 Entities, SeriesDao, EpisodeDao, AppDatabase
│   └── repository/            SeriesRepository
└── ui/
    ├── theme/                 Material-3-Theme
    ├── navigation/            AppNavHost, Routes
    ├── components/            wiederverwendbare Composables
    ├── serieslist/            Übersicht mit Filter + Suche
    ├── seriesdetail/          Staffeln/Folgen abhaken
    └── editseries/            Anlegen/Bearbeiten
```

Wenn die App wächst: Aufteilung in Gradle-Module (`:core:data`, `:core:domain`, `:feature:*`). Für den Anfang ist ein Modul schneller und einfacher.

## Datenmodell

```
series 1 ──── n seasons 1 ──── n episodes
```

### `series`
| Spalte | Typ | Beschreibung |
|---|---|---|
| id | Long PK | |
| title | String | indiziert |
| status | Enum `WatchStatus` | WATCHING, CAUGHT_UP (aufgeholt), PAUSED, PLAN_TO_WATCH, COMPLETED, DROPPED |
| watcher | Enum `Watcher`? | ME, JOHANNA, BOTH (Excel „Wer“) |
| genre | String? | |
| rating | Int? | 1–10 |
| streamingService | String? | z. B. Netflix |
| notes | String? | Import-Hinweise landen hier |
| isFavorite | Boolean | |
| tmdbId, posterUrl | Int?, String? | gesetzt nach dem TMDB-Abgleich |
| isEnded | Boolean? | TMDB-Status Ended/Canceled |
| lastWatchedAirDate, nextAirDate, infoUpdatedAt | Long? (epoch day) | Excel „letzte Ausstrahlung“, „nächste“, „aktualisiert“ |
| createdAt, updatedAt | Long (epoch ms) | `updatedAt` sortiert die Liste („zuletzt geschaut“) |

### `import_progress` (vorübergehend)
Excel-Position einer Serie, die noch nicht mit TMDB abgeglichen ist: lastSeason/lastEpisode/lastSpecial,
maxSeason/maxEpisode/maxSpecial, watched/total, updatedAt. Liefert Fortschritt und Anzeige, bis die
echten Folgen da sind. Beim ersten Abgleich werden damit die Folgen abgehakt; danach wird die Zeile gelöscht.

### `seasons`
| Spalte | Typ | Beschreibung |
|---|---|---|
| id | Long PK | |
| seriesId | FK → series (CASCADE) | |
| number | Int | eindeutig pro Serie; **0 = Specials** |
| title | String? | optional, z. B. „Specials“ |

### `episodes`
| Spalte | Typ | Beschreibung |
|---|---|---|
| id | Long PK | |
| seasonId | FK → seasons (CASCADE) | |
| number | Int | eindeutig pro Staffel |
| title | String? | später aus TMDB |
| airDate | Long? (epoch day) | für „neue Folge verfügbar“ |
| watchedAt | Long? (epoch ms) | `null` = nicht gesehen; der Zeitstempel ergibt die Watch-Historie |

### Abgeleitete Werte (nicht gespeichert)
- **Fortschritt** = gesehene / alle Folgen (SQL-Subqueries in `SeriesDao.observeOverview`)
- **Nächste Folge** = erste ungesehene nach (Staffel, Folge)
- **Auto-Status** (`Progress.autoStatus`, abhängig vom Anlass der Änderung):
  - Folge abgehakt → „Schaue ich“ (außer bei „Abgebrochen“)
  - alles gesehen → „Fertig“, wenn die Serie beendet ist, sonst „Aufgeholt“
  - neue Folgen beim Abgleich → „Aufgeholt“/„Fertig“ wird zu „Pausiert“
  - Haken entfernt → „Fertig“/„Aufgeholt“ wird zu „Schaue ich“
  - Manuell auf „Fertig“ setzen markiert alle Folgen als gesehen.

### Excel → App
| Excel | App |
|---|---|
| fertig = 1 | COMPLETED |
| Status 1 | WATCHING |
| Status 2, gesehen = von | CAUGHT_UP |
| Status 2, sonst | PAUSED |
| Status 0 | PLAN_TO_WATCH |
| Wer M / J / B | ME / JOHANNA / BOTH |
| Staffel/Episode/Special, max…, gesehen/von | `import_progress` bis zum TMDB-Abgleich |

### TMDB-Abgleich
- Suche mit `language=de-DE`, damit deutsche Titel gefunden werden.
- Ein Treffer wird automatisch übernommen, wenn der normierte Titel (`TitleMatcher`) passt **und** die Zahl
  der ausgestrahlten regulären Staffeln der Excel-Spalte maxStaffel entspricht. Sonst: „Bitte prüfen“.
- Es werden nur bereits ausgestrahlte Folgen angelegt (wie Excel „von“). Specials (Staffel 0) nur, wenn die
  Excel Specials führt, weil TMDB dort oft viele Extras listet.
- „Abweichungen zur Excel“ zeigt Serien, bei denen gesehen/von nicht mit TMDB übereinstimmt.

### Warum Folgen einzeln speichern statt nur „S2E5“?
Mit einzelnen Folgen kann man Lücken abbilden (eine Folge übersprungen), eine Historie und Statistiken führen und später TMDB-Daten pro Folge (Titel, Ausstrahlungsdatum) ergänzen. Der Preis: ein paar Tausend Zeilen. Für SQLite ist das nichts.

### Geplante Erweiterungen
- `watch_events` (episodeId, watchedAt) für Rewatches, falls gewünscht.
- `tags` / `series_tags` für Genres oder eigene Listen.
- Schema-Änderungen über Room-Migrationen; die Schemata werden in `app/schemas/` exportiert.

## Mengengerüst
- ~20.000 Folgen in ~1.500 Staffeln bei 361 Serien (Ausreißer: Pokémon 1.368, One Piece 1.169).
- ~100 Byte pro Folge inklusive Index → **~2–3 MB Datenbank**. Die Liste liest nur SQL-Summen.
- Archiv-JSON ~2 MB, Excel-Export wenige KB.
- `episodes.watchedAtImported` kennzeichnet Abhak-Daten, die nur aus dem Excel-Datum „aktualisiert“ stammen.

## Export
- `data/export/ExcelExport.kt`: App-Stand → Excel-Zeilen nach der Original-Logik (Position, max, gesehen/von,
  ungesehen, nächste, Sortierung). Nicht abgeglichene Serien geben die Excel-Werte unverändert zurück.
- `data/export/XlsxWriter.kt`: eigener XLSX-Schreiber (ZIP + XML), keine Bibliothek.
- `data/export/BackupRepository.kt`: Archiv-JSON (formatVersion 1); das Wiederherstellen läuft in einer Transaktion.
- CI-Round-Trip: Seed → App → Excel → `tools/convert_excel.py` → `tools/check_roundtrip.py` muss identisch sein.

## Datenbank-Migrationen
- v1 → v2: destruktiv (nur Grundgerüst).
- v2 → v3: `episodes.watchedAtImported` (Mitternachts-Zeitstempel = importiert), `import_progress.unseen`.
- v3 → v4: `series.originalTitle`.
- v4 → v5: `episodes.rating`, `episodes.kind` (REGULAR/SPECIAL/HIDDEN; Staffel 0 → SPECIAL), `episodes.isCustom`,
  `episodes.runtime`, `series.tmdbMetaVersion`, `series.excelWatched/excelTotal`.
  Für schon abgeglichene Serien trägt `SeedImporter.backfillExcelCounts()` die Excel-Werte per Titel aus `serien.json` nach.

## Folgen-Typen & Specials
- `HIDDEN` zählt nirgends (Fortschritt, Status, Excel, Statistik). „Nächste Folge“ nimmt nur `REGULAR`.
- Abgleich: alle TMDB-Specials zählen (`SPECIAL`). Gesehen sind beim ersten Abgleich die ersten `lastSpecial`
  in chronologischer Reihenfolge. Sind nur Specials offen, bleibt der Excel-Status („fertig“/„aufgeholt“).
- Extras blendet der Nutzer per langem Drücken aus; Antippen einer ausgeblendeten Folge lässt sie wieder zählen
  und hakt sie ab (`SeriesRepository.countAndWatch`).
- Specials werden chronologisch nach Erstausstrahlung angezeigt (als Liste mit Titel), auch eigene.
- Eigene Specials: Staffel 0, `isCustom = 1`, Nummer ab 1000; werden beim Aktualisieren nie verändert.
- Excel-Export: „Special“ = laufende Nummer der gesehenen Specials nach Erstausstrahlung.

## Statistik
`data/stats/StatsCalculator.compute()` ist eine reine Funktion über alle Serien (unit-getestet). Sehzeit nutzt
die TMDB-Laufzeit je Folge, fehlende Werte den Median der Serie. Der Verlauf nutzt nur echte Abhak-Zeitpunkte.

## Excel-Startdaten
`tools/convert_excel.py` wandelt das Blatt „Serien“ in `app/src/main/assets/seed/serien.json` um
(eine Serie pro Zeile). `SeedImporter` lädt die Datei beim ersten Start. Mapping-Logik:
`domain/ExcelMapping.kt`. Hinweis: Das Repo ist öffentlich, die Liste damit auch.
