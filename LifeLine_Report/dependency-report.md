# Dependency Report: REstart
Generated: 2026-10-25 (third run, `2026-10-25-2`) · Scope: full
Ecosystems: Maven (Gradle, Android) · GitHub Actions · vendored fonts · Previous report: 2026-10-25

**Summary.** Third run, after the 1.1.0 release. Two things changed since the previous run.
First, 1.1.0 removed nine of the eleven bundled fonts and recorded the two that stay
(Montserrat, Doto) in a new `VENDORED.md` ledger with their OFL-1.1 texts in the APK, which
closes the oldest open item of the baseline. Second, this run applied four updates under the
update rule used here (stable release, published at least 14 days before the run, no known
advisory, lock file and sha256 verification metadata regenerated in the same change, debug and
signed release builds and unit tests passing): Compose 1.11.4 → 1.12.1, kotlinx-coroutines
1.9.0 → 1.11.0, Kotlin 2.4.20 → 2.4.21, Gradle 9.8.0 → 9.8.1. `palette-ktx` 1.1.0 waits: it was
published on 2026-10-21. The release runtime classpath is still 155 packages, all under
permissive licences. Open from earlier runs: `com.google.android.material` still reports no
licence in the registry. New, minor: the ledger's source records do not match the font files
byte for byte (section 5).

## 1. Context Map

**Ecosystems found**

| Ecosystem | Manifest / lock | Notes |
|---|---|---|
| Maven via Gradle (Kotlin DSL) | `app/build.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`; lock: `app/gradle.lockfile`, `settings-gradle.lockfile` | dependency locking on for all configurations; checksums in `gradle/verification-metadata.xml` (617 components, sha256) |
| GitHub Actions | `.github/workflows/validate-gradle-wrapper.yml` | 2 actions, SHA-pinned |
| Gradle wrapper / toolchain | `gradle/wrapper/gradle-wrapper.properties` | Gradle 9.8.1 (sha256-pinned) |
| Vendored files | `VENDORED.md` | 2 fonts (new in 1.1.0) |

**Modules** (single Gradle module `:app`, Kotlin, package `ch.remaker`): unchanged since the
baseline. Entry points: `MainActivity` (HOME intent), `REstartApp`, `REstartInitProvider`, the
accessibility and notification services, four widget receivers.

**Module → external dependency map** (import counts from `scan-imports.sh`, whole app):
`androidx.compose.*` 133 (was 116; the 1.1.0 settings, clock and About screens) ·
`androidx.core` 68 · `kotlinx.coroutines` 27 · `androidx.lifecycle` 24 · `androidx.appcompat` 24
· `androidx.recyclerview` 16 · `com.google` (material) 13 (was 15) · `androidx.activity` 13 ·
`androidx.room` 10 · `com.squareup` (moshi) 9 · `androidx.fragment` 9 · `androidx.navigation` 6
· `androidx.biometric` 6 · `androidx.annotation` 6 · `androidx.viewpager2` 3 ·
`androidx.coordinatorlayout` 2 · `androidx.palette` 1 · `androidx.viewbinding` 1;
`org.xmlpull`, `org.json`, `android.*` are platform. No new external package is imported.

## 2. External Code Inventory

### Packages: direct runtime dependencies (27 on `prodReleaseRuntimeClasspath`, from the lock file)

