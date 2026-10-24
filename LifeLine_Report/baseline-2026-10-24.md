# Dependency Report — REstart
Generated: 2026-10-24 · Scope: full
Ecosystems: Maven (Gradle, Android) · GitHub Actions · Previous report: none

**Summary.** REstart is an Android launcher built on AndroidX, Jetpack Compose and Material;
these frameworks are the "held hostage" list and are healthy. 191 packages were resolved
(34 direct runtime, 148 transitive, 9 test), all under permissive licences. Findings worth
acting on: one phantom dependency (`kotlinx-coroutines`, used in 9 files but not declared),
six declared-but-unused or misplaced dependencies that ship in the release APK for nothing,
one deprecated artifact, one alpha library in a security-relevant path, no Gradle lock file,
and eleven font files copied in without their licence texts.

## 1. Context Map

**Ecosystems found**

| Ecosystem | Manifest / lock | Notes |
|---|---|---|
| Maven via Gradle (Kotlin DSL) | `app/build.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml` | no lock file (dependency locking off); resolved tree taken with `gradlew app:dependencies` |
| GitHub Actions | `.github/workflows/validate-gradle-wrapper.yml`, `delete-unused-caches_ci.yml` | 3 actions |
| Gradle wrapper / toolchain | `gradle/wrapper/gradle-wrapper.properties`, `gradle/gradle-daemon-jvm.properties` | Gradle 9.8.0 (sha256-pinned), JDK 21 via foojay |

**Modules** (single Gradle module `:app`, Kotlin, package `ch.remaker`)

| Module | Files | Role |
|---|---|---|
| `restart` (root) | 4 | `REstartApp` (Application), `MainActivity`, `MainViewModel`, `CrashReportActivity` |
| `restart/ui` | 32 | home, drawer, settings, widgets, notes, shortcuts, onboarding, icon packs |
| `restart/helper` | 18 | system utils, weather receiver, biometric, private space, shortcuts |
| `restart/data` | 11 | `Prefs` (SharedPreferences + JSON backup), Room `WidgetDatabase`, list items |
| `restart/services`, `listener`, `init` | 7 | accessibility service, notification listener, init provider |
| `restart/style` | 2 | Compose theme |
| `common` | 10 | extensions, crash handler, colour utils, logger |
| `components` | 8 | font-aware view subclasses |
| `fuzzywuzzy` | 2 | fuzzy and smart search scoring (own code, despite the name) |

**Module → external dependency map** (import counts)

| Module | External packages |
|---|---|
| `restart/ui` | compose ui/foundation/runtime (101), core-ktx (38), material (11), recyclerview (11), lifecycle (13), kotlinx.coroutines (16), appcompat (12), fragment (6), navigation (4), activity (8), viewpager2 (3), biometric (2), compose animation/material (2) |
| `restart/helper` | kotlinx.coroutines (7), core-ktx (12), moshi (5), lifecycle (6), biometric (2), material (1), recyclerview (1), xmlpull (platform) |
| `restart/data` | room (10), moshi (4), core-ktx (5), compose runtime (1) |
| `restart/style` | compose ui/runtime/material (12) |
| `restart` root | activity (4), lifecycle (4), navigation (2), appcompat (2), biometric (1), kotlinx.coroutines (4), material (1), fragment (1) |
| `common` | core-ktx (8), recyclerview (3), palette (1), biometric (1), fragment (1), appcompat (1), annotation (3) |
| `components` | appcompat (6), material (2), coordinatorlayout (2), recyclerview (1), core-ktx (2) |
| `fuzzywuzzy` | none (JDK only) |

## 2. External Code Inventory

### Packages — direct runtime dependencies (34 resolved on `prodReleaseRuntimeClasspath`)

