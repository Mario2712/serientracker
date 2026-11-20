package io.github.mario2712.serientracker.domain

import java.text.Normalizer

/** Decides whether a TMDB search result is clearly the series from the Excel list. */
object TitleMatcher {

    private val articles = setOf("the", "der", "die", "das", "a", "an")

    fun normalize(title: String): String {
        val lower = title.lowercase()
            .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
            .replace("&", " and ")
        val ascii = Normalizer.normalize(lower, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return ascii.split(Regex("[^a-z0-9]+"))
            .filter { it.isNotEmpty() }
            .filterIndexed { i, word -> !(i == 0 && word in articles) }
            .joinToString(" ")
    }

    fun sameTitle(a: String, b: String): Boolean = normalize(a) == normalize(b)

    /**
     * Auto-accept only if a title matches and the season count fits the Excel list.
     * [regularSeasons] excludes the specials season 0. If Excel has no season count, the title has to do.
     */
    fun isConfidentMatch(
        excelTitle: String,
        excelMaxSeason: Int?,
        candidateNames: List<String>,
        regularSeasons: Int?,
    ): Boolean {
        if (candidateNames.none { sameTitle(excelTitle, it) }) return false
        if (excelMaxSeason == null || excelMaxSeason <= 0 || regularSeasons == null) return true
        return regularSeasons == excelMaxSeason
    }
}