| Package | Version | Direct? | Used? | License | Last release (deps.dev `latest`) | Risk signals | Criticality |
|---|---|---|---|---|---|---|---|
| androidx.compose.ui:ui | 1.12.1 (was 1.11.4) | direct | yes | Apache-2.0 | 1.13.0-beta02, 2026-10-21 | - | HIGH |
| androidx.compose.foundation:foundation | 1.12.1 (was 1.11.4) | direct | yes | Apache-2.0 | 1.13.0-beta02 | - | HIGH |
| androidx.compose.runtime:runtime-android | 1.12.1 (was 1.11.4) | direct | yes | Apache-2.0 | 1.13.0-beta02 | - | HIGH |
| androidx.compose.material:material | 1.12.1 (was 1.11.4) | direct | yes (2 files) | Apache-2.0 | 1.13.0-beta02 | - | MED |
| androidx.compose.animation:animation | 1.12.1 (was 1.11.4) | direct | yes (1 file) | Apache-2.0 | 1.13.0-beta02 | - | LOW |
| com.google.android.material:material | 1.14.0 | direct | yes (13 imports + themes) | Apache-2.0 per project; registry: none | 1.14.0, 2026-05-20 | ⚪ licence not confirmed by registry (unchanged) | HIGH |
| androidx.appcompat:appcompat | 1.8.0 | direct | yes (24) | Apache-2.0 | 1.8.0, 2026-08-12 | - | HIGH |
| androidx.core:core-ktx | 1.19.1 | direct | yes (68) | Apache-2.0 | 1.19.1, 2026-09-23 | - | HIGH |
| androidx.recyclerview:recyclerview | 1.4.0 | direct | yes (16) | Apache-2.0 | 1.4.0, 2025-01-15 | - | HIGH |
| androidx.lifecycle:lifecycle-viewmodel-ktx | 2.11.0 | direct | yes | Apache-2.0 | 2.12.0-alpha05 | - | HIGH |
| androidx.lifecycle:lifecycle-process | 2.11.0 | direct | yes | Apache-2.0 | 2.12.0-alpha05 | - | MED |
| androidx.room:room-runtime / room-ktx | 2.8.5 | direct | yes (10) | Apache-2.0 | 2.8.5, 2026-09-09 | - | HIGH |
| com.squareup.moshi:moshi / moshi-kotlin | 1.15.2 | direct | yes (4 files) | Apache-2.0 | 1.15.2, 2024-12-05 | - | MED |
| androidx.navigation:navigation-fragment-ktx / navigation-ui-ktx | 2.10.2 | direct | yes | Apache-2.0 | 2.10.2, 2026-09-23 | - | MED |
| androidx.biometric:biometric | 1.1.0 | direct | yes (5 files) | Apache-2.0 | 1.4.0-alpha07 (no stable newer than 1.1.0) | - | MED |
| androidx.constraintlayout:constraintlayout | 2.2.2 | direct | yes (6 layouts) | Apache-2.0 | 2.2.2, 2026-07-29 | - | MED |
| androidx.activity:activity / activity-ktx / activity-compose | 1.13.0 | direct | yes (13) | Apache-2.0 | 1.14.0-alpha03 | - | MED |
| androidx.viewpager2:viewpager2 | 1.1.0 | direct | yes (3 files) | Apache-2.0 | 1.1.0, 2024-05-23 | - | MED |
| androidx.palette:palette-ktx | 1.0.0 | direct | yes (1 file) | Apache-2.0 | 1.1.0, 2026-10-21 | ⚪ stable update, too recent for the 14-day rule | LOW |
| org.jetbrains.kotlinx:kotlinx-coroutines-android | 1.11.0 (was 1.9.0) | direct | yes (27 imports) | Apache-2.0 | 1.11.0, 2026-05-07 | - | HIGH |
| androidx.databinding:viewbinding | 9.4.1 | direct (AGP) | yes | Apache-2.0 | 9.5.0-rc01 | - | HIGH |
| org.jetbrains.kotlin:kotlin-stdlib | 2.4.21 (was 2.4.20) | direct (plugin) | yes | Apache-2.0 | 2.5.0-Beta2 | - | HIGH |

Transitive runtime packages: 128, all AndroidX / Kotlin / Google; licences not fetched for
transitive packages (see Limits). Direct test/debug dependencies (12): junit 4.13.2 (EPL-1.0),
espresso 3.7.0, androidx.test 1.7.0, fragment-testing 1.9.1, compose ui-test-junit4 /
ui-test-manifest / ui-tooling 1.12.1 (was 1.11.4), navigation-testing 2.10.2.

**Updates applied in this run** (rule above; dates from deps.dev and services.gradle.org)

| Item | Before → after | Published | Notes |
|---|---|---|---|
| Compose (ui, foundation, runtime, material, animation, tooling, ui-test) | 1.11.4 → 1.12.1 | 2026-09-09 | 30 release-runtime rows moved together |
| kotlinx-coroutines-android (+ core, core-jvm, bom) | 1.9.0 → 1.11.0 | 2026-05-07 | |
| Kotlin (plugin, compose compiler plugin, stdlib) | 2.4.20 → 2.4.21 | 2026-10-08 | supersedes the closed Dependabot PR #1 |
| Gradle wrapper | 9.8.0 → 9.8.1 | 2026-10-07 | sha256 from services.gradle.org; 9.8.2 (2026-10-19) too recent |
| androidx.transition (transitive) | 1.6.0 → 1.5.0 | - | resolved down: nothing requires 1.6.0 any more |

Checked and not applied: `palette-ktx` 1.1.0 (2026-10-21; eligible from 2026-11-04). Everything
else is on its newest stable release.

