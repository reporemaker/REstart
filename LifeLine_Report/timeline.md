# Dependency Timeline: REstart
Runs: 4 · 2026-10-24 → 2026-10-26

Regenerated from every public snapshot in `state/`. Only packages that changed in an
important field are listed, most recently changed first; versions for direct and vendored
packages only. Maintainers, publisher and repository are not exposed by the Maven registries
LifeLine reads, so those rows are absent for this project.

**Coverage line at 2026-10-26.** From the fourth run the snapshot reads the Gradle lock files
itself and includes the development classpaths (lint tool, instrumented tests, annotation
processing, Compose tooling). Rows that appear or change scope to `-dev` on 2026-10-26 were
there before on those classpaths; the release runtime did not change in that run (155 packages,
same versions).

## org.jetbrains.kotlinx:kotlinx-coroutines-android (direct-runtime, `.`)
| Field | Steps |
|---|---|
| version | 2026-10-24 1.9.0 → 2026-10-25-2 1.11.0 |
| scope | 2026-10-24 transitive-runtime → 2026-10-25 direct-runtime |

Reading: a phantom dependency that was declared, then brought to the current stable line.

## androidx.compose.ui:ui-tooling (direct-dev, `.`)
| Field | Steps |
|---|---|
| version | 2026-10-24 1.11.4 → 2026-10-25-2 1.12.1 |
| scope | 2026-10-24 direct-runtime → 2026-10-25 direct-dev |

## androidx.compose.ui:ui, foundation:foundation, runtime:runtime-android, material:material, animation:animation (direct-runtime, `.`)
| Field | Steps |
|---|---|
| version | 2026-10-24 1.11.4 → 2026-10-25-2 1.12.1 |

The same step for `ui-test-junit4` and `ui-test-manifest` (direct-dev).

## org.jetbrains.kotlin:kotlin-stdlib (transitive-runtime, `.`)
| Field | Steps |
|---|---|
| version | 2026-10-24 2.4.20 → 2026-10-25-2 2.4.21 |
| scope | 2026-10-24 direct-runtime → 2026-10-26 transitive-runtime |

Reading: a relabel, not a change; it comes with the Kotlin plugin, not from a declaration.

## androidx.databinding:viewbinding (transitive-runtime, `.`)
| Field | Steps |
|---|---|
| scope | 2026-10-24 direct-runtime → 2026-10-26 transitive-runtime |

Reading: a relabel; view binding is switched on in the Android plugin, not declared. Licence
not collected after the relabel (licences are fetched for direct packages only).

## androidx.biometric:biometric (direct-runtime, `.`)
| Field | Steps |
|---|---|
| version | 2026-10-24 1.4.0-alpha02 (as `biometric-ktx`) → 2026-10-25 1.1.0 |
| scope | 2026-10-24 transitive-runtime → 2026-10-25 direct-runtime |

## androidx.lifecycle:lifecycle-process (direct-runtime, `.`)
| Field | Steps |
|---|---|
| scope | 2026-10-24 transitive-runtime → 2026-10-25 direct-runtime |

## androidx.lifecycle:lifecycle-common (transitive-runtime, `.`)
| Field | Steps |
|---|---|
| scope | 2026-10-24 direct-runtime → 2026-10-25 transitive-runtime |

Licence not collected after 2026-10-24 (licences are fetched for direct packages only).

## androidx.test:core-ktx, androidx.test.espresso:espresso-idling-resource (direct-dev, `.`)
| Field | Steps |
|---|---|
| scope | 2026-10-24 direct-runtime → 2026-10-25 direct-dev |

## Arrivals and departures
- Listed from 2026-10-26 (development classpaths only, 139 rows): among them several packages
  that left the release runtime on 2026-10-25 and are still used by tests or tooling
  (`material3`, `material-icons-core`, the `ui-tooling-*` family, `concurrent-futures-ktx`,
  `lifecycle-common-java8`, `androidx.test:core`, `monitor`). None of them
  is in the APK.
- Arrived 2026-10-25-2 (vendored): JulietaUla/Montserrat 9.000, oliverlalan/Doto 1.000.
- Gone 2026-10-25 (24): androidx.biometric:biometric-ktx,
  androidx.compose.material3:material3, material3-android,
  androidx.compose.material:material-icons-core, material-icons-core-android,
  androidx.compose.ui:ui-tooling-android, ui-tooling-data, ui-tooling-data-android,
  ui-tooling-preview, ui-tooling-preview-android, androidx.concurrent:concurrent-futures-ktx,
  androidx.constraintlayout:constraintlayout-compose, constraintlayout-compose-android,
  androidx.lifecycle:lifecycle-common-java8, lifecycle-extensions, lifecycle-service,
  androidx.test:core, androidx.test:monitor, androidx.tracing:tracing-ktx,
  androidx.work:work-runtime, work-runtime-ktx, org.apache.commons:commons-lang3, commons-text,
  org.jetbrains.kotlin:kotlin-stdlib-common.
