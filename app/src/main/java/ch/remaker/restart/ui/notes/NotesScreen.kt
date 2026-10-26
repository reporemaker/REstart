package ch.remaker.restart.ui.notes

import android.app.DatePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ch.remaker.common.getLocalizedString
import ch.remaker.restart.R
import ch.remaker.restart.data.NoteItem
import ch.remaker.restart.data.NotesStore
import ch.remaker.restart.helper.FontManager
import ch.remaker.restart.style.SettingsTheme
import ch.remaker.restart.ui.components.DialogManager
import ch.remaker.restart.ui.compose.SettingsComposable.PageHeader
import java.util.Calendar
import java.util.Locale

/**
 * The notes page state: the entries, the one being edited, and every change written at once.
 * Kept outside the composition so the fragment's back handler can close the editor.
 */
class NotesState(private val store: NotesStore) {
    val items = mutableStateListOf<NoteItem>().apply { addAll(store.load()) }
    var editingId by mutableStateOf<Long?>(null)

    private fun persist() = store.save(items.toList())

    fun categories(): List<String> = store.categories(items)

    fun add(text: String, todo: Boolean) {
        val now = System.currentTimeMillis()
        items.add(NoteItem(id = now, text = text.trim(), created = now, edited = now, todo = todo))
        persist()
    }

    fun update(item: NoteItem) {
        val i = items.indexOfFirst { it.id == item.id }
        if (i >= 0) {
            items[i] = item
            persist()
        }
    }

    fun toggleDone(item: NoteItem) = update(item.copy(done = !item.done, edited = System.currentTimeMillis()))

    fun delete(item: NoteItem) {
        items.removeAll { it.id == item.id }
        if (editingId == item.id) {
            pendingSave = null
            editingId = null
        }
        persist()
    }

    fun clearDone() {
        items.removeAll { it.todo && it.done }
        persist()
    }

    /** The open editor's save; it runs before the editor closes and when the page goes away. */
    var pendingSave: (() -> Unit)? = null

    fun flush() {
        pendingSave?.invoke()
    }

    fun closeEditor() {
        flush()
        pendingSave = null
        editingId = null
    }
}

/** Local midnight of the day [millis] falls on. */
internal fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun dayLabel(millis: Long): String =
    DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEdMMM"), millis).toString()

private fun fullLabel(millis: Long, is24: Boolean): String =
    DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), if (is24) "EEEEdMMMMHHmm" else "EEEEdMMMMhmma"), millis).toString()

private fun caps(s: String) = s.uppercase(Locale.getDefault())

/** The page colours: the theme's pure black or white, the text in full contrast, dimmed and hairline tints. */
private class Ink(val text: Color, val bg: Color) {
    val muted = text.copy(alpha = 0.72f)
    val line = text.copy(alpha = 0.14f)
}

@Composable
private fun font(): FontFamily {
    val context = LocalContext.current
    val typeface = remember { FontManager.getTypeface(context) }
    return remember(typeface) { typeface?.let { FontFamily(it) } ?: FontFamily.Default }
}

@Composable
fun NotesScreen(state: NotesState, isDark: Boolean, dialogs: DialogManager, onBack: () -> Unit, onShare: (String) -> Unit) {
    val ink = Ink(SettingsTheme.typography.title.color, if (isDark) Color.Black else Color.White)
    val editing = state.editingId?.let { id -> state.items.firstOrNull { it.id == id } }
    Box(modifier = Modifier.fillMaxSize().background(ink.bg)) {
        if (editing != null) {
            NoteEditor(state, editing, ink, dialogs, onShare)
        } else {
            NotesList(state, ink, dialogs, onBack)
        }
    }
}

