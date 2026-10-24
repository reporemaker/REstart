# Dependency Report — REstart
Generated: 2026-10-25 · Scope: full
Ecosystems: Maven (Gradle, Android) · GitHub Actions · Previous report: 2026-10-24 (baseline)

**Summary.** Second run, one day after the baseline. The project applied the baseline's
recommendations in commit `73cd4ab4`: the phantom coroutines dependency is declared, the
four unused packages are gone, test and debug tooling left the release APK, the biometric
library is on a stable release, every artifact is now locked (`app/gradle.lockfile`) and
sha256-verified (`gradle/verification-metadata.xml`), the JDK is no longer downloaded at
build time and both GitHub Actions are pinned to commit SHAs. The release runtime
classpath shrank from 182 to 155 packages (167 rows with test/debug, was 191), all under
permissive licences. Open items from the baseline: the eleven font files still have no
licence texts or origin record; `com.google.android.material` still reports no licence in the
registry. New, minor: two stable updates are available (`kotlinx-coroutines` 1.9.0 → 1.11.0,
`palette-ktx` 1.0.0 → 1.1.0). v1.0.0 was released on 2026-10-25 with this dependency set.

## 1. Context Map

**Ecosystems found**

| Ecosystem | Manifest / lock | Notes |
|---|---|---|
| Maven via Gradle (Kotlin DSL) | `app/build.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`; lock: `app/gradle.lockfile`, `settings-gradle.lockfile` | dependency locking on for all configurations; checksums in `gradle/verification-metadata.xml` (483 components, sha256) |
| GitHub Actions | `.github/workflows/validate-gradle-wrapper.yml` | 2 actions, SHA-pinned; the nightly cache-purge workflow was removed |
| Gradle wrapper / toolchain | `gradle/wrapper/gradle-wrapper.properties` | Gradle 9.8.0 (sha256-pinned); JDK from the local installation (foojay resolver removed) |

**Modules** (single Gradle module `:app`, Kotlin, package `ch.remaker`) — unchanged since the
baseline: `restart` (app, 4 files), `restart/ui` (32), `restart/helper` (18), `restart/data`
(11), `restart/services|listener|init` (7), `restart/style` (2), `common` (10), `components`
(8), `fuzzywuzzy` (2, own code). Entry points: `MainActivity` (HOME intent),
`REstartApp`, `REstartInitProvider`, the accessibility and notification services, four widget
receivers.

**Module → external dependency map** (import counts from `scan-imports.sh`, whole app):
`androidx.compose.*` 116 · `androidx.core` 67 · `kotlinx.coroutines` 27 · `androidx.lifecycle`
24 · `androidx.appcompat` 24 · `androidx.recyclerview` 16 · `com.google` (material) 15 ·
`androidx.activity` 13 · `androidx.room` 10 · `com.squareup` (moshi) 9 · `androidx.fragment` 9
· `androidx.navigation` 6 · `androidx.biometric` 6 · `androidx.annotation` 6 ·
`androidx.viewpager2` 3 · `androidx.coordinatorlayout` 2 · `androidx.palette` 1 ·
`androidx.viewbinding` 1; `org.xmlpull`, `org.json`, `android.*` are platform. Per module the
distribution is the one recorded in the baseline report (`baseline-2026-10-24.md`, section 1);
no source file changed its imports since, except `CrashReportActivity` (the e-mail sender
is gone) and `SystemUtils` (one icon style instead of four).

## 2. External Code Inventory

### Packages — direct runtime dependencies (27 on `prodReleaseRuntimeClasspath`, from the lock file)

