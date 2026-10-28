package ch.remaker.restart.data.backup

import ch.remaker.restart.data.NoteItem
import ch.remaker.restart.data.NotesCategories

/**
 * How the notes of a backup meet the notes on the phone.
 *
 * Merge: an entry only in the backup is added, an entry only on the phone stays, an entry in both
 * keeps the version edited last. Ids deleted on either side (the deleted lists are joined) do not
 * come back. Replace: the backup's entries as they are.
 */
object NotesMerge {

    data class Result(val items: List<NoteItem>, val deleted: Set<Long>, val categories: List<String>)

    fun merge(
        phone: List<NoteItem>, phoneDeleted: Set<Long>, phoneCategories: List<String>,
        backup: List<NoteItem>, backupDeleted: Set<Long>, backupCategories: List<String>,
    ): Result {
        val deleted = phoneDeleted + backupDeleted
        val fromBackup = backup.associateBy { it.id }
        val kept = phone.filter { it.id !in deleted }.map { mine ->
            val theirs = fromBackup[mine.id]
            if (theirs != null && theirs.edited > mine.edited) theirs else mine
        }
        val known = phone.map { it.id }.toSet()
        val added = backup.filter { it.id !in known && it.id !in deleted }
        val categories = backupCategories.fold(phoneCategories) { names, name -> NotesCategories.add(names, name) }
        return Result(kept + added, deleted, categories)
    }

    fun replace(backup: List<NoteItem>, backupDeleted: Set<Long>, backupCategories: List<String>): Result =
        Result(backup.filter { it.id !in backupDeleted }, backupDeleted, backupCategories)
}
