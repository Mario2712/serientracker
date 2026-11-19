# Architektur

## Stack

| Bereich | Wahl | Warum |
|---|---|---|
| Sprache | Kotlin 2.0 | Standard für Android |
| UI | Jetpack Compose + Material 3 | Deklarativ, Material You (dynamische Farben am Pixel) |
| Persistenz | Room (SQLite) | Offline, typsicher, Flows für reaktive UI |
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
   Room DAOs   (TMDB API)  (Import/Export)
       │
    SQLite
```

- **`domain/`**: reine Kotlin-Logik ohne Android-Abhängigkeit (`WatchStatus`, `Progress`), voll unit-testbar.
- **`data/local/`**: Room-Entities, DAOs, `AppDatabase`.
- **`data/repository/`**: `SeriesRepository` kapselt Transaktionen und Regeln, etwa den automatischen Statuswechsel.
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
| status | Enum `WatchStatus` | WATCHING, PLAN_TO_WATCH, PAUSED, COMPLETED, DROPPED |
| rating | Int? | 1–10 |
| streamingService | String? | z. B. Netflix |
| notes | String? | |
| isFavorite | Boolean | |
| tmdbId, posterUrl | Int?, String? | vorbereitet für die TMDB-Anbindung |
| createdAt, updatedAt | Long (epoch ms) | `updatedAt` sortiert die Liste („zuletzt geschaut“) |

### `seasons`
| Spalte | Typ | Beschreibung |
|---|---|---|
| id | Long PK | |
| seriesId | FK → series (CASCADE) | |
| number | Int | eindeutig pro Serie |
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
- **Auto-Status** (`Progress.autoStatus`): erste Folge gesehen → „Schaue ich“; alle gesehen → „Fertig“; eine Folge wieder abgehakt → zurück auf „Schaue ich“. Manuell auf „Fertig“ setzen markiert alle Folgen als gesehen.

### Warum Folgen einzeln speichern statt nur „S2E5“?
Mit einzelnen Folgen kann man Lücken abbilden (eine Folge übersprungen), eine Historie und Statistiken führen und später TMDB-Daten pro Folge (Titel, Ausstrahlungsdatum) ergänzen. Der Preis: ein paar Tausend Zeilen. Für SQLite ist das nichts.

### Geplante Erweiterungen
- `watch_events` (episodeId, watchedAt) für Rewatches, falls gewünscht.
- `tags` / `series_tags` für Genres oder eigene Listen.
- Schema-Änderungen über Room-Migrationen; die Schemata werden in `app/schemas/` exportiert.

## Excel-Import (geplant)
Die `.xlsx` ist ein ZIP mit XML. Statt Apache POI (mehrere MB, Probleme auf Android) kommt ein kleiner eigener Parser zum Einsatz (`ZipInputStream` + `XmlPullParser`). Der Import läuft über den System-Dateiauswähler (Storage Access Framework), braucht also keine Berechtigungen, und ruft `SeriesRepository.save(...)` auf. Das Spalten-Mapping richtet sich nach der echten Datei.
