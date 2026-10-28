# Changelog

REstart is a fork of mLauncher, which descends from Olauncher; the people behind that code,
the two changes sent back upstream and every difference at the fork point are in
[FOUNDATION.md](FOUNDATION.md). This file starts where that one ends.

## 1.3.0 - 28 October 2026

A minor release: backups are rebuilt from the ground up, with a password, and the fixes
gathered for 1.2.1 come with them. Backups of 1.0 to 1.2 are still read.

### Backups
- **Every backup has a password.** Making a backup asks for a password (at least 8 characters,
  twice) and the file is encrypted with it (AES-256-GCM, key from PBKDF2-HMAC-SHA256, 600,000
  rounds); restoring asks for it again. A wrong password or a damaged file is refused without
  touching anything. The password is not kept anywhere: without it the file cannot be opened.
- **You choose what goes in, and what comes back.** Seven parts, each with a tick both ways:
  settings; home screen and gestures; drawer (hidden, locked and pinned apps, names,
  categories); shortcuts; notes and to-dos; widget layout; font. A restore changes only the
  parts ticked; before, it wiped every setting first.
- **Notes are merged.** An entry only in the backup is added, one written since stays, one in
  both keeps the later edit, and one deleted on either side does not come back. "Replace them
  with the notes in the backup" is still there.
