package ch.remaker.restart.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BackupKeysTest {

    @Test
    fun everyKeyInPrefsKeysHasAPlace() {
        // A new setting that is in no list would silently stay out of every backup
        val source = File("src/main/java/ch/remaker/restart/data/PrefsKeys.kt").readText()
        val values = Regex("""const val \w+ = "([^"]+)"""").findAll(source).map { it.groupValues[1] }
            .filterNot { it.startsWith("ch.remaker.") }
            .toList()
        assertTrue(values.size > 100)
        val unplaced = values.filter { key ->
            BackupKeys.sectionOf(key) == null && key !in BackupKeys.DEVICE && key !in BackupKeys.LEGACY &&
                key !in BackupKeys.SLOT_FIELDS && key !in BackupKeys.GESTURE_SLOTS
        }
        assertEquals(emptyList<String>(), unplaced)
    }

    @Test
    fun stateOfThisInstallNeverGoesIntoABackup() {
        listOf("APP_VERSION", "FIRST_OPEN", "GESTURES_MERGED", "WORK", "PENDING_SHORTCUTS", "AUTO_BACKUP_KEY").forEach {
            assertNull(it, BackupKeys.sectionOf(it))
        }
    }

    @Test
    fun keysFallIntoTheirSections() {
        assertEquals(BackupSection.SETTINGS, BackupKeys.sectionOf("APP_THEME"))
        assertEquals(BackupSection.SETTINGS, BackupKeys.sectionOf("CONTEXT_MENU_FLAGS"))
        assertEquals(BackupSection.HOME, BackupKeys.sectionOf("APP_PACKAGE_3"))
        assertEquals(BackupSection.HOME, BackupKeys.sectionOf("APP_ACTIVITY_SHAPE_CIRCLE"))
        assertEquals(BackupSection.HOME, BackupKeys.sectionOf("SWIPE_LEFT_ACTION"))
        assertEquals(BackupSection.DRAWER, BackupKeys.sectionOf("HIDDEN_APPS"))
        assertEquals(BackupSection.DRAWER, BackupKeys.sectionOf("com.android.chrome_TAG_0"))
        assertEquals(BackupSection.DRAWER, BackupKeys.sectionOf("org.mozilla.firefox_ALIAS"))
        assertEquals(BackupSection.SHORTCUTS, BackupKeys.sectionOf("com.android.chrome/shortcut:abc_ALIAS"))
        assertEquals(BackupSection.SHORTCUTS, BackupKeys.sectionOf("com.android.chrome/shortcut:abc_TAG_-12"))
        assertEquals(BackupSection.NOTES, BackupKeys.sectionOf("NOTES_ITEMS"))
        assertEquals(BackupSection.NOTES, BackupKeys.sectionOf("NOTES_DELETED"))
    }

    @Test
    fun unknownAndOldKeysAreLeftOut() {
        assertNull(BackupKeys.sectionOf("SHOW_ALARM"))
        assertNull(BackupKeys.sectionOf("NOTES_MESSAGES"))
        assertNull(BackupKeys.sectionOf("APP_NAME_99X"))
        assertNull(BackupKeys.sectionOf("anything"))
    }
}
