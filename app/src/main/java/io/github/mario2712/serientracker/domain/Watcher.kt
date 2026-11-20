package io.github.mario2712.serientracker.domain

/** Who watches a series ("Wer" in the Excel list). */
enum class Watcher(val label: String, val excelCode: String) {
    ME("Ich", "M"),
    JOHANNA("Johanna", "J"),
    BOTH("Beide", "B"),
    ;

    companion object {
        fun fromExcel(code: String?): Watcher? =
            entries.firstOrNull { it.excelCode.equals(code?.trim(), ignoreCase = true) }
    }
}

/** List filter for [Watcher]. "Meine" includes the series watched together. */
enum class WatcherFilter(val label: String, private val matches: Set<Watcher>) {
    MINE("Meine", setOf(Watcher.ME, Watcher.BOTH)),
    JOHANNA("Johannas", setOf(Watcher.JOHANNA, Watcher.BOTH)),
    TOGETHER("Gemeinsam", setOf(Watcher.BOTH)),
    ;

    fun accepts(watcher: Watcher?): Boolean = watcher in matches
}