| Package | Version | Direct? | Used? | License | Last release (deps.dev `latest`) | Risk signals | Criticality |
|---|---|---|---|---|---|---|---|
| androidx.compose.ui:ui | 1.11.4 | direct | yes (51 imports) | Apache-2.0 | 1.13.0-beta02, 2026-10-21 | — | HIGH |
| androidx.compose.foundation:foundation | 1.11.4 | direct | yes (37) | Apache-2.0 | 1.13.0-beta02 | — | HIGH |
| androidx.compose.runtime:runtime-android | 1.11.4 | direct | yes (25) | Apache-2.0 | 1.13.0-beta02 | — | HIGH |
| androidx.compose.material:material | 1.11.4 | direct | yes (2 files) | Apache-2.0 | 1.13.0-beta02 | — | MED |
| androidx.compose.animation:animation | 1.11.4 | direct | yes (1 file) | Apache-2.0 | 1.13.0-beta02 | — | LOW |
| com.google.android.material:material | 1.14.0 | direct | yes (13 files + themes) | Apache-2.0 per project; registry: none | 1.14.0, 2026-05-20 | ⚪ licence not confirmed by registry (unchanged) | HIGH |
| androidx.appcompat:appcompat | 1.8.0 | direct | yes (24) | Apache-2.0 | 1.8.0, 2026-08-12 | — | HIGH |
| androidx.core:core-ktx | 1.19.1 | direct | yes (67) | Apache-2.0 | 1.19.1, 2026-09-23 | — | HIGH |
| androidx.recyclerview:recyclerview | 1.4.0 | direct | yes (16) | Apache-2.0 | 1.4.0, 2025-01-15 | — | HIGH |
| androidx.lifecycle:lifecycle-viewmodel-ktx | 2.11.0 | direct | yes (15 files) | Apache-2.0 | 2.12.0-alpha05 | — | HIGH |
| androidx.lifecycle:lifecycle-process | 2.11.0 | direct (new) | yes (`ProcessLifecycleOwner`, `AppReloader`) | Apache-2.0 | 2.12.0-alpha05 | — | MED |
| androidx.room:room-runtime / room-ktx | 2.8.5 | direct | yes (data: 10, `WidgetDatabase`) | Apache-2.0 | 2.8.5, 2026-09-09 | — | HIGH |
| com.squareup.moshi:moshi / moshi-kotlin | 1.15.2 | direct | yes (4 files) | Apache-2.0 | 1.15.2, 2024-12-05 | — | MED |
| androidx.navigation:navigation-fragment-ktx / navigation-ui-ktx | 2.10.2 | direct | yes (5 files, `nav_graph.xml`) | Apache-2.0 | 2.10.2, 2026-09-23 | — | MED |
| androidx.biometric:biometric | 1.1.0 | direct (replaces `-ktx` alpha) | yes (5 files: app lock, `BiometricPrompt`/`BiometricManager` only) | Apache-2.0 | 1.4.0-alpha07, 2026-04-22 (no stable newer than 1.1.0) | — | MED |
| androidx.constraintlayout:constraintlayout | 2.2.2 | direct | yes (6 layouts) | Apache-2.0 | 2.2.2, 2026-07-29 | — | MED |
| androidx.activity:activity / activity-ktx / activity-compose | 1.13.0 | direct | yes (13) | Apache-2.0 | 1.14.0-alpha03 | — | MED |
| androidx.viewpager2:viewpager2 | 1.1.0 | direct | yes (3 files, onboarding) | Apache-2.0 | 1.1.0, 2024-05-23 | — | MED |
| androidx.palette:palette-ktx | 1.0.0 | direct | yes (1 file, `Palette.from`) | Apache-2.0 | 1.1.0, 2026-10-21 | ⚪ stable update available | LOW |
| org.jetbrains.kotlinx:kotlinx-coroutines-android | 1.9.0 | direct (new; was phantom) | yes (27 imports, 9 files) | Apache-2.0 | 1.11.0, 2026-05-07 | ⚪ stable update available | HIGH |
| androidx.databinding:viewbinding | 9.4.1 | direct (AGP) | yes (all layouts) | Apache-2.0 | 9.5.0-rc01 | — | HIGH |
| org.jetbrains.kotlin:kotlin-stdlib | 2.4.20 | direct (plugin) | yes | Apache-2.0 | 2.5.0-Beta2 | — | HIGH |

Transitive runtime packages: 128 (was 148), all AndroidX / Kotlin / Google; licences not
fetched for transitive packages (see Limits). Direct test/debug dependencies (12, from the
version catalog): junit 4.13.2 (EPL-1.0, 2021, maintenance-only by design), espresso
core/contrib/idling-resource 3.7.0, androidx.test runner/rules/core-ktx 1.7.0,
fragment-testing 1.9.1, compose ui-test-junit4/ui-test-manifest/ui-tooling 1.11.4,
navigation-testing 2.10.2 — current on their stable lines.

**Findings**

- ✅ Resolved since the baseline: phantom `kotlinx-coroutines-android` (now declared);
  unused `work-runtime-ktx`, `commons-text`, `constraintlayout-compose`,
  `lifecycle-extensions` (removed, 18 transitive packages went with them); `ui-tooling`,
  `androidx.test:core-ktx`, `espresso-idling-resource` out of the release APK; alpha
  `biometric-ktx` replaced by stable `biometric 1.1.0`; no lock file → locked and
  sha256-verified.
- 🟡 Imported but only transitively declared (same vendor, unchanged): `androidx.fragment`
  (9 files, via appcompat/navigation), `androidx.lifecycle:lifecycle-runtime-ktx`
  (`lifecycleScope`, via viewmodel-ktx), `androidx.coordinatorlayout` (2 files, via material).
  With locking on, their versions can no longer move unnoticed; declaring them is tidiness,
  not risk.
