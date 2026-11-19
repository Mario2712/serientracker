package io.github.mario2712.serientracker.domain

/** Where a series stands for the user. Stored in Room by its name. */
enum class WatchStatus(val label: String) {
    WATCHING("Schaue ich"),
    PLAN_TO_WATCH("Geplant"),
    PAUSED("Pausiert"),
    COMPLETED("Fertig"),
    DROPPED("Abgebrochen"),
}
