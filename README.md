<p align="center"><img src="docs/re-mark.svg" width="96" alt="RE* mark"></p>

# RE*start

A quiet Android home screen. Text instead of icon grids, apps sorted into categories you
control, widgets that stay where you put them, and nothing that phones home.

RE*start is a GPL-3.0 fork of [mLauncher](https://github.com/CodeWorksCreativeHub/mLauncher),
which descends from [Olauncher](https://github.com/tanujnotes/Olauncher). Where it comes from,
who built it and what changed at the fork point: [FOUNDATION.md](FOUNDATION.md).

<p align="center">
  <img src="docs/screenshots/drawer-categories.png" width="24%" alt="Drawer with categories">&nbsp;
  <img src="docs/screenshots/widget-resize.png" width="24%" alt="Widget resizing">
</p>

## What it does

- **Drawer with categories** — pinned apps on top, the rest in groups that come from your tags
  or from what the app tells Android it is; one group open at a time, or a plain A–Z list.
- **Smart search** — matches word beginnings and single-letter typos; if no app matches it looks
  in files, then contacts, all on the device.
- **Widgets on a grid** — placed and resized in cells, so they do not drift, overlap or leave
  the page.
- **Shortcuts from "Add to home screen"** — web apps and app shortcuts, through Android's
  standard route, no WebView.
- **Settings that survive updates** — an automatic copy before every update, restorable from
  Settings → Backup/Restore.
- **Quiet by design** — no ads, no analytics, no crash uploads, no accounts. The only request the
  app makes on its own is the weather, with the location rounded to about 1 km. Details in
  [SECURITY.md](SECURITY.md).

## Install

Download the APK from the [latest release](https://github.com/reporemaker/REstart/releases/latest)
and open it on the phone. Android 9 (API 28) or newer. After installing, set RE*start as the
home app: Settings → Apps → Default apps → Home app.

RE*start is a new app with its own package name (`ch.remaker.restart`); it does not update a
Panther Launcher installation. Export your settings there (Settings → Backup/Restore) and import
them here.

## Build

```
./gradlew assembleProdDebug
```

For a signed release put the keystore at `app/restart.jks` and the secrets in
`app/keystore.properties` (`KEY_STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`; the file is
ignored by git), then:

```
./gradlew assembleProdRelease
```

## Contributing

Issues and pull requests are open; see [CONTRIBUTING.md](CONTRIBUTING.md). Security problems go
through [SECURITY.md](SECURITY.md), not a public issue. Changes since the fork are listed in
[CHANGELOG.md](CHANGELOG.md).

## Licence

[GPL-3.0](LICENSE), like Olauncher, OlauncherCF and mLauncher before it. The original copyright
notices are kept; the people behind the code are named in [FOUNDATION.md](FOUNDATION.md).

[RE\*maker](https://remaker.ch) // SEU//EmekUlas
