# Contributing

Issues and pull requests are welcome. A few things make them easier to take in.

## Issues

- **Bug**: what you did, what happened, what you expected, the REstart version (Settings →
  About) and the Android version. A screenshot or a crash log file name helps.
- **Feature**: what you want to achieve, not only how. REstart is a quiet launcher; features
  that add noise, accounts or network traffic will not be accepted.
- Security problems: see [SECURITY.md](SECURITY.md), not a public issue.

## Pull requests

- One change per pull request, on a branch named `fix/…`, `feat/…` or `chore/…`.
- Keep the style of the surrounding code. No new dependencies without saying why.
- Every user-facing string goes through `strings.xml`; English is the source language.
- Build and run the unit tests before opening: `./gradlew assembleProdDebug testProdDebugUnitTest`.
- Say what you tested on (device or emulator, Android version).

By contributing you agree that your contribution is licensed under GPL-3.0, like the rest of
the code.

## Where the code comes from

See [FOUNDATION.md](FOUNDATION.md): the lineage from Olauncher to REstart, the people behind
it, and the list of differences from mLauncher at the fork point.
