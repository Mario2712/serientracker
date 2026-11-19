package io.github.mario2712.serientracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleMatcherTest {

    @Test
    fun `normalize ignores case, punctuation, umlauts and leading articles`() {
        assertEquals("knight of seven kingdoms", TitleMatcher.normalize("A Knight of the Seven Kingdoms"))
        assertEquals("daredevil born again", TitleMatcher.normalize("Daredevil: Born Again"))
        assertEquals("kingdom suedkorea", TitleMatcher.normalize("Kingdom (Südkorea)"))
        assertEquals("7 vs wild", TitleMatcher.normalize("7 vs. Wild"))
        assertEquals("bojack horseman", TitleMatcher.normalize("BoJack Horseman"))
    }

    @Test
    fun `same title`() {
        assertTrue(TitleMatcher.sameTitle("Pokemon", "Pokémon"))
        assertTrue(TitleMatcher.sameTitle("Die Simpsons", "Simpsons"))
        assertFalse(TitleMatcher.sameTitle("Arrow", "The Flash"))
    }

    @Test
    fun `confident only when title and season count fit`() {
        assertTrue(TitleMatcher.isConfidentMatch("12 Monkeys", 4, listOf("12 Monkeys"), 4))
        assertFalse(TitleMatcher.isConfidentMatch("12 Monkeys", 4, listOf("12 Monkeys"), 1))
        assertFalse(TitleMatcher.isConfidentMatch("Damengambit", 1, listOf("The Queen's Gambit"), 1))
        assertTrue(TitleMatcher.isConfidentMatch("Damengambit", 1, listOf("Das Damengambit", "The Queen's Gambit"), 1))
        assertTrue(TitleMatcher.isConfidentMatch("24", null, listOf("24"), 9))
    }
}
