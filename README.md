<p align="center">
  <img src="docs/vmpro-icon.png" width="120" alt="VMPro icon">
</p>

<h1 align="center">VMPro</h1>

A lightweight Android app that surfaces **publicly available** release downloads from
GitHub and lets you grab them on-device. Built from scratch in Kotlin + Jetpack Compose —
original code, not derived from any existing manager app.

App name **VMPro** · versionName **4.1** · website **https://vancedmanager.com**

> We build this manager from scratch, we just add publicly available links from GitHub
> users: **[`J-hc`](https://github.com/j-hc/revanced-magisk-module), [`ReVanced`](https://github.com/ReVanced), & [`Morphe`](https://github.com/MorpheApp)**.

## What it does

Three tabs, all fetched live from the GitHub REST API (no token needed):

- **Apps** — installable APKs, grouped by patch project:
  - **Morphe**: Twitter*, YouTube, YT Music, Reddit
  - **ReVanced**: YouTube, YT Music
- **MicroG** — the GmsCore backend needed for non-root builds:
  - **GmsCore** — [`ReVanced/GmsCore`](https://github.com/ReVanced/GmsCore/releases)
  - **MicroG RE** — [`MorpheApp/MicroG-RE`](https://github.com/MorpheApp/MicroG-RE/releases)
- **Modules** — the same app list as *Apps* but the Magisk/KernelSU `.zip` modules.

\* Twitter (piko) is only published as a module `.zip`, so it appears under *Modules* and is
marked **N/A** under *Apps* — the manager never fabricates a file that doesn't exist upstream.

The app never hosts, builds, or modifies any file — every download comes straight from the
official GitHub release pages.

## Project layout

```
app/src/main/java/com/vmpro/app/
  VmproApp.kt                  # Application: analytics init
  MainActivity.kt              # Compose UI: tabs, sections, app rows, download button
  analytics/Analytics.kt       # Aptabase event wrapper (privacy-first)
  data/Model.kt                # Release / Asset models, version compare, byte formatting
  data/Source.kt               # Source repos (used by the About page)
  data/Catalog.kt              # Curated app list + asset-name matching rules
  data/GithubRepository.kt     # OkHttp fetch + latest-asset resolution
  ui/ManagerViewModel.kt       # Per-tab state, catalog resolution, download/install actions
  ui/Theme.kt                  # Material 3 theme (blue)
  ui/AboutScreen.kt            # About / credits page
  util/DownloadController.kt   # DownloadManager + completion tracking
  util/Downloader.kt           # Open URL + uninstall (PackageInstaller)
  util/UninstallReceiver.kt    # Receives uninstall result -> confirm dialog
```

## Building

Requirements: JDK 17 and the Android SDK (build-tools 34, platform android-34).

```bash
./gradlew assembleDebug      # debug build
./gradlew assembleRelease    # signed, R8-optimized release build (~2 MB)
```

Output: `app/build/outputs/apk/{debug,release}/`.

- **minSdk** 24 · **targetSdk** 34 · **Gradle** 8.9 · **AGP** 8.5.2 · **Kotlin** 1.9.24
- Release builds run R8 (code shrink/obfuscate) + resource shrinking.

### Signing

Release signing is read from `keystore.properties` at the project root (gitignored — never
commit it). Format:

```properties
storeFile=keystore/vmpro-release.jks
storePassword=********
keyAlias=vmpro
keyPassword=********
```

If `keystore.properties` is absent, the release build is produced unsigned (so CI without
the key still compiles).

### Analytics key

Analytics (Aptabase) is injected at build time from `local.properties` (gitignored):

```properties
aptabase.key=A-XX-XXXXXXXXXX
```

Leave it blank to disable analytics entirely — the app runs normally either way. Get a key
free at https://aptabase.com (or self-host).

## Install on a device

Copy the APK to your phone and open it (enable "install unknown apps" for your file
manager), or via ADB:

```bash
adb install -r app/build/outputs/apk/release/vmpro-4.1.apk
```