- **Shortcuts and widgets wait instead of vanishing.** Shortcuts an app still offers are pinned
  again; the ones only their app can add again (a browser's "Add to home screen") wait in a
  list with their name, category, lock and place until they are added again, instead of being
  pruned at once. Widgets come back as boxes in their old place: one tap and Android's consent,
  and the widget sits there again.
- **Automatic backup to a folder of your choice.** Off until you turn it on; then an encrypted
  backup after every update and at most once a day when something changed, the last 5 kept.
  The folder stays when RE*start is removed, so a reinstall or a new phone can be restored from
  it. The password is not stored: the key from it is kept wrapped by the Android Keystore.
- **A fresh install offers the restore.** The first-run tips carry "Have a backup? Tap to
  restore it." and "Continue without a backup"; the tips stay until one of the two.
- **Copies before updates hold everything** (notes, widget layout and shortcuts too), and one is
  also taken before every restore, as a way back.
- **An old backup can no longer delete settings.** Restoring wrote the file's old version number
  back, and the clean-up for that version then removed two settings still in use (A-Z bar,
  swipe threshold). The version number is never taken from a backup now, and the clean-up never
  touches those keys.
- **No phone-to-phone transfer of launcher data.** It carried widget ids that do not exist on
  the new phone; a new phone gets its setup from a backup file.

### Added
- **Hidden apps: hiding is not locking.** A line under the page title says that a hidden app
  only leaves the drawer and the search, and where the lock is (long press, Lock; if Lock is
  not in the menu, Settings › Features › Context Menu). 21 languages.


### Changed
- **Up to five notes on the home screen** (three before). Where the home apps leave less room
  (large notes size, many apps) the block shows fewer instead of running under the app list; the
  counts always stay.
- **AM/PM switch off means a 24-hour clock.** On a phone set to 12 hours the switch used to drop
  only the marker ("3:28", morning or afternoon?); off now shows "15:28". With the switch on, or
  on a phone set to 24 hours, nothing changes.


### Fixed
- **Sheets on the light theme.** Every sheet (choices, category actions, rename, sliders) stood
  on a light grey left over from mLauncher instead of white, and its texts were drawn in Doto's
  thin cut, so they read as grey. Sheets are white on the light theme now, and their texts keep
  Doto's emboldened cut as every other screen does (the dark theme had the thin cut too).
- **Drawer category heads.** Each head had its own full-width line and the list's inset line
  under it: two lines. One inset line now, as between the apps; the count and the arrow are
  easier to read on both themes.
- **Home notes cut short.** In a wide line (One line, a column on its own) an entry with a day
  was cut ("Renew the passpo… · Overdue Oct 23"). A day that does not fit beside the text now
  moves under it; only a text too long for a line of its own ends in "…".
- **Settings summaries.** Gestures read "Swipes, taps, hardware buttons" (there are no hardware
  button settings, and the shapes were missing): now "Swipes, taps, shapes, buttons"; Notes
  reads "Home screen, categories". 21 languages.
- **Device summary** (the RE* mark in the settings): the values are in a plain monospaced face
  whatever the font, so model names and numbers stay readable in Doto, and the colours follow
  the theme (white titles on the light theme's sheet were hard to see). The values are now in
  the full text colour too, and the grey outline around every sheet is gone (only the top rule
  stays).
- **Grey, faint texts on both themes.** A dotted font loses most of its ink when it is also
  dimmed, so texts drawn at 60 to 80 per cent read as grey. In full colour now: the notes counts
  on the home screen, the small 01-04 in front of the home apps, the weekday in the ring clock,
  the section labels and arrows in the settings, the labels and dates on the notes page, the
  second button and the notes in sheets, the count and arrow on drawer category heads and the
  folder line under a search result. The A-Z bar in the drawer was a fixed grey with a white
  highlight, so the chosen letter vanished on the light theme; it follows the drawer's text
  colour. Placeholder texts are darker. Still dimmed on purpose: done entries, an option not
  chosen and empty fields.
- **Drawer menu and edit rows.** The long-press menu and the rename and tag rows stand on a
  see-through ground, and the app's own name and icon showed through them as a grey ghost; the
  row steps aside while one of them is open.
- **Keyboard after the drawer.** Leaving the drawer lets go of whichever field had the keyboard
  (the search, a rename or tag row), and the home screen closes any keyboard left open.
- **Home notes counts.** The count line breaks only between counts and drops the dot where it
  breaks ("Notes 4 · To-do 3 / Overdue 2", not "Notes 4 · To-do 3 · / Overdue 2").
- **Long home notes.** A note without a day ran past the screen edge, cut mid-letter; it ends in
  "…" now, like the others.
- **Notes page for screen readers.** Done and Important are check boxes with their state ("Important,
  ticked"), the to-do boxes in the list too, and Note / To-do say which one is chosen.


### Build
- **Reproducible release.** Without the signing key the release builds unsigned instead of
  stopping, so F-Droid and anyone else can rebuild it. Each published release is rebuilt on a
  clean Linux machine and compared with the signed APK (GitHub Actions); unit tests, translations
  and the no-em-dash rule run on every push. The key never leaves the release machine.

## 1.2.0 - 26 October 2026

### Notes and to-dos
- The notes page (the RE* mark opens it) is rebuilt in the Swiss style: a page head, entries as
  ruled rows in three sections (To-do, Notes, Done), the kind, the text and ADD at the bottom.
- An entry is a note or a to-do; a to-do has a square box, done ones drop to Done and can be
  cleared at once. Tapping an entry opens it full page: text, kind, done, important, category,
  date, written; it saves itself on the way back (arrow, back key or leaving the app).
- Categories are the user's own: pick one or type a new one in an entry, or create, rename and
  remove them under Settings → Notes (a removed category leaves its entries without one). An
  optional date per entry; an open to-do past its date shows OVERDUE.
- The sections are ordered by date first, and on the same day the important entries first:
  to-dos from the longest overdue to the furthest, undated last; notes dated today or later
  first, then the others by the day they show, newest first.
- Fixed on the way: priority and category were stored as translated words, so sorting and the
  priority colour only worked in English, and entries were sorted by their date text (weekday
  names) instead of time. Entries now keep codes and times; old notes are moved over once
  (the highest priority becomes important, "None" drops the category).
- The old notes settings (bubble colours, auto-expand) are gone; the page follows the theme.

### Notes on the home screen (instead of the alarm)
- The next-alarm line under the clock is gone; the notes block takes its place and its look in
  all four clock styles: a count line (notes, open to-dos, overdue; a zero is not written) and
  up to three entries marked important: overdue to-dos first, then today, then coming, then
  undated. Nothing to show hides the block. One line puts it under the rule.
- Tapping the count line opens the notes page; tapping an entry opens that entry, and closing
  it goes back home. The block redraws itself at midnight and when the clock or time zone
  changes, and whenever the home screen comes back.
- In a narrow column the text gives way first; a day that does not fit moves under the text.
  Counts use the language's own digits (Arabic ٣).
- Settings → Notes is back: show on the home screen (also under Clock area → Shown information),
  alignment, colour and size (the alarm's old values carry over), and the categories. The alarm
  settings and the bedtime filter that belonged to them are removed.

### Clock area: a live preview
- The four style cards are now four names to choose from. Over them, at full size and scrolling
  with the page, a preview of the chosen style drawn by the home screen's own code: battery line,
  clock, date, ring, rule and notes as they will be, in the page colours. Every switch, alignment,
  colour and style shows at once; the size sliders follow the finger (the sheet no longer dims the
  page).
- No notes, or notes switched off: none in the preview either, as on the home screen.

### Gestures
- **One action per direction.** The short and the long swipe are gone: a swipe right does the same
  however far it goes (it used to fall between "short" and "long", where the long one did nothing,
  so opening the phone could take three or four tries). Up always opens the app drawer, down the
  notifications with the quick settings; left and right, double tap and long press can be set.
- **The whole path counts.** A swipe was judged by where it started and ended only, so a circle
  that stopped short could open the app of a swipe. A stroke that bends is never a swipe now.
- **Shapes in one stroke:** a circle, a four-sided figure (sides need not be equal: a rectangle,
  a trapezium, a slanted one) or a triangle drawn without lifting the finger, each with its own
  action (off until set). Tuned on 182 strokes drawn by hand: a circle may leave a quarter of its
  round undrawn, run past its start or be an egg; corners may be drawn round or sharp; a stroke
  begun at a corner may stop short of it; anything at least 1.4 cm across. Three measures keep the
  shapes apart (how much of the path runs straight, the corners, and how much of the shape the
  triangle through three corners covers); none of the 182 is taken for another shape.
- **Try a gesture:** a pad under Settings > Gestures shows what the home screen makes of a stroke
  (the shape and how close it was, the swipe, or why nothing) and what it would do.
- **Swipes from the edge:** with gesture navigation Android keeps the side edges for "back", which
  does nothing on the home screen; the home screen takes 200 dp of each edge back, in the middle.
- Settings > Gestures is rebuilt for this: always the same, swipes with one distance setting, taps,
  shapes (drawn, not named), buttons. Old settings carry over: a side whose short swipe did nothing
  takes the long swipe's action.

### Fixed
- **Digital Wellbeing would not open** from the screen time line (or a gesture set to it) on
  phones where the maker renamed its screen (Samsung does between One UI versions): it now tries
  the known screens, the app's own entry, then any of its screens that opens, before saying so,
  in the phone's language.
- **Long dashes in the texts.** The feedback and hidden-apps texts (21 languages), the licence
  lines and the docs used the em dash; they now use a colon, a semicolon or a hyphen. Turkish
  "Tarih'i göster", "Pil'i göster" are now "Tarihi göster", "Pili göster".
- **Arabic clock on the Swiss stack.** Some measure passes came out narrower than Arabic-Indic
  digits lay out, so "١٠" could break into two lines (three or four lines for the time); the clock
  now measures again at the real width of its widest line.
- **Margins changed after the first layout** (clock beside the column, ring) were not applied for
  the layout direction; the block sets its layout parameters anew each time.
- **Light theme blinking dark.** Pages cross-fade when they change (home, drawer, settings);
  the page background (colour + opacity) sat on each page, so mid-fade both were half
  transparent and the wallpaper showed through: a dark wallpaper made the light theme blink.
  The background now sits on the container under every page, set before the first frame.
- **Widget page menus in the Swiss style.** The long-press menu is the same titled sheet as
  every other menu; the widget picker lists apps with their icon and a thin + / -, widgets
  indented with their size in cells.

## 1.1.1 - 25 October 2026

A patch release: six faults found in daily use after 1.1.0, and the dependency round from the
third LifeLine audit.

### Fixed
- **Sheets and the keyboard.** Every bottom sheet now follows the keyboard: typing a new
  category name, renaming one or entering a colour code no longer happens under the keyboard.
  Long sheets scroll inside the screen with the title and the buttons in view, so choosing apps
  for a category reaches the end of the list. The keyboard opens by itself for name fields.
- **Interface language.** Choosing a language did nothing: the setting was still being written
  when the launcher restarted, and Android brought the old value back. Settings are now written
  before every restart (language, icon pack, restore from a backup, font), and the launcher
  comes back on its own. Kurdish (Kurmanji) is in the list; its translation was already there.
- **Light theme.** Switching the theme left a dark background under the light theme's black
  text. Colours still at the old theme's defaults now follow the new theme; a background or a
  text colour that would be unreadable on the new theme goes back to the theme's colour.
- **Readability.** Body text is pure black on light and pure white on dark, the dark theme's
  surfaces are pure black, the text shadow is gone, and Doto body text uses its emboldened cut,
  so the dots no longer wash out to grey.
- **Untranslated choices.** Alignment, ring placement, ring content and dial sheets showed the
  options' code names ("Left", "Beside"); every choice sheet now shows the translated name.
- **Clock and date alignment.** Date and alarm alignment were only offered for the Classic
  clock. They are back wherever the date stands under the clock (Swiss without the side column,
  Rings), and clock alignment wherever the numbers are on screen.
- Turkish capitals use the dotted İ ("DİL", not "DIL"); version lines keep "BUILD".
- The RE* mark comes in two versions: black "RE" on light backgrounds, white "RE" on dark ones.
- Advanced settings summary: "backup, reset, language, restart" in every language (expert
  options were removed in 1.1.0).

### Dependencies (third LifeLine run, 25 Oct 2026)
- Compose 1.11.4 → 1.12.1, kotlinx-coroutines 1.9.0 → 1.11.0, Kotlin 2.4.20 → 2.4.21, Gradle
  9.8.0 → 9.8.1. Rule: stable releases published at least 14 days earlier, no known advisory;
  lock file and sha256 verification metadata regenerated in the same change; debug and signed
  release builds and the unit tests pass. `palette-ktx` 1.1.0 (21 Oct) waits until 4 Nov.
- `gradle/gradle-daemon-jvm.properties` removed again (an IDE had written it back in 1.1.0)
  and ignored by git, so the build keeps using the installed JDK and never downloads one.
- `gradle/verification-metadata.xml` regenerated from scratch: 485 components instead of 617,
  without the entries of the replaced versions. An offline clean build passes with it.

## 1.1.0 - 25 October 2026

A design release: the whole app now speaks one visual language, the clock became a choice,
settings were reorganised by what they affect, and every language is complete again.

### Swiss style throughout
- Settings: page heads with a bold title and a 2 dp rule, hairline rows instead of boxed cards, a
  numbered settings home, square tiles and switches, section labels in spaced capitals.
- Popups: square sheets with a rule on top, every choice as a one-column list (no segmented
  buttons), outlined square buttons, square check boxes, caps text buttons.
- Home screen and drawer: ruled rows, an index in front of a left-aligned home list, the search
  field as a ruled line, category heads in capitals, the quick-action row under a rule with the
  RE* mark closing it on the right.
- Dialogs: category name input, multi-choice lists, confirmations, the settings-copy picker and
  the "exit launcher" list all use the same sheet.

### Clock area - four styles
- Classic, Swiss (hours over minutes), Rings (a thick hour ring and a thin minute ring, both
  flowing from 12 o'clock, with the day or the time inside) and One line.
- One settings page under Look & Feel holds everything about the block: the style with live
  previews, what is shown, the chosen style's own layout rows, colours and sizes, tap actions.

### Settings reorganised
- Features = behaviour (Search, Drawer, Weather, Private space); Look & Feel = appearance
  (Appearance, Clock area, Home list, Quick actions, Drawer); Advanced = Actions and General.
- Home-screen pages and the pager are gone; the home list holds up to 8 apps.
- Theme import/export removed - the full backup already carries every colour; the dead
  "Expert options" switch removed; Help & feedback is a page of its own (help, how to report a
  bug, a translation error, or a security problem).
- A back arrow and a page head on every inner page, including the favourites and hidden-apps
  screens.

### About
- The RE* mark, name, version and signature; number cards for Contributors (47 names, the
  lineage Olauncher → OlauncherCF → mLauncher → RE*start) and Licences (full texts of GPL-3.0,
  OFL-1.1, CC BY 4.0 and Apache-2.0 from the APK).

### Fonts
- Montserrat and Doto stay with their OFL-1.1 texts in the APK and a `VENDORED.md` record;
  nine bundled fonts removed (about 1.5 MB less).
- The user's font is applied everywhere - onboarding, dialogs, pickers, labels - and the clock
  keeps the font family in every style (weights derived from it, no system face swapped in, no
  negative tracking on dot-matrix or custom fonts).

### Translations
- All 21 languages complete; 1,099 inherited strings corrected in context (dictionary
  mistranslations, leftover "Multi Launcher" names, untranslated English, broken line breaks).
- German in Swiss spelling and informal address, Spanish informal, Portuguese European.
- Machine-translated strings are marked in each file; corrections come as issues (Translation
  template) or pull requests. `scripts/check-strings.py` checks completeness.

### Dependencies (after the LifeLine audit)
- Unused packages removed, coroutines declared, biometric on a stable release, Gradle
  dependency locking and sha256 verification, no JDK download at build time, Actions pinned to
  commit SHAs.

## 1.0.0 - 25 October 2026

- New name and identity: RE*start, package `ch.remaker.restart`, the RE* mark as app icon.
  It does not update a Panther Launcher installation: export the settings there (Settings →
  Backup/Restore) and import them here.
- Crash dialog no longer offers to send anything; it names the contact address as plain text.
- Repository moved to [reporemaker/REstart](https://github.com/reporemaker/REstart); issues and pull requests open.
- One icon style instead of four: the RE* star, tinted with each app's colour.

#### Dependency and toolchain cleanup, after the first [LifeLine](https://github.com/reporemaker/LifeLine) audit (24 Oct 2026)

LifeLine mapped every external dependency of the project (191 resolved packages, 2 CI
workflows, JDK and Gradle provisioning) and flagged what follows. The audit itself changes
nothing; these are the decisions taken from it. Public report: `LifeLine_Report/`.

- `kotlinx-coroutines-android` is now declared (it was used in 9 files but only arrived
  transitively; a dependency bump could have removed it).
- Removed four packages no code imports: `work-runtime-ktx` (pulled WorkManager and a second
  sqlite stack into the APK), `commons-text`, `constraintlayout-compose`,
  `lifecycle-extensions` (deprecated meta package; `lifecycle-process` is declared instead).
- `compose ui-tooling`, `androidx.test core-ktx` and `espresso-idling-resource` no longer ship in
  the release APK (debug / androidTest only).
- `biometric-ktx 1.4.0-alpha02` replaced by the stable `androidx.biometric:biometric 1.1.0`;
  the ktx artifact never had a stable release, and only `BiometricPrompt`/`BiometricManager`
  are used.
- The empty `libs/*.jar` include is gone; a jar dropped into that folder would have entered the
  APK unchecked.
- Dependency locking is on (`app/gradle.lockfile`) and every artifact is verified by sha256
  (`gradle/verification-metadata.xml`): a moved version or a changed jar fails the build.
- The JDK is no longer downloaded through api.foojay.io at build time (resolver plugin and
  `gradle-daemon-jvm.properties` removed); the build uses the installed JDK. An offline clean
  build passes.
- GitHub Actions pinned to commit SHAs; the nightly third-party cache-purge workflow is
  removed (no CI builds, nothing to purge, one less token-bearing action).