**Findings**

- ✅ Resolved since the baseline: the eleven fonts without licence texts or origin record.
  1.1.0 keeps two (Montserrat, Doto), ships their OFL-1.1 texts in the APK and records them in
  `VENDORED.md`; the other nine are gone from the repository.
- 🟡 Ledger provenance: see section 5.
- 🟡 Imported but only transitively declared (unchanged): `androidx.fragment`,
  `androidx.lifecycle:lifecycle-runtime-ktx`, `androidx.coordinatorlayout`. Locked, so not a
  drift risk; declaring them is tidiness.
- ⚪ `com.google.android.material:material`: deps.dev returns no licence; the project's own
  LICENSE is Apache-2.0. Unchanged, informational.

### Toolchain

| Tool / action | Version | Pinned how | Notes |
|---|---|---|---|
| Gradle wrapper | 9.8.1 (was 9.8.0) | sha256 in `gradle-wrapper.properties` | ✅ |
| JDK | 21 | - | this run: the local Android Studio JBR 21 |
| Dependency set | all configurations | `app/gradle.lockfile`, `settings-gradle.lockfile` | regenerated in this run |
| Artifact integrity | 617 components (was 483) | `gradle/verification-metadata.xml`, sha256 | new versions added in this run; entries of the replaced versions are still in the file |
| Android Gradle Plugin | 9.4.1 | version catalog | newest stable |
| Kotlin + compose plugin | 2.4.21 (was 2.4.20) | version catalog | |
| KSP | 2.3.12 | version catalog | newest stable |
| `actions/checkout` | v7.0.1 | commit SHA `3d3c42e5…` | newest release |
| `gradle/wrapper-validation-action` | v3.5.0 | commit SHA `f9c9c575…` | newest release |
| Repositories | google(), mavenCentral(), gradlePluginPortal() | `FAIL_ON_PROJECT_REPOS` | |

### Assets

| Asset | Where | Source / licence | Notes |
|---|---|---|---|
| Montserrat 9.000, Doto 1.000 | `app/src/main/res/font/montserrat.ttf`, `doto.ttf` | OFL-1.1, texts in `app/src/main/assets/fonts/` | ✅ recorded in `VENDORED.md` (section 5) |
| Licence texts | `app/src/main/assets/licenses/` (GPL-3.0, Apache-2.0, OFL-1.1, CC BY 4.0) | shown on the About screen | new in 1.1.0 |
| RE* mark | `app/src/main/res/drawable/*`, `docs/re-mark.svg` | REMAKER's own | |
| Weather data | runtime, open-meteo.com | CC BY 4.0 | attribution in `nontranslatable.xml` |

No CDN scripts, no downloaded models, no WebView.

## 3. External Services

| Service | Referenced in | Credential |
|---|---|---|
| api.open-meteo.com (forecast) | `helper/receivers/WeatherReceiver.kt` | no |
| geocoding-api.open-meteo.com | `helper/receivers/WeatherReceiver.kt` | no |
| weather.com | `helper/SystemUtils.kt`, opens the browser, no request from the app | no |
| google.com, example.com | `ContextExtensions.kt`, `ShortcutHelper.kt`, intent resolution only | no |
| remaker.ch, github.com/reporemaker/REstart | About screen links (new: remaker.ch), README, release channel | no |
| services.gradle.org, maven.google.com, repo1.maven.org, plugins.gradle.org | build time only; artifacts locked and checksum-verified | no |

Unchanged: no accounts, no database server, no telemetry, no crash upload. The only runtime
network calls are the two open-meteo endpoints, with the location rounded to about 1 km. The
new URLs in the scan (gnu.org, apache.org, openfontlicense.org) are inside the bundled licence
texts, not requests.

## 4. Recommendations

Recommendations awaiting a human decision; LifeLine changed nothing except what the owner
asked for in this run (the four updates above).

| Package / item | Verdict | Built-in | Total lines | Needed (reachable) | Used API | Pulls in | License |
|---|---|---|---|---|---|---|---|
| palette-ktx | update to 1.1.0 from 2026-11-04 under the same rule | - | - | - | `Palette.from(bitmap)` | - | Apache-2.0 ✅ |
| `VENDORED.md` provenance | correct `Source` / `Upstream-Path` for both fonts to where the bytes really come from, or note the difference under `Modifications` (section 5) | - | - | - | - | - | OFL-1.1 ✅ |
| Verification metadata | regenerate from scratch once to drop the entries of replaced versions (617 → about 485) | - | - | - | - | - | - |
| fragment, lifecycle-runtime-ktx, coordinatorlayout | optional: declare explicitly | - | - | - | 9 / 1 / 2 files | already present | Apache-2.0 ✅ |
| Update rule | write the rule used in this run into `CONTRIBUTING.md` so contributors and future runs apply the same one | - | - | - | - | - | - |

