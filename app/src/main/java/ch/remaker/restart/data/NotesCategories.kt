package ch.remaker.restart.data

/**
 * The note categories: the ones the user created (kept even while no entry uses them) and the ones
 * entries carry. Names compare without case, so "Finans" and "finans" are one category. Pure logic,
 * pinned by NotesCategoriesTest; NotesStore keeps the saved list.
 */
object NotesCategories {

    private fun same(a: String, b: String) = a.trim().equals(b.trim(), ignoreCase = true)

    /** Every category, the saved ones and those in use, alphabetically, each once. */
    fun all(saved: List<String>, items: List<NoteItem>): List<String> =
        (saved + items.map { it.category })
            .map { it.trim() }.filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }

    /** How many entries carry [name]. */
    fun count(items: List<NoteItem>, name: String): Int = items.count { same(it.category, name) }

    /** The saved list with [name] added; unchanged when it is blank or already there. */
    fun add(saved: List<String>, name: String): List<String> {
        val clean = name.trim()
        if (clean.isEmpty() || saved.any { same(it, clean) }) return saved
        return saved + clean
    }

    /**
     * Can [old] be renamed to [new]? Not to a blank name, and not onto another category (that would
     * silently merge two lists); a new spelling of the same name ("finans" to "Finans") is fine.
     */
    fun canRename(all: List<String>, old: String, new: String): Boolean {
        val clean = new.trim()
        if (clean.isEmpty()) return false
        return all.none { same(it, clean) && !same(it, old) }
    }

    /** [old] renamed to [new] in the saved list and on every entry that carries it. */
    fun rename(saved: List<String>, items: List<NoteItem>, old: String, new: String): Pair<List<String>, List<NoteItem>> {
        val clean = new.trim()
        val list = saved.filterNot { same(it, old) } + clean
        val entries = items.map { if (same(it.category, old)) it.copy(category = clean) else it }
        return list to entries
    }

    /** [name] gone from the saved list; its entries stay, without a category. */
    fun remove(saved: List<String>, items: List<NoteItem>, name: String): Pair<List<String>, List<NoteItem>> {
        val list = saved.filterNot { same(it, name) }
        val entries = items.map { if (same(it.category, name)) it.copy(category = "") else it }
        return list to entries
    }
}
