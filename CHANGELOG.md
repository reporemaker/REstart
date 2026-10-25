# Changelog

REstart is a fork of mLauncher, which descends from Olauncher; the people behind that code,
the two changes sent back upstream and every difference at the fork point are in
[FOUNDATION.md](FOUNDATION.md). This file starts where that one ends.

## Unreleased

- Settings reorganised by surface: Features = behaviour (Search, Drawer, Weather, Private
  space); Look & Feel = appearance (Appearance, Clock area, Home list, Quick actions, Drawer);
  Advanced = Expert and General (language, settings text size, orientation, lock, haptics).
  Nothing was renamed or removed except the home-screen pages: the pager and its "Number of
  pages" / "Home pager" settings are gone, the home list holds up to 8 apps, and long swipes
  left/right no longer switch pages (their default action is now Disabled).
- Clock area: one settings page under Look & Feel holds everything about the block at the top
  of the home screen — the four styles with live previews, what is shown (clock, AM/PM, date,
  day of year, alarm, battery), the chosen style's own layout rows, colours and sizes, and the
  tap actions for the clock and the date. "No alarm is set." is now translated.
- Fonts: the clock keeps the user's font family in every style — weights are derived from it
  instead of swapping in the system light face, negative letter-spacing is applied only to the
  system font (so Doto's dots never collide), and the bottom sheets keep each label's weight.
- Swiss style across the app: page heads with a 2 dp rule, hairline rows instead of boxes,
  numbered settings home, square tiles and switches, caps section labels; popups square with a
  rule, every choice a one-column list; home apps and drawer rows ruled, index numbers on a
  left-aligned home list, the quick-action row under a rule with the RE* mark at its end.
- Clock display: four styles (Classic, Swiss, Rings, One line) chosen on their own settings
  page under Look & Feel, with live previews; the shown-information switches (clock, AM/PM,
  date, day of year, alarm, battery) moved there from Features; each style brings only its own
  layout rows (ring placement, size, what the ring shows, 12 h / 24 h; Swiss side column and
  weights; one-line rule and alarm line); colours and sizes follow in one group.
- About screen redesigned: the RE* mark (white tile in the light theme, dark tile in the dark
  theme, the same rule everywhere in the app), name, version, "SEU // RE*maker"; open-source
  repository and licence; the foundation line (Multi Launcher, which descends from Olauncher);
  weather data credit; contact address as plain text. A second page, "Contributors and
  licences", names everyone whose work is in the code up to the fork point and shows the full
  texts of GPL-3.0, OFL-1.1, CC BY 4.0 and Apache-2.0 from the APK's assets.
- Fonts: Montserrat and Doto stay, nine bundled fonts are removed (Bitter, Fira Code, Hack, Lato,
  Merriweather, Quicksand, Raleway, Roboto, Source Code Pro); the APK loses about 1.5 MB. System
  font and your own font file (Settings → Look & Feel → Font → Custom) remain. The two fonts now
  ship with their OFL-1.1 licence texts (`assets/fonts/`) and are recorded in `VENDORED.md`,
  closing the licence gap the first LifeLine audit found.

## 1.0.0 — 25 October 2026

- New name and identity: RE*start, package `ch.remaker.restart`, the RE* mark as app icon.
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
