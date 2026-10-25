# Changelog

REstart is a fork of mLauncher, which descends from Olauncher; the people behind that code,
the two changes sent back upstream and every difference at the fork point are in
[FOUNDATION.md](FOUNDATION.md). This file starts where that one ends.

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
