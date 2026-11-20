package io.github.mario2712.serientracker.domain

/** How an episode counts. Stored in Room by its name. */
enum class EpisodeKind(val label: String) {
    /** Normal episode of a season; drives "next episode" and the Excel position. */
    REGULAR("Folge"),

    /** Counts as special (Excel column "Special"), wherever TMDB lists it. */
    SPECIAL("Special"),

    /** Does not count at all (e.g. a TMDB extra that is no special for Mario). */
    HIDDEN("Ausgeblendet"),
}