@Composable
private fun Caps(text: String, ink: Color, modifier: Modifier = Modifier, bold: Boolean = false, size: Float = 11.5f) {
    androidx.compose.foundation.text.BasicText(
        text = caps(text),
        modifier = modifier,
        style = TextStyle(fontFamily = font(), fontSize = size.sp, letterSpacing = 0.14.em, color = ink, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    )
}

@Composable
private fun Body(text: String, ink: Color, modifier: Modifier = Modifier, size: Float = 17f, bold: Boolean = false, struck: Boolean = false, lines: Int = Int.MAX_VALUE) {
    androidx.compose.foundation.text.BasicText(
        text = text,
        modifier = modifier,
        maxLines = lines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            fontFamily = font(), fontSize = size.sp, lineHeight = (size * 1.32f).sp, color = ink,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textDecoration = if (struck) TextDecoration.LineThrough else TextDecoration.None
        )
    )
}

/** Square check box, 1.5 dp outline, filled with the text colour when ticked; 44 dp to touch. */
@Composable
private fun CheckSquare(checked: Boolean, ink: Ink, onToggle: () -> Unit) {
    Box(modifier = Modifier.size(44.dp).clickable(onClick = onToggle), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier.size(18.dp).border(1.5.dp, ink.text)
                .background(if (checked) ink.text else Color.Transparent)
        )
    }
}

@Composable
private fun Rule(color: Color, thick: Boolean = false) =
    Box(modifier = Modifier.fillMaxWidth().height(if (thick) 2.dp else 1.dp).background(color))

/** The meta line under an entry: category, IMPORTANT, and the day (overdue for an open to-do past it). */
private fun metaOf(item: NoteItem, today: Long): String {
    val parts = mutableListOf<String>()
    if (item.category.isNotBlank()) parts += item.category
    if (item.important) parts += getLocalizedString(R.string.notes_important)
    when {
        item.due != null && item.todo && !item.done && item.due < today ->
            parts += getLocalizedString(R.string.notes_overdue) + " · " + dayLabel(item.due)
        item.due != null -> parts += dayLabel(item.due)
        !item.todo -> parts += dayLabel(item.created)
    }
    return caps(parts.joinToString(" · "))
}

