package ch.remaker.restart.data.backup

import ch.remaker.restart.data.NoteItem
import org.junit.Assert.assertEquals
import org.junit.Test

class NotesMergeTest {

    private fun note(id: Long, text: String, edited: Long = id) = NoteItem(id = id, text = text, created = id, edited = edited)

    private fun merge(phone: List<NoteItem>, backup: List<NoteItem>, phoneDeleted: Set<Long> = emptySet(), backupDeleted: Set<Long> = emptySet()) =
        NotesMerge.merge(phone, phoneDeleted, emptyList(), backup, backupDeleted, emptyList())

    @Test
    fun anEntryOnlyInTheBackupIsAdded() {
        val result = merge(listOf(note(1, "a")), listOf(note(1, "a"), note(2, "b")))
        assertEquals(listOf("a", "b"), result.items.map { it.text })
    }

    @Test
    fun anEntryWrittenAfterTheBackupStays() {
        val result = merge(listOf(note(1, "a"), note(3, "new")), listOf(note(1, "a")))
        assertEquals(listOf("a", "new"), result.items.map { it.text })
    }

    @Test
    fun theLaterEditWins() {
        assertEquals("phone", merge(listOf(note(1, "phone", edited = 20)), listOf(note(1, "backup", edited = 10))).items.single().text)
        assertEquals("backup", merge(listOf(note(1, "phone", edited = 10)), listOf(note(1, "backup", edited = 20))).items.single().text)
    }

    @Test
    fun anEntryDeletedOnThePhoneDoesNotComeBack() {
        val result = merge(emptyList(), listOf(note(1, "a"), note(2, "b")), phoneDeleted = setOf(1))
        assertEquals(listOf("b"), result.items.map { it.text })
        assertEquals(setOf(1L), result.deleted)
    }

    @Test
    fun anEntryDeletedBeforeTheBackupGoesFromThePhoneToo() {
        val result = merge(listOf(note(1, "a"), note(2, "b")), listOf(note(2, "b")), backupDeleted = setOf(1))
        assertEquals(listOf("b"), result.items.map { it.text })
    }

    @Test
    fun categoriesAreJoinedOnce() {
        val result = NotesMerge.merge(emptyList(), emptySet(), listOf("Ev", "İş"), emptyList(), emptySet(), listOf("iş", "Banka"))
        assertEquals(listOf("Ev", "İş", "Banka"), result.categories)
    }

    @Test
    fun replaceTakesTheBackupAsItIs() {
        val result = NotesMerge.replace(listOf(note(1, "a"), note(2, "b")), setOf(2), listOf("Ev"))
        assertEquals(listOf("a"), result.items.map { it.text })
        assertEquals(listOf("Ev"), result.categories)
    }
}
