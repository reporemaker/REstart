package ch.remaker.restart.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesCategoriesTest {

    private var nextId = 1L
    private fun item(category: String) = NoteItem(id = nextId++, text = "t", created = 0, edited = 0, category = category)

    @Test
    fun allJoinsSavedAndUsedOnceEachAlphabetically() {
        val items = listOf(item("Work"), item(" finans "), item(""), item("work"))
        assertEquals(listOf("Ev", "finans", "Work"), NotesCategories.all(listOf("Ev"), items))
    }

    @Test
    fun savedSpellingWins() {
        assertEquals(listOf("Ev", "Finans", "Work"), NotesCategories.all(listOf("Ev", "Finans"), listOf(item("finans"), item("Work"))))
    }

    @Test
    fun aSavedCategoryStaysWithoutEntries() {
        assertEquals(listOf("Boş"), NotesCategories.all(listOf("Boş"), emptyList()))
    }

    @Test
    fun addIgnoresBlanksAndDuplicates() {
        assertEquals(listOf("Ev"), NotesCategories.add(listOf("Ev"), "  "))
        assertEquals(listOf("Ev"), NotesCategories.add(listOf("Ev"), "ev"))
        assertEquals(listOf("Ev", "İş"), NotesCategories.add(listOf("Ev"), " İş "))
    }

    @Test
    fun renameMovesEveryEntry() {
        val items = listOf(item("Ev"), item("ev"), item("İş"))
        val (saved, entries) = NotesCategories.rename(listOf("Ev"), items, "Ev", "Yuva")
        assertEquals(listOf("Yuva"), saved)
        assertEquals(listOf("Yuva", "Yuva", "İş"), entries.map { it.category })
    }

    @Test
    fun renameRefusesAnotherCategoryButAllowsANewSpelling() {
        val all = listOf("Ev", "İş")
        assertFalse(NotesCategories.canRename(all, "Ev", "iş"))
        assertFalse(NotesCategories.canRename(all, "Ev", " "))
        assertTrue(NotesCategories.canRename(all, "Ev", "EV"))
        assertTrue(NotesCategories.canRename(all, "Ev", "Yuva"))
    }

    @Test
    fun removeKeepsTheEntriesWithoutCategory() {
        val items = listOf(item("Ev"), item("İş"))
        val (saved, entries) = NotesCategories.remove(listOf("Ev", "İş"), items, "ev")
        assertEquals(listOf("İş"), saved)
        assertEquals(listOf("", "İş"), entries.map { it.category })
        assertEquals(2, entries.size)
    }

    @Test
    fun countIgnoresCase() {
        assertEquals(2, NotesCategories.count(listOf(item("Ev"), item("ev "), item("İş")), "EV"))
    }
}