@Composable
private fun NotesList(state: NotesState, ink: Ink, dialogs: DialogManager, onBack: () -> Unit) {
    val today = startOfDay(System.currentTimeMillis())
    val open = state.items.filter { it.todo && !it.done }
        .sortedWith(compareBy<NoteItem>({ it.due ?: Long.MAX_VALUE }, { !it.important }, { -it.created }))
    val notes = state.items.filter { !it.todo }.sortedWith(compareBy<NoteItem>({ !it.important }, { -it.created }))
    val done = state.items.filter { it.todo && it.done }.sortedByDescending { it.edited }

    var input by remember { mutableStateOf("") }
    var newIsTodo by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        PageHeader(iconRes = R.drawable.ic_back, title = getLocalizedString(R.string.notes_settings_title), onClick = onBack, titleFontSize = 22.sp, fontColor = ink.text)

        if (state.items.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                Body(getLocalizedString(R.string.notes_list_empty_hint), ink.muted)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp)) {
                fun section(label: String, entries: List<NoteItem>) {
                    if (entries.isEmpty()) return
                    item { Caps(label, ink.muted, Modifier.padding(top = 22.dp, bottom = 4.dp)) }
                    entries.forEach { entry ->
                        item(key = entry.id) {
                            Column(modifier = Modifier.fillMaxWidth().clickable { state.editingId = entry.id }) {
                                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 6.dp)) {
                                    if (entry.todo) CheckSquare(entry.done, ink) { state.toggleDone(entry) }
                                    Column(modifier = Modifier.weight(1f).padding(top = 10.dp, bottom = 6.dp)) {
                                        Body(entry.text, if (entry.done) ink.muted else ink.text, bold = entry.important && !entry.done, struck = entry.done, lines = 2)
                                        val meta = metaOf(entry, today)
                                        if (meta.isNotEmpty()) Caps(meta, ink.muted, Modifier.padding(top = 5.dp))
                                    }
                                }
                                Rule(ink.line)
                            }
                        }
                    }
                }
                section(getLocalizedString(R.string.notes_kind_todo), open)
                section(getLocalizedString(R.string.notes_settings_title), notes)
                section(getLocalizedString(R.string.notes_section_done), done)
                if (done.isNotEmpty()) item {
                    Caps(getLocalizedString(R.string.notes_clear_done), ink.text, bold = true, modifier = Modifier
                        .padding(vertical = 4.dp)
                        .clickable {
                            dialogs.confirm(
                                getLocalizedString(R.string.notes_clear_done),
                                getLocalizedString(R.string.notes_clear_done_confirm, done.size),
                                getLocalizedString(R.string.delete)
                            ) { state.clearDone() }
                        }
                        .padding(vertical = 14.dp))
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }

        // New entry: the kind, the text on a ruled line, ADD
        Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 12.dp)) {
            Rule(ink.text, thick = true)
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                Caps(getLocalizedString(R.string.notes_kind_note), if (!newIsTodo) ink.text else ink.muted, bold = !newIsTodo,
                    modifier = Modifier.clickable { newIsTodo = false }.padding(vertical = 14.dp))
                Caps(getLocalizedString(R.string.notes_kind_todo), if (newIsTodo) ink.text else ink.muted, bold = newIsTodo,
                    modifier = Modifier.clickable { newIsTodo = true }.padding(vertical = 14.dp))
            }
            Row(verticalAlignment = Alignment.Bottom) {
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    maxLines = 4,
                    cursorBrush = SolidColor(ink.text),
                    textStyle = TextStyle(fontFamily = font(), fontSize = 18.sp, color = ink.text),
                    modifier = Modifier.weight(1f),
                    decorationBox = { field ->
                        Column {
                            Box(modifier = Modifier.padding(vertical = 10.dp)) {
                                if (input.isEmpty()) Body(getLocalizedString(R.string.message_hint), ink.muted, size = 18f)
                                field()
                            }
                            Rule(ink.text, thick = true)
                        }
                    }
                )
                Spacer(Modifier.width(16.dp))
                Caps(getLocalizedString(R.string.notes_add), ink.text, bold = true, size = 13f, modifier = Modifier
                    .clickable {
                        if (input.isNotBlank()) {
                            state.add(input, newIsTodo)
                            input = ""
                        }
                    }
                    .padding(vertical = 14.dp))
            }
        }
    }
}

/** A ruled settings-style row: label in spaced capitals, the value, an optional end mark. */
@Composable
private fun FieldRow(label: String, ink: Ink, onClick: (() -> Unit)? = null, end: @Composable () -> Unit = {}, value: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }) {
        Row(modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            Caps(label, ink.muted, Modifier.width(118.dp))
            Box(modifier = Modifier.weight(1f)) { value() }
            end()
        }
        Rule(ink.line)
    }
}

