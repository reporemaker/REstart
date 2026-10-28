package ch.remaker.restart.data.backup

import ch.remaker.restart.data.*

/** The parts a backup is made of; each can be left out when backing up and when restoring. */
enum class BackupSection { SETTINGS, HOME, DRAWER, SHORTCUTS, NOTES, WIDGETS, FONT }

/**
 * Which section each stored setting belongs to. Only keys named here go into a backup and only
 * these are read back from one: anything else in a file is ignored, and a new setting has to be
 * added here (a unit test reads PrefsKeys.kt and fails on a key that is in no list).
 */
object BackupKeys {

    /** State of this install, never written to a backup nor taken from one. */
    val DEVICE = setOf(
        APP_VERSION, FIRST_OPEN, FIRST_SETTINGS_OPEN, GESTURES_MERGED, ONBOARDING_COMPLETED,
        "WORK", "PRIVATE", "SYSTEM", "USER",
        PENDING_SHORTCUTS, PENDING_WIDGETS, RESTORE_SUMMARY,
        AUTO_BACKUP_FOLDER, AUTO_BACKUP_SECTIONS, AUTO_BACKUP_KEY, AUTO_BACKUP_SALT,
        AUTO_BACKUP_LAST, AUTO_BACKUP_HASH, AUTO_BACKUP_FAILED,
    )

    /** Old forms that are read once and converted (notes before 1.2.0); not written any more. */
    val LEGACY = setOf(NOTES_MESSAGES, NOTES_CATEGORY, NOTES_PRIORITY)

    /** Settings stored as Float; an old (untyped) backup file cannot tell 1.0 from 1. */
    val FLOAT_KEYS = setOf(SHORT_SWIPE_THRESHOLD, LONG_SWIPE_THRESHOLD, WEATHER_LATITUDE, WEATHER_LONGITUDE)

    /** Ids of the gesture slots; with the app slots 0..7 they make the APP_NAME_<id> style keys. */
    val GESTURE_SLOTS = setOf(
        SHORT_SWIPE_UP, SHORT_SWIPE_DOWN, SHORT_SWIPE_LEFT, SHORT_SWIPE_RIGHT,
        LONG_SWIPE_UP, LONG_SWIPE_DOWN, LONG_SWIPE_LEFT, LONG_SWIPE_RIGHT,
        CLICK_CLOCK, CLICK_USAGE, CLICK_FLOATING, CLICK_DATE, DOUBLE_TAP, LONG_PRESS,
        SHAPE_CIRCLE, SHAPE_SQUARE, SHAPE_TRIANGLE,
    )

    /** The four keys of an app slot: APP_NAME_<id>, APP_PACKAGE_<id>, APP_ACTIVITY_<id>, APP_USER_<id>. */
    val SLOT_FIELDS = setOf(APP_NAME, APP_PACKAGE, APP_ACTIVITY, APP_USER)

    private val HOME = setOf(
        HOME_APPS_NUM, HOME_LOCKED,
        SWIPE_UP_ACTION, SWIPE_DOWN_ACTION, SWIPE_LEFT_ACTION, SWIPE_RIGHT_ACTION,
        LONG_SWIPE_UP_ACTION, LONG_SWIPE_DOWN_ACTION, LONG_SWIPE_LEFT_ACTION, LONG_SWIPE_RIGHT_ACTION,
        CLICK_CLOCK_ACTION, CLICK_APP_USAGE_ACTION, CLICK_FLOATING_ACTION, CLICK_DATE_ACTION,
        DOUBLE_TAP_ACTION, LONG_PRESS_ACTION, SHAPE_CIRCLE_ACTION, SHAPE_SQUARE_ACTION, SHAPE_TRIANGLE_ACTION,
    )

    /** String sets of app (and shortcut) entries; their shortcut entries belong to [BackupSection.SHORTCUTS]. */
    val APP_SETS = setOf(HIDDEN_APPS, LOCKED_APPS, PINNED_APPS)

    private val DRAWER = APP_SETS + setOf(
        HIDDEN_CONTACTS, PINNED_CONTACTS, CATEGORY_ORDER, CUSTOM_CATEGORIES, OPEN_DRAWER_CATEGORY,
    )

    private val NOTES = setOf(NOTES_ITEMS, NOTES_CATEGORIES, NOTES_DELETED)