| Package | Version | Direct? | Used? | License | Last release | Risk signals | Criticality |
|---|---|---|---|---|---|---|---|
| androidx.compose.ui:ui | 1.11.4 | direct | yes (ui, style: 51 imports) | Apache-2.0 | 2026-10 | — | HIGH |
| androidx.compose.foundation:foundation | 1.11.4 | direct | yes (37) | Apache-2.0 | 2026-10 | — | HIGH |
| androidx.compose.runtime:runtime-android | 1.11.4 | direct | yes (25) | Apache-2.0 | 2026-10 | — | HIGH |
| androidx.compose.material:material | 1.11.4 | direct | yes (2 files) | Apache-2.0 | 2026-10 | — | MED |
| androidx.compose.animation:animation | 1.11.4 | direct | yes (1 file) | Apache-2.0 | 2026-10 | — | LOW |
| androidx.compose.ui:ui-tooling | 1.11.4 | direct (`implementation`) | no (0 imports) | Apache-2.0 | 2026-10 | 🟡 debug tooling shipped in release | LOW |
| com.google.android.material:material | 1.14.0 | direct | yes (13 files + themes) | Apache-2.0 (registry gave none) | 2026-05 | ⚪ licence not confirmed by registry | HIGH |
| androidx.appcompat:appcompat | 1.8.0 | direct | yes (24) | Apache-2.0 | 2026-08 | — | HIGH |
| androidx.core:core-ktx | 1.19.1 | direct | yes (67) | Apache-2.0 | 2026-09 | — | HIGH |
| androidx.recyclerview:recyclerview | 1.4.0 | direct | yes (16) | Apache-2.0 | 2025-01 | — | HIGH |
| androidx.lifecycle:lifecycle-viewmodel-ktx | 2.11.0 | direct | yes (15 files) | Apache-2.0 | 2026-10 | — | HIGH |
| androidx.lifecycle:lifecycle-common | 2.11.0 | direct (pulled by extensions) | yes | Apache-2.0 | 2026-10 | — | HIGH |
| androidx.lifecycle:lifecycle-extensions | 2.2.0 | direct | no (meta artifact, no classes imported) | Apache-2.0 | 2024-02 | 🟡 deprecated since 2020 · stale (982 days) | LOW |
| androidx.room:room-runtime / room-ktx | 2.8.5 | direct | yes (data: 10, `WidgetDatabase`) | Apache-2.0 | 2026-09 | — | HIGH |
| com.squareup.moshi:moshi / moshi-kotlin | 1.15.2 | direct | yes (4 files: prefs backup, theme, weather JSON) | Apache-2.0 | 2024-12 | — | MED |
| androidx.navigation:navigation-fragment-ktx / navigation-ui-ktx | 2.10.2 | direct | yes (5 files, `nav_graph.xml`) | Apache-2.0 | 2026-09 | — | MED |
| androidx.biometric:biometric-ktx | 1.4.0-alpha02 | direct | yes (5 files: app lock) | Apache-2.0 | 2024-08 | 🟡 alpha release in production · stale (801 days) | MED |
| androidx.constraintlayout:constraintlayout | 2.2.2 | direct | yes (6 layouts) | Apache-2.0 | 2026-07 | — | MED |
| androidx.constraintlayout:constraintlayout-compose | 1.1.2 | direct | no (0 imports) | Apache-2.0 | 2026-07 | 🟡 declared but unused | LOW |
| androidx.activity:activity / activity-ktx / activity-compose | 1.13.0 | direct | yes (13) | Apache-2.0 | 2026-09 | — | MED |
| androidx.viewpager2:viewpager2 | 1.1.0 | direct | yes (3 files, onboarding) | Apache-2.0 | 2024-05 | — | MED |
| androidx.palette:palette-ktx | 1.0.0 | direct | yes (1 file, `Palette.from`) | Apache-2.0 | 2026-10 (1.1.0) | — | LOW |
| androidx.work:work-runtime-ktx | 2.12.0 | direct | no (0 imports, no worker in manifest) | Apache-2.0 | 2026-09 | 🟡 declared but unused | LOW |
| org.apache.commons:commons-text | 1.15.0 | direct | no (0 imports) | Apache-2.0 | 2025-12 | 🟡 declared but unused | LOW |
| androidx.test:core / core-ktx | 1.7.0 | direct (`implementation`) | no in app code | Apache-2.0 | 2026-10 | 🟡 test library in the release classpath | LOW |
| androidx.test.espresso:espresso-idling-resource | 3.7.0 | direct (`implementation`) | no (0 imports) | Apache-2.0 | 2026-10 | 🟡 test library in the release classpath | LOW |
| androidx.databinding:viewbinding | 9.4.1 | direct (AGP) | yes (all layouts) | Apache-2.0 | 2026-10 | — | HIGH |
| org.jetbrains.kotlin:kotlin-stdlib | 2.4.20 | direct (plugin) | yes | Apache-2.0 | 2026-10 | — | HIGH |

Transitive runtime packages: 148, all AndroidX / Kotlin / Google (`androidx.*` 131,
`org.jetbrains.*` 13, `com.google.*` 4); licences not fetched for transitive packages this run
(see Limits). Test dependencies (9, `androidTestImplementation`/`testImplementation`): junit 4.13.2
(EPL-1.0, 2021), espresso 3.7.0, androidx.test runner/rules 1.7.0, fragment-testing 1.9.1,
compose ui-test 1.11.4, navigation-testing 2.10.2 — all current except junit 4, which is
maintenance-only by design.