@Composable
private fun NoteEditor(state: NotesState, original: NoteItem, ink: Ink, dialogs: DialogManager, onShare: (String) -> Unit) {
    val context = LocalContext.current
    var text by remember(original.id) { mutableStateOf(original.text) }
    var todo by remember(original.id) { mutableStateOf(original.todo) }
    var done by remember(original.id) { mutableStateOf(original.done) }
    var important by remember(original.id) { mutableStateOf(original.important) }
    var category by remember(original.id) { mutableStateOf(original.category) }
    var due by remember(original.id) { mutableStateOf(original.due) }

    // Every change is kept as it is made; an emptied text keeps the old one
    fun commit() {
        val changed = original.copy(
            text = text.trim().ifEmpty { original.text }, todo = todo, done = todo && done,
            important = important, category = category.trim(), due = due
        )
        if (changed != original) state.update(changed.copy(edited = System.currentTimeMillis()))
    }
    SideEffect { state.pendingSave = ::commit }

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        PageHeader(
            iconRes = R.drawable.ic_back,
            title = getLocalizedString(if (todo) R.string.notes_kind_todo else R.string.notes_kind_note),
            onClick = { state.closeEditor() },
            titleFontSize = 22.sp,
            fontColor = ink.text
        )
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                cursorBrush = SolidColor(ink.text),
                textStyle = TextStyle(fontFamily = font(), fontSize = 23.sp, lineHeight = 30.sp, color = ink.text, fontWeight = FontWeight.Medium),
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 24.dp)
            )
            Rule(ink.line)
            FieldRow(getLocalizedString(R.string.notes_kind), ink) {
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    Caps(getLocalizedString(R.string.notes_kind_note), if (!todo) ink.text else ink.muted, bold = !todo,
                        modifier = Modifier.clickable { todo = false; commit() }.padding(vertical = 14.dp))
                    Caps(getLocalizedString(R.string.notes_kind_todo), if (todo) ink.text else ink.muted, bold = todo,
                        modifier = Modifier.clickable { todo = true; commit() }.padding(vertical = 14.dp))
                }
            }
            if (todo) FieldRow(getLocalizedString(R.string.notes_section_done), ink, onClick = { done = !done; commit() },
                end = { CheckSquare(done, ink) { done = !done; commit() } }) {}
            FieldRow(getLocalizedString(R.string.notes_important), ink, onClick = { important = !important; commit() },
                end = { CheckSquare(important, ink) { important = !important; commit() } }) {}
            FieldRow(getLocalizedString(R.string.category), ink, onClick = {
                val existing = state.categories()
                val labels = listOf(getLocalizedString(R.string.notes_category_none)) + existing + ("+ " + getLocalizedString(R.string.categories_new))
                dialogs.chooser(getLocalizedString(R.string.category), labels) { i ->
                    when (i) {
                        0 -> { category = ""; commit() }
                        labels.lastIndex -> dialogs.showTextInputDialog(context, getLocalizedString(R.string.categories_new), "", getLocalizedString(R.string.categories_name_hint)) { name ->
                            if (name.isNotBlank()) { category = name.trim(); commit() }
                        }
                        else -> { category = existing[i - 1]; commit() }
                    }
                }
            }, end = { Body("→", ink.text) }) {
                Body(category.ifBlank { getLocalizedString(R.string.notes_category_none) }, if (category.isBlank()) ink.muted else ink.text)
            }
            FieldRow(getLocalizedString(R.string.notes_date), ink, onClick = {
                val c = Calendar.getInstance().apply { timeInMillis = due ?: System.currentTimeMillis() }
                DatePickerDialog(context, { _, y, m, d ->
                    due = Calendar.getInstance().apply { clear(); set(y, m, d) }.timeInMillis
                    commit()
                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
            }, end = {
                if (due != null) Caps(getLocalizedString(R.string.notes_date_remove), ink.text,
                    modifier = Modifier.clickable { due = null; commit() }.padding(vertical = 14.dp, horizontal = 2.dp))
                else Body("→", ink.text)
            }) {
                Body(due?.let { dayLabel(it) } ?: getLocalizedString(R.string.notes_date_none), if (due == null) ink.muted else ink.text)
            }
            FieldRow(getLocalizedString(R.string.notes_written), ink) {
                Body(fullLabel(original.created, DateFormat.is24HourFormat(context)), ink.muted, size = 15f)
            }
            Spacer(Modifier.height(24.dp))
        }
        Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 12.dp)) {
            Rule(ink.text, thick = true)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Caps(getLocalizedString(R.string.share_note), ink.text, size = 13f, modifier = Modifier
                    .clickable { commit(); onShare(text.trim()) }.padding(vertical = 18.dp))
                Caps(getLocalizedString(R.string.delete), ink.text, bold = true, size = 13f, modifier = Modifier
                    .clickable {
                        dialogs.confirm(
                            getLocalizedString(R.string.confirm_delete_title),
                            getLocalizedString(R.string.confirm_delete_message),
                            getLocalizedString(R.string.delete)
                        ) { state.delete(original) }
                    }
                    .padding(vertical = 18.dp))
            }
        }
    }
}