No vendoring candidate: every third-party package is a framework library (Apache-2.0, deep
integration). `measure-package.sh` and `used-api.sh` cover npm only.

## 5. Vendor Tracking

`check-vendored.sh` on `VENDORED.md` (2 entries), with a manual check of each font file's
`name` table against upstream:

| Entry | Recorded | Latest upstream | Version status | Local integrity | Note |
|---|---|---|---|---|---|
| Montserrat | 9.000 | release tag `v7.222` (2021-08-11); master file `fonts/ttf/Montserrat-Regular.ttf` is version 9.000 | current | unchanged | the script reports `update-available` because the newest release tag is older than the master files; the font version is the same. The master file is ttfautohint-hinted (445,928 bytes), ours is unhinted (330,948 bytes): same version, different build |
| Doto | 1.000 | no release or tag; master `fonts/ttf/Doto.ttf` is version 1.000 (last change 2024-10-02) | current | unchanged | the script reports `unchecked`; the recorded `Upstream-Path` `fonts/ttf/Doto-Regular.ttf` does not exist upstream, and the static file there is the Black weight, ours is Regular |

🟡 Both copies match their recorded checksums, so nothing was changed after recording, but
neither can be traced byte for byte to the recorded upstream path. Most likely both came
through mLauncher from a Google Fonts build. `Status` and `Last-Checked` in `VENDORED.md`
already read `current` / 2026-10-25; LifeLine left the file unchanged.

## 6. Closed vulnerabilities (responsible disclosure)

No vulnerability has been closed yet.

## 7. Changes since last report

From `diff-state.sh` on the public snapshots 2026-10-25 → 2026-10-25-2
(`changes/2026-10-25-2-changes.md`):

- 2 vendored entries added: Montserrat 9.000, Doto 1.000 (1.1.0 font cleanup).
- 39 version changes, all from this run's updates: 30 Compose rows 1.11.4 → 1.12.1 (plus 3
  test/debug rows), 4 coroutines rows 1.9.0 → 1.11.0, kotlin-stdlib 2.4.20 → 2.4.21,
  transition 1.6.0 → 1.5.0 (transitive).
- No package added or removed on the Maven side, no scope or licence change, no vulnerability
  closed.
- Toolchain: Gradle 9.8.0 → 9.8.1, verification metadata 483 → 617 components.

Timeline across all runs: `timeline.md`.

## 8. Limits of This Analysis

- **Lock file read by hand.** `snapshot-state.sh` has no Gradle reader yet; Maven rows were
  built from `app/gradle.lockfile` (`prodReleaseRuntimeClasspath`) after the updates, with the
  snapshot's column rules. The baseline (2026-10-24) was a Gradle resolution, not a lock.
- `snapshot-state.sh`'s own OSV batch failed in this run (it only had the two GitHub-ecosystem
  font rows, which OSV does not know); OSV was queried separately for all 167 Maven rows and
  answered.
- **Vendored fonts**: no OSV coverage; upstream version checked by reading the font files'
  `name` tables, not by the script.
- **Test configurations**: direct test/debug dependencies at their declared versions;
  transitive test dependencies not listed.
- **Maven health from deps.dev**: `latest` includes pre-releases; maintainers and publisher
  not available for Maven; licences fetched for direct dependencies only.
- The Android platform, the Gradle plugins and the GitHub Actions were not queried against OSV.
- Import mapping is by package prefix; reflection and resource-only usage checked by grep for
  a few libraries only.
- The updates were verified by building (debug, signed release with R8, lint-vital) and by
  the 29 unit tests; they were **not** run on a device or emulator. Compose 1.12 is a minor
  release; check the home screen, drawer, settings and clock styles on a phone before the next
  release.
- `nogit/` (local notes, not in git) was excluded from every table.

## Sensitive report

A sensitive report was produced in this run. It is not part of this repository and is
delivered to the customer outside GitHub.

Generated: 2026-10-25 · sha256: e6725451212d2655a693504b275564a5e0d3406f3f71a9ea9d63035d8937aeb7