**Findings**

- 🔴 **Phantom dependency: `org.jetbrains.kotlinx:kotlinx-coroutines-android`** — imported in
  9 files (`launch`, `Dispatchers`, `withContext`, `delay`, `CoroutineScope`, `Job`) but not
  declared; it arrives transitively at 1.9.0 through lifecycle/room/work. Its version is
  decided by whichever AndroidX bump pulls a different one.
- 🟡 Imported but only transitively declared (same vendor, low risk): `androidx.fragment`
  (9 files, via appcompat/navigation), `androidx.lifecycle:lifecycle-runtime-ktx`
  (`lifecycleScope`, via viewmodel-ktx), `androidx.coordinatorlayout` (2 files, via material).
- 🟡 Declared but unused in app code: `work-runtime-ktx`, `commons-text`,
  `constraintlayout-compose`; test libraries in `implementation`: `androidx.test:core-ktx`,
  `espresso-idling-resource`; `compose ui-tooling` in `implementation` instead of
  `debugImplementation`. All six ship in the release APK.
- 🟡 Deprecated: `lifecycle-extensions 2.2.0` (meta artifact, superseded in 2020; nothing is
  imported from it).
- 🟡 Pre-release in production: `biometric-ktx 1.4.0-alpha02` (app-lock path), 801 days old.
- 🟡 No lock file: Gradle dependency locking is off, so the transitive set is whatever resolves
  on the day; this run's snapshot is a resolution, not a lock.
- ⚪ `lifecycle-common` and `androidx.test:core` appear as direct only because
  `lifecycle-extensions` and `test:core-ktx` pull them to the top level.

### Toolchain

| Tool / action | Version | Pinned how | Notes |
|---|---|---|---|
| Gradle wrapper | 9.8.0 | sha256 in `gradle-wrapper.properties` | ✅ |
| JDK toolchain | 21 | foojay resolver 1.0.0, redirect URLs in `gradle-daemon-jvm.properties` | see Limits |
| Android Gradle Plugin | 9.4.1 | version catalog | |
| Kotlin + compose plugin | 2.4.20 | version catalog | |
| KSP | 2.3.12 | version catalog | Room and Moshi codegen |
| `actions/checkout` | v7.0.1 | tag | |
| `gradle/wrapper-validation-action` | v3.5.0 | tag | validates the wrapper jar on push/PR |
| `MyAlbum/purge-cache` | v3 | major tag | nightly cache cleanup, third-party author |
| Repositories | google(), mavenCentral(), gradlePluginPortal() | `FAIL_ON_PROJECT_REPOS` | no development/snapshot repositories (checked) |

### Assets

| Asset | Where | Source / licence | Notes |
|---|---|---|---|
| 11 font files (1.8 MB): Bitter, Doto, Fira Code, Hack, Lato, Merriweather, Montserrat, Quicksand, Raleway, Roboto, Source Code Pro | `app/src/main/res/font/*.ttf` | Google Fonts / upstream projects; expected OFL-1.1 (9), MIT + Bitstream Vera (Hack), Apache-2.0 (Roboto) | 🟡 no licence texts in the repo, no record of version or origin |
| RE* mark | `app/src/main/res/drawable/*`, `docs/re-mark.svg` | REMAKER's own | — |
| Weather data | runtime, open-meteo.com | CC BY 4.0 | attribution present in `nontranslatable.xml` |

No CDN scripts, no downloaded models, no WebView.

## 3. External Services

| Service | Referenced in | Credential |
|---|---|---|
| api.open-meteo.com (forecast) | `helper/receivers/WeatherReceiver.kt` (`URL.readText`) | no |
| geocoding-api.open-meteo.com | `helper/receivers/WeatherReceiver.kt` | no |
| weather.com | `helper/SystemUtils.kt` — opens the browser, no request from the app | no |
| google.com, example.com | `ContextExtensions.kt`, `ShortcutHelper.kt` — intent resolution only, never fetched | no |
| github.com/reporemaker/REstart | About screen links, README | no |
| api.foojay.io, services.gradle.org, maven.google.com, repo1.maven.org, plugins.gradle.org | build time only | no |

The app has no accounts, no database server, no telemetry and no crash upload; the only
runtime network calls are the two open-meteo endpoints, with the location rounded to about
1 km.

## 4. Recommendations

Recommendations awaiting a human decision — LifeLine changes nothing.