- ⚪ Stable updates available: `kotlinx-coroutines-android` 1.9.0 → 1.11.0, `palette-ktx`
  1.0.0 → 1.1.0. Everything else on the direct list is on its latest stable release; the
  `latest` column shows alphas/betas for Compose, Lifecycle, Activity and Kotlin that are not
  recommendations.
- ⚪ `androidx.concurrent:concurrent-futures` moved from 1.2.0 to 1.1.0 (transitive; the
  1.2.0 came through WorkManager). No advisory on either version; nothing to do.
- ⚪ `com.google.android.material:material`: deps.dev returns no licence; the project's own
  LICENSE is Apache-2.0. Unchanged, informational.

### Toolchain

| Tool / action | Version | Pinned how | Notes |
|---|---|---|---|
| Gradle wrapper | 9.8.0 | sha256 in `gradle-wrapper.properties` | ✅ |
| JDK | 21 (local) | — | foojay resolver and `gradle-daemon-jvm.properties` removed 2026-10-24; nothing is downloaded for the JDK at build time |
| Dependency set | all configurations | `app/gradle.lockfile`, `settings-gradle.lockfile` | `dependencyLocking { lockAllConfigurations() }` |
| Artifact integrity | 483 components | `gradle/verification-metadata.xml`, sha256 | written 2026-10-24; a changed jar fails the build |
| Android Gradle Plugin | 9.4.1 | version catalog | |
| Kotlin + compose plugin | 2.4.20 | version catalog | |
| KSP | 2.3.12 | version catalog | Room and Moshi codegen |
| `actions/checkout` | v7.0.1 | commit SHA `3d3c42e5…` | ✅ |
| `gradle/wrapper-validation-action` | v3.5.0 | commit SHA `f9c9c575…` | ✅ validates the wrapper jar on push/PR |
| Repositories | google(), mavenCentral(), gradlePluginPortal() | `FAIL_ON_PROJECT_REPOS` | no development/snapshot repositories |

### Assets

| Asset | Where | Source / licence | Notes |
|---|---|---|---|
| 11 font files (1.8 MB): Bitter, Doto, Fira Code, Hack, Lato, Merriweather, Montserrat, Quicksand, Raleway, Roboto, Source Code Pro | `app/src/main/res/font/*.ttf` | Google Fonts / upstream projects; expected OFL-1.1 (9), MIT + Bitstream Vera (Hack), Apache-2.0 (Roboto) | 🟡 unchanged: no licence texts in the repo, no record of version or origin. OFL requires the licence text to travel with the font; 1.0.0 shipped without it |
| RE* mark | `app/src/main/res/drawable/*`, `docs/re-mark.svg` | REMAKER's own | now also the single "RE* star" icon style |
| Weather data | runtime, open-meteo.com | CC BY 4.0 | attribution present in `nontranslatable.xml` |

No CDN scripts, no downloaded models, no WebView. (`fonts.googleapis.com` appears once in
the URL scan, in the local `nogit/` notes that are not part of the repository; nothing in the
app references it.)

## 3. External Services

| Service | Referenced in | Credential |
|---|---|---|
| api.open-meteo.com (forecast) | `helper/receivers/WeatherReceiver.kt` | no |
| geocoding-api.open-meteo.com | `helper/receivers/WeatherReceiver.kt` | no |
| weather.com | `helper/SystemUtils.kt` — opens the browser, no request from the app | no |
| google.com, example.com | `ContextExtensions.kt`, `ShortcutHelper.kt` — intent resolution only, never fetched | no |
| github.com/reporemaker/REstart | About screen links, README, release channel | no |
| services.gradle.org, maven.google.com, repo1.maven.org, plugins.gradle.org | build time only; artifacts now locked and checksum-verified | no |

Unchanged: no accounts, no database server, no telemetry, no crash upload (the crash dialog
shows a contact address as plain text and sends nothing). The only runtime network calls
are the two open-meteo endpoints, with the location rounded to about 1 km.

## 4. Recommendations

Recommendations awaiting a human decision — LifeLine changes nothing.