    private val SETTINGS = setOf(
        LOCK_ORIENTATION, LOCK_ORIENTATION_PORTRAIT, HAPTIC_FEEDBACK, FORCE_COLORED_WALLPAPER,
        AUTO_SHOW_KEYBOARD, AUTO_OPEN_APP, OPEN_APP_ON_ENTER, RECENT_APPS_DISPLAYED, ICON_RAINBOW_COLORS,
        RECENT_COUNTER, FILTER_STRENGTH, SHORT_SWIPE_THRESHOLD, LONG_SWIPE_THRESHOLD, ENABLE_FILTER_STRENGTH,
        SMART_SEARCH, DRAWER_CATEGORIES, DRAWER_VIEW, CLOCK_STYLE, RING_PLACEMENT, RING_SIZE, CLOCK_RULE,
        SWISS_SIDE_COLUMN, SWISS_WEIGHTS, RING_INSIDE, RING_SCALE, SEARCH_SCOPE, CATEGORY_SIZE_TEXT,
        CATEGORY_COLOR, CATEGORY_UPPERCASE, CATEGORY_SHOW_COUNT, SEARCH_SPACING, SEARCH_HEIGHT,
        HOME_ALIGNMENT, HOME_ALIGNMENT_BOTTOM, HOME_CLICK_AREA, SHOW_PRIVATE_SPACES, DRAWER_ALIGNMENT,
        CLOCK_ALIGNMENT, DATE_ALIGNMENT, ALARM_ALIGNMENT, SHOW_BACKGROUND, STATUS_BAR, NAVIGATION_BAR,
        SHOW_BATTERY, SHOW_BATTERY_ICON, SHOW_WEATHER, GPS_LOCATION, SHOW_AZSIDEBAR, SHOW_DATE,
        SHOW_DAY_OF_YEAR, SETTINGS_LOCKED, HIDE_SEARCH_VIEW, AUTO_EXPAND_NOTES, CLICK_EDIT_DELETE,
        SHOW_CLOCK, SHOW_CLOCK_FORMAT, NOTES_ON_HOME, SHOW_FLOATING, ICON_PACK_HOME, CUSTOM_ICON_PACK_HOME,
        ICON_PACK_APP_LIST, CUSTOM_ICON_PACK_APP_LIST, SEARCH_START, LAUNCHER_FONT, APP_USAGE_STATS,
        APP_OPACITY, APP_LANGUAGE, APP_THEME, TEMP_UNIT, APP_SIZE_TEXT, DATE_SIZE_TEXT, CLOCK_SIZE_TEXT,
        ALARM_SIZE_TEXT, BATTERY_SIZE_TEXT, TEXT_SIZE_SETTINGS, TEXT_PADDING_SIZE, BACKGROUND_COLOR,
        APP_COLOR, DATE_COLOR, ALARM_CLOCK_COLOR, CLOCK_COLOR, BATTERY_COLOR, SHORTCUT_ICONS_COLOR,
        NOTES_BACKGROUND_COLOR, BUBBLE_BACKGROUND_COLOR, BUBBLE_MESSAGE_COLOR, BUBBLE_TIMEDATE_COLOR,
        BUBBLE_CATEGORY_COLOR, INPUT_MESSAGE_COLOR, INPUT_MESSAGEHINT_COLOR, WEATHER_LATITUDE,
        WEATHER_LONGITUDE, WEATHER_LOCATION, EXPERT_OPTIONS,
        "HOME_BUTTON_FLAGS", "CONTEXT_MENU_FLAGS", "APPLIST_BUTTON_FLAGS",
    )

    private val SLOT_KEY = Regex("^(${SLOT_FIELDS.joinToString("|")})_(\\d+|${GESTURE_SLOTS.joinToString("|")})$")

    /** "<package>/shortcut:<id>_ALIAS" and "<package>/shortcut:<id>_TAG[_<userHash>]". */
    private val SHORTCUT_SETTING = Regex("^.+/${Regex.escape(SHORTCUT_PREFIX)}.+_(ALIAS|TAG(_-?\\d+)?)$")

    /** "<package>_ALIAS", "<package>_TAG" and "<package>_TAG_<userHash>". */
    private val APP_SETTING = Regex("^[^/]+_(ALIAS|TAG(_-?\\d+)?)$")

    /** The section [key] is backed up in, or null for a key that never goes into a backup. */
    fun sectionOf(key: String): BackupSection? = when {
        key in DEVICE || key in LEGACY -> null
        key in SETTINGS -> BackupSection.SETTINGS
        key in HOME || SLOT_KEY.matches(key) -> BackupSection.HOME
        key in DRAWER -> BackupSection.DRAWER
        key in NOTES -> BackupSection.NOTES
        SHORTCUT_SETTING.matches(key) -> BackupSection.SHORTCUTS
        APP_SETTING.matches(key) -> BackupSection.DRAWER
        else -> null
    }

    /** An entry of [APP_SETS] that stands for a shortcut ("pkg/shortcut:id" or "pkg|shortcut:id|user"). */
    fun isShortcutEntry(entry: String): Boolean = SHORTCUT_PREFIX in entry
}
