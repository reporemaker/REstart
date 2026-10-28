package ch.remaker.restart.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private fun content(sections: Set<BackupSection> = BackupSection.entries.toSet()) = BackupContent(
        versionCode = 1030001, versionName = "1.3.0 Build 1", created = 1_793_000_000_000L,
        sections = sections,
        prefs = mapOf(
            BackupSection.SETTINGS to mapOf("APP_THEME" to "Dark", "SHORT_SWIPE_THRESHOLD" to 1.0f, "APP_SIZE_TEXT" to 20, "SHOW_DATE" to true),
            BackupSection.DRAWER to mapOf("HIDDEN_APPS" to setOf("a|b|0", "c|d|0")),
            BackupSection.NOTES to mapOf("NOTES_ITEMS" to "[]"),
        ),
        shortcuts = listOf(ShortcutEntry("com.android.chrome", "abc", "Bank", 0)),
        widgets = listOf(WidgetEntry("com.google.android.deskclock/.Widget", "Clock", 0, 1, 6, 3, 0)),
        font = byteArrayOf(1, 2, 3),
    )

    @Test
    fun everythingComesBackWithItsType() {
        val back = BackupCodec.fromJson(BackupCodec.toJson(content()))!!
        assertEquals(BackupSection.entries.toSet(), back.sections)
        assertEquals(1.0f, back.prefsOf(BackupSection.SETTINGS)["SHORT_SWIPE_THRESHOLD"])
        assertEquals(20, back.prefsOf(BackupSection.SETTINGS)["APP_SIZE_TEXT"])
        assertEquals(true, back.prefsOf(BackupSection.SETTINGS)["SHOW_DATE"])
        assertEquals(setOf("a|b|0", "c|d|0"), back.prefsOf(BackupSection.DRAWER)["HIDDEN_APPS"])
        assertEquals(content().shortcuts, back.shortcuts)
        assertEquals(content().widgets, back.widgets)
        assertArrayEquals(byteArrayOf(1, 2, 3), back.font)
        assertEquals("1.3.0 Build 1", back.versionName)
        assertFalse(back.legacy)
    }

    @Test
    fun leftOutSectionsAreNotWritten() {
        val json = BackupCodec.toJson(content(setOf(BackupSection.SETTINGS)))
        assertFalse(json.contains("NOTES_ITEMS"))
        assertFalse(json.contains("chrome"))
        assertEquals(setOf(BackupSection.SETTINGS), BackupCodec.fromJson(json)!!.sections)
    }

    @Test
    fun keysThatDoNotBelongAreIgnored() {
        val json = """{"format":2,"sections":["SETTINGS"],"prefs":{"SETTINGS":{
            "APP_THEME":{"t":"s","v":"Light"},"APP_VERSION":{"t":"i","v":1},"HIDDEN_APPS":{"t":"set","v":["x"]},"EVIL":{"t":"s","v":"x"}}}}"""
        val back = BackupCodec.fromJson(json)!!
        assertEquals(mapOf("APP_THEME" to "Light"), back.prefsOf(BackupSection.SETTINGS))
    }

    @Test
    fun anOldFlatFileIsSortedIntoSections() {
        val old = """{"APP_VERSION":1100500,"APP_THEME":"Dark","SHORT_SWIPE_THRESHOLD":1.0,"APP_SIZE_TEXT":20.0,
            "APP_PACKAGE_0":"com.android.chrome","HIDDEN_APPS":["a|b|0"],"NOTES_ITEMS":"[]","SHOW_AZSIDEBAR":true,
            "com.android.chrome_TAG_0":"Web","FIRST_OPEN":false,"SHOW_ALARM":true}"""
        val back = BackupCodec.fromJson(old)!!
        assertTrue(back.legacy)
        assertEquals(1.0f, back.prefsOf(BackupSection.SETTINGS)["SHORT_SWIPE_THRESHOLD"])
        assertEquals(20, back.prefsOf(BackupSection.SETTINGS)["APP_SIZE_TEXT"])
        assertEquals(true, back.prefsOf(BackupSection.SETTINGS)["SHOW_AZSIDEBAR"])
        assertEquals("com.android.chrome", back.prefsOf(BackupSection.HOME)["APP_PACKAGE_0"])
        assertEquals("Web", back.prefsOf(BackupSection.DRAWER)["com.android.chrome_TAG_0"])
        assertEquals("[]", back.prefsOf(BackupSection.NOTES)["NOTES_ITEMS"])
        // The old version number and the state of the old install are not taken over
        val all = back.prefs.values.flatMap { it.keys }
        assertFalse("APP_VERSION" in all)
        assertFalse("FIRST_OPEN" in all)
        assertFalse("SHOW_ALARM" in all)
        assertTrue(back.needsGestureMerge)
        assertNull(back.legacyNotesMessages)
    }

    @Test
    fun oldNotesWithoutTheNewListAreKeptForConversion() {
        val back = BackupCodec.fromJson("""{"NOTES_MESSAGES":"[{\"text\":\"x\"}]","GESTURES_MERGED":true}""")!!
        assertEquals("""[{"text":"x"}]""", back.legacyNotesMessages)
        assertTrue(BackupSection.NOTES in back.sections)
        assertFalse(back.needsGestureMerge)
    }

    @Test
    fun notABackup() {
        assertNull(BackupCodec.fromJson("hello"))
        assertNull(BackupCodec.fromJson("{}"))
        assertNull(BackupCodec.fromJson("""{"format":9}"""))
    }
}
