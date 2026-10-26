# Foundation

Where the code in REstart comes from, who wrote it, and what changed after the fork.
Written 24 October 2026; the lists below end at the fork point and are not updated.

## 1. Lineage

Three people, four project names, GPL-3.0 from start to finish:

| Project | Who | When | What happened |
|---|---|---|---|
| [Olauncher](https://github.com/tanujnotes/Olauncher) | Tanuj M. | July 2020 → | Written from scratch; nothing before it. |
| [OlauncherCF](https://github.com/OlauncherCF/OlauncherCF) | Joscha Loos | July 2021 → 2024 | Clutter-free fork of Olauncher: ads and links removed. |
| [mLauncher](https://github.com/CodeWorksCreativeHub/mLauncher) / Multi Launcher | Wayne Wesley | December 2022 → | Built on a copy of OlauncherCF. The organisation changed its name four times (HeCodes2Much → HeAndroids2Much → DroidWorksStudio → CodeWorksCreativeHub), the maintainer three times; all the same person. |
| Panther Launcher → **REstart** | SEU//EmekUlas, REMAKER | October 2026 → | Forked from mLauncher on 2 October 2026 with the full history kept. |

Verified against GitHub, F-Droid and Wayback Machine records.

## 2. Thank you

Everyone whose commits are in this repository's history up to the fork point, side by side.
Bots are left out; people who used more than one name are listed once.

Tanuj M. (tanujnotes) · Joscha Loos (jooooscha) · Wayne Wesley (CreativeCodeCat, earlier The-Repo-Club and HeCodes2Much) · Ilithy · ferlanero · sr093906 · ar1ja · Masashige Machan (Machannn) · Ari Archer (TruncatedDinosour) · ongots · AstronW · Enmanuel Jaco (MrJako2001) · Samuel Hutchinson (flightmansam) · Bathula Sharath Kumar · Helfexen · Moji (mojienjoyment) · Sannoob3144 · anotherus3r · linsui · ximossi (abrossimow) · ShLerP (mikropsoft) · Ahmad Abu Yahya · Anurag (anuragkanojiya1) · Arman Hayots · CYAXXX · David Marzal · Ftamino · Hugo Cousin · Ikko Eltociear Ashimine · Il mastro Stefanuzzo · Jaak (quxup) · Joris van Dijk · Naman Kumar Jha · Nausill_on · Nikita Skvortsov · Omer Acar · R7010A · Slava · Suletta-Majo · TettaVolante · Tiago Carmo (ReduxFlakes) · YakisikliBaran · dennis8 · florient22 · lars (Sarsharses) · orestisfra · éclairevoyant

This launcher stands on their work. The complete record, commit by commit, is `git log`.

## 3. What went back upstream

Before the fork, the general-purpose work was offered to mLauncher. Both pull requests were merged:

- **[#1104](https://github.com/CodeWorksCreativeHub/mLauncher/pull/1104): pinned shortcuts from "Add to home screen"** (merged 2 October 2026, 16 files, closes #283). Browsers and apps can add web apps and shortcuts through Android's standard route; no WebView, no new permissions, and a shortcut's settings never touch the app that owns it.
- **[#1115](https://github.com/CodeWorksCreativeHub/mLauncher/pull/1115): app alias shown on the home screen, favourites and widget** (merged 22 October 2026, 4 files). A renamed app now shows its alias everywhere, not only in the drawer.
- **[#1110](https://github.com/CodeWorksCreativeHub/mLauncher/issues/1110): Turkish translation**: 106 of 349 strings corrected and handed over; the maintainer uploaded them to Crowdin.

## 4. Why an independent project

I came to mLauncher as a user: a good launcher, a good project. I fixed the two things I
missed on my own phone and sent them in; both were merged. I was not part of the team and
did not need to be; that is how open source works.

The list grew with use. On 3 October 2026 I read the whole code base with security in mind.
There was no active leak; but there was a piece that sent crash logs to the maintainer's
server, silenced only by a comment line, third-party widget code loaded into the launcher's
own process, and notes, hidden apps and location going to cloud backup in plain text. Next
to that, dozens of small bugs and an interface I wanted done differently. The arithmetic of
doing this through pull requests is simple: every change its own branch, its own review,
its own wait; months. I wanted something that worked on my phone now. So I forked. Since
that day I fix every problem I see here, at my own pace. I write the code together with AI
tools; the one who reads it, tests it and signs it is me.

I did not send the team a separate report; the fixes are here in the open, each with its
reason. In hindsight the right order would have been a short note first, then the fork. The
code is GPL-3.0; the mLauncher team, like anyone, can take from here whatever they want, and
I will keep contributing there. In this repository, every decision and every vulnerability
is mine to answer for.

The three lists below are where we departed from mLauncher at the fork point: security
first, then bug fixes, then the interface. Each item is a name and a line or two; the
details are in the git history.

### 4.1 Security

- **Crash uploader removed**: the disabled code that posted crash logs to `crash.5646316.xyz` is gone from the build.
- **Crash e-mail to the user**: reports no longer go to the upstream developer; the user enters the recipient.
- **Old domain links removed**: theme download and privacy-policy links to `5646316.xyz` are gone.
- **Persistent app lock**: lock changes after the first were lost on restart; they are now saved.
- **No lock bypass**: locked apps cannot be opened through the home quick-action buttons or the date tap without authentication.
- **No lock without a screen lock**: an app can no longer be locked on a device without one, which used to make it silently unopenable.
- **Accessibility service trimmed**: no permission to read screen content, no notification text written to the log.
- **Widgets without code loading**: widgets render without the owner app's code being loaded into the launcher; widget ids returned by other apps are not trusted.
- **Hidden apps stay hidden**: pinning an app no longer brings hidden apps back into the drawer and search.
- **Icon-pack requests verified**: a fake "apply icon pack" request from another app is ignored; the package name is read from the system.
- **Theme and backup import limited**: an imported theme can only change colours; a broken theme or backup no longer crashes the launcher at start.
- **Coarse weather location**: about 1 km precision, precise-location permission removed, coordinates never logged.
- **Private crash log**: kept in the app's own storage instead of the shared Download folder, without the names of opened apps.
- **No cloud backup of launcher data**: notes stay out of screenshots, the contact cache is deleted when the permission is withdrawn, e-mail addresses are not read.
- **Pinned toolchain**: stable Kotlin release, no development repositories, checksum-pinned Gradle download.
- **Nothing typed leaves the device**: forwarding unmatched searches to the Play Store or a web search engine, the internet search button and the search-engine setting are removed.

### 4.2 Bug fixes

- **Stuck drawer**: the drawer no longer becomes permanently unopenable after two quick swipes.
- **Drawer closes only when pulled down**: no more open-and-close on repeated swipes.
- **Alias on home**: a renamed app shows its alias on the home screen in every mode (also sent upstream as #1115).
- **Slow swipes**: slow gestures are detected correctly.
- **Instant tap**: home-screen apps open on tap; the 0.3-second wait is gone.
- **Follow-system theme**: works when dark mode is scheduled or automatic.
- **Hidden apps and recents**: hidden apps no longer show in the drawer or search when "Show recent apps" is on.
- **Turkish translation**: 106 strings corrected.
- **Settings survive updates**: an automatic copy of all settings before every update (last five kept), restorable from Settings → Backup/Restore.
- **Widget geometry in grid cells**: no drifting on open, no breaking on rotation, no stale sizes.
- **Widgets stay on the page**: no overlap, no leaving the page; a widget dropped on taken cells returns, a full page warns.
- **Widget default size**: new widgets take the size their app asks for, learn their real size correctly, and can be made smaller than the default.
- **Widget crashes and glitches**: the "Remove all widgets" crash, the page restart after resize mode and the menu opening during a slow drag are fixed.
- **Never-opened apps**: adding a widget of an app that has never run explains why it may stay empty and offers to open the app.
- **Widget picker and host id**: every widget is listed, configure screens open the way Android intends, the host id no longer depends on the app name.
- **Category view search**: refreshing the list no longer replaces search results with the full list.

### 4.3 Interface

- **Smart Search**: independent of name length; matches word beginnings and single-letter typos; switching it off restores the old search.
- **Larger search field**: a bigger search area in the drawer with a gap before the list.
- **Category view**: pinned apps on top, the rest in categories with one open at a time; off brings back the A–Z list.
- **Automatic categories**: from the app's tag, otherwise from the category the app declares to Android; the rest go to "Other".
- **Category header settings**: size, colour, capitalisation, app count, search-field height and spacing are adjustable.
- **Word of the Day removed**: with all its parts.
- **New logo**: white on black, set in Cormorant Garamond (open licence); light and dark variants, adaptive icon, toast, splash and "Launcher Dots" use it.
- **Widget editing view**: thin outline, small handles, live size label, dotted grid, dashed landing preview.
- **Launcher in its own list**: tapping it opens the settings.
- **About screen cleaned**: donation, Discord and "Share Application" links removed.
- **Selection lists instead of toggles**: drawer view (A–Z / Categories) and search scope.
- **Staged on-device search**: apps, then files, then contacts; a missing permission appears as a one-tap row in the list.
- **Manage Categories screen**: rename, create with chosen apps, set the apps kept above the categories and the maximum number of categories.
- **Settings redesigned**: two columns of thin-outlined cards, a fixed "name · version · About" line at the bottom; sections on sub-screens in cards, expert options behind a switch.

## 5. Names

The fork was first built as **Panther Launcher** (5 October 2026, package `app.pantherlauncher`) for
my own phone only, with no intention of distributing it. In October 2026 it became **REstart**
(written RE\*start), a REMAKER project: developed in the open under GPL-3.0, free of charge, with
the source on GitHub under [reporemaker](https://github.com/reporemaker). Neither the licence nor
the names above change what this file records: the foundation is theirs; from here on, the
responsibility is mine.

---

This file lists the fork differences up to 24 October 2026 and is not updated afterwards.
Later development can be followed in the release notes and the git history.

24 October 2026
SEU//Emek Ulas