| Package | Verdict | Built-in | Total lines | Needed (reachable) | Used API | Pulls in | License |
|---|---|---|---|---|---|---|---|
| kotlinx-coroutines-android | **pin**: declare in `libs.versions.toml` at 1.9.0 (or newer) | — | not measured (Maven) | — | `launch`, `Dispatchers`, `withContext`, `delay`, `CoroutineScope`, `Job`, 9 files | already present | Apache-2.0 ✅ |
| work-runtime-ktx | **remove** | — | — | — | none | WorkManager + Room/sqlite deps on the classpath | Apache-2.0 ✅ |
| commons-text | **remove** | — | — | — | none | commons-lang3 | Apache-2.0 ✅ |
| constraintlayout-compose | **remove** | — | — | — | none | — | Apache-2.0 ✅ |
| compose ui-tooling | move to `debugImplementation` | — | — | — | none in app code | tooling-data, preview | Apache-2.0 ✅ |
| androidx.test core-ktx, espresso-idling-resource | move to `androidTestImplementation` (or remove) | — | — | — | none in app code | test runner deps | Apache-2.0 ✅ |
| lifecycle-extensions | **remove**; keep `lifecycle-viewmodel-ktx`, add `lifecycle-runtime-ktx` and `lifecycle-livedata-ktx` explicitly | — | — | — | nothing imported from it | lifecycle-common | Apache-2.0 ✅ |
| biometric-ktx 1.4.0-alpha02 | **pin to a stable** release (1.1.0 is the current stable line) or accept the alpha knowingly | — | — | — | `BiometricPrompt`, 5 files | — | Apache-2.0 ✅ |
| palette-ktx | keep (1 file, `Palette.from(bitmap)` for dominant colours; no platform equivalent) | — | — | — | 1 call | — | Apache-2.0 ✅ |
| Gradle locking | enable `dependencyLocking { lockAllConfigurations() }`, commit `app/gradle.lockfile` | — | — | — | — | — | — |
| Fonts | add the OFL / MIT / Apache licence texts next to the files and record origin + version in a `VENDORED.md` ledger | — | — | — | — | — | OFL-1.1 ✅ MIT ✅ Apache-2.0 ✅ |

No vendoring candidate: every third-party package is a framework library (Apache-2.0,
deep integration); nothing is small enough or unhealthy enough to copy in.
`measure-package.sh` and `used-api.sh` cover npm only, so lines and API surface were not
measured; the "Used API" column comes from the import scan.

## 5. Vendor Tracking

No `VENDORED.md` at the project root (`check-vendored.sh`: `NOLEDGER`). The scan found no
foreign licence headers in `app/src`: `fuzzywuzzy/` and `components/` are the project's own
code inherited from mLauncher, which is the fork lineage documented in `FOUNDATION.md`, not
vendored code. The only copied-in third-party files are the eleven fonts above.
**Recommendation**: create `VENDORED.md` with one entry per font family (upstream, version,
licence, checksum) — LifeLine can draft it on approval.

## 6. Closed vulnerabilities (responsible disclosure)

No vulnerability has been closed yet.

## 7. Limits of This Analysis

- **No lock file.** Package rows come from `./gradlew app:dependencies --configuration
  prodReleaseRuntimeClasspath` on 2026-10-24 (resolved versions, not a lock). A future run on
  another day may resolve differently until dependency locking is enabled.
- **Test configurations**: direct test dependencies were taken from `gradle/libs.versions.toml`
  at their declared versions; transitive test dependencies are not listed.
- **Maven health from deps.dev**: `latest` includes pre-release versions (alpha/beta), so
  "newer available" is not always "stable newer"; maintainers and publisher are not available
  for Maven; licences were fetched for direct dependencies only (transitive: `-`).
- The Android platform, the Gradle plugins (AGP, Kotlin, KSP) and the GitHub Actions were not
  queried against OSV (no package rows for them); OSV was reachable for the Maven rows.
- Import mapping is by package prefix (`androidx.lifecycle.*` → lifecycle artifacts etc.);
  reflection and resource-only usage (layout XML, manifest) were checked by grep for
  ConstraintLayout, ViewPager2, ComposeView and WorkManager only.
- `measure-package.sh`, `used-api.sh`, `upstream-diff.sh` support npm only; nothing was
  measured for Maven packages.
- JDK provisioning via foojay redirect URLs could not be checked for integrity by LifeLine.
- `nogit/` (local notes, not in git) was excluded from every table; `scan-imports.sh` did
  list its Python helper, which was ignored.

## Sensitive report

A sensitive report was produced in this run. It is not part of this repository and is
delivered to the customer outside GitHub.

Generated: 2026-10-24 · sha256: 7dff41841cf9dc29345fdec9fb3b6939f0e5919f3c1aabc3087b3b6922704641