| Package / item | Verdict | Built-in | Total lines | Needed (reachable) | Used API | Pulls in | License |
|---|---|---|---|---|---|---|---|
| Fonts (11 files) | add the OFL-1.1 / MIT / Apache-2.0 texts next to the files and record origin, version and checksum in a `VENDORED.md` ledger; decide how many of the 11 stay selectable (APK size) | — | — | — | — | — | OFL-1.1 ✅ MIT ✅ Apache-2.0 ✅ |
| kotlinx-coroutines-android | optional: 1.9.0 → 1.11.0 (stable); regenerate the lock file and verification metadata in the same change | — | not measured (Maven) | — | `launch`, `Dispatchers`, `withContext`, `delay`, `CoroutineScope`, `Job` | already present | Apache-2.0 ✅ |
| palette-ktx | optional: 1.0.0 → 1.1.0 (stable) | — | — | — | `Palette.from(bitmap)` | — | Apache-2.0 ✅ |
| fragment, lifecycle-runtime-ktx, coordinatorlayout | optional: declare explicitly (imported directly, arrive transitively) | — | — | — | 9 / 1 / 2 files | already present | Apache-2.0 ✅ |
| Lock file hygiene | every dependency change runs `./gradlew app:dependencies --write-locks` and `--write-verification-metadata sha256`; review both generated files in the PR | — | — | — | — | — | — |

No vendoring candidate: every third-party package is a framework library (Apache-2.0,
deep integration); nothing is small enough or unhealthy enough to copy in.
`measure-package.sh` and `used-api.sh` cover npm only, so lines and API surface were not
measured; the "Used API" column comes from the import scan.

## 5. Vendor Tracking

No `VENDORED.md` at the project root (`check-vendored.sh`: `NOLEDGER`). No foreign licence
headers in `app/src`; `fuzzywuzzy/` and `components/` are the project's own code inherited
from mLauncher (lineage in `FOUNDATION.md`), not vendored code. The only copied-in
third-party files are the eleven fonts. **Recommendation** (unchanged): create `VENDORED.md`
with one entry per font family (upstream, version, licence, checksum) — LifeLine can draft it
on approval.

## 6. Closed vulnerabilities (responsible disclosure)

No vulnerability has been closed yet.

## 7. Changes since last report

From `diff-state.sh` on the public snapshots 2026-10-24 → 2026-10-25
(`changes/2026-10-25-changes.md`):

- 24 packages removed (6 direct, 18 transitive): the unused and misplaced dependencies of
  the baseline and what they pulled in — WorkManager, commons-lang3/text, material3 and
  material-icons-core, Compose tooling-data/preview, constraintlayout-compose,
  lifecycle-extensions/common-java8/service, test core/monitor, tracing-ktx,
  concurrent-futures-ktx, kotlin-stdlib-common.
- 6 scope changes: `kotlinx-coroutines-android` and `lifecycle-process` transitive → direct;
  `ui-tooling`, `test:core-ktx`, `espresso-idling-resource` release → test/debug;
  `lifecycle-common` direct → transitive.
- 2 version changes: `biometric` 1.4.0-alpha02 (`-ktx`) → 1.1.0; `concurrent-futures`
  1.2.0 → 1.1.0 (transitive).
- No package added, no vulnerability closed, no maintainer/publisher/repository change
  (not observable for Maven). 143 packages unchanged.
- Toolchain: lock file, verification metadata and SHA-pinned actions appeared; foojay JDK
  download and the cache-purge workflow disappeared.

Timeline across all runs: `timeline.md`.

## 8. Limits of This Analysis

- **Lock file read by hand.** `snapshot-state.sh` has no Gradle reader yet; the rows were
  built from `app/gradle.lockfile` (configuration `prodReleaseRuntimeClasspath`, i.e. the
  release APK) with the snapshot's column rules. The baseline (2026-10-24) was a Gradle
  resolution, not a lock, so in this comparison "removed" is reliable while "added" could not
  have been detected.
- **Test configurations**: direct test/debug dependencies at the versions declared in
  `gradle/libs.versions.toml`; transitive test dependencies not listed.
- **Maven health from deps.dev**: `latest` includes pre-release versions (alpha/beta);
  maintainers and publisher are not available for Maven; licences fetched for direct
  dependencies only (transitive: `-`).
- The Android platform, the Gradle plugins (AGP, Kotlin, KSP) and the GitHub Actions were not
  queried against OSV (no package rows for them); OSV was reachable for all 167 Maven rows.
- Import mapping is by package prefix; reflection and resource-only usage (layouts,
  manifest) were checked by grep for ConstraintLayout, ViewPager2 and ComposeView only.
- `measure-package.sh`, `used-api.sh`, `upstream-diff.sh` support npm only; nothing was
  measured for Maven packages.
- `gradle/verification-metadata.xml` pins what was in the developer's cache on 2026-10-24;
  LifeLine did not re-download artifacts to confirm the checksums against the registries.
- `nogit/` (local notes, not in git) was excluded from every table.

## Sensitive report

A sensitive report was produced in this run. It is not part of this repository and is
delivered to the customer outside GitHub.

Generated: 2026-10-25 · sha256: e5c985391c11bcc84fa77c39924da1392a0a570b6fb198908d632acfdc1f5f74
