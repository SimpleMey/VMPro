<p align="center">
  <img src="docs/vmpro-icon.png" width="120" alt="VMPro icon">
</p>

<h1 align="center">VMPro</h1>

A lightweight Android app that surfaces **publicly available** release downloads from
GitHub and lets you grab and install them on-device. Built from scratch in Kotlin +
Jetpack Compose, original code, not derived from any existing manager app.

App name **VMPro** · versionName **4.5.3** · website **https://vmpro.app**

> We build this manager from scratch; we only add publicly available links from GitHub
> projects: **[`J-hc`](https://github.com/j-hc/revanced-magisk-module),
> [`ReVanced`](https://github.com/ReVanced), [`Morphe`](https://github.com/MorpheApp),
> [`Anddea`](https://github.com/anddea), & [`piko`](https://github.com/crimera/piko)**.
> The patched APKs are produced by an auto-build pipeline,
> [`Morphe-Builds`](https://github.com/SimpleMey/Morphe-Builds).

## What it does

A **Phone** section and a **TV** section (bottom navigation). The Phone section has three
tabs you can swipe between, all fetched live from the GitHub REST API (no token needed):

- **Apps** — installable APKs, each with its own install page (version, size, sources):
  - **Morphe**: YT Morphe, Morphe Music, Reddit
  - **Anddea (RVX)**: RVX App, RVX Music
  - **piko**: Twitter (X)
  - **NewTube** — from its own GitHub release
- **MicroG** — the GmsCore backend needed for non-root builds:
  - **GmsCore** — [`ReVanced/GmsCore`](https://github.com/ReVanced/GmsCore/releases) (installs the Huawei HMS build on Huawei devices)
  - **MicroG RE** — [`MorpheApp/MicroG-RE`](https://github.com/MorpheApp/MicroG-RE/releases)
- **Modules** — the Magisk / KernelSU `.zip` modules from
  [`j-hc/revanced-magisk-module`](https://github.com/j-hc/revanced-magisk-module), to flash
  in Magisk or KernelSU.

The **TV** section lists Android TV apps with D-pad navigation:

- **SmartTube** — [`yuliskov/SmartTube`](https://github.com/yuliskov/SmartTube)
- **TizenTube (Cobalt)** — [`reisxd/TizenTubeCobalt`](https://github.com/reisxd/TizenTubeCobalt)

### Highlights

- **Automatic architecture** — detects the device CPU (arm64-v8a, armeabi-v7a, x86) and
  downloads the matching build for apps, MicroG, and TV apps, so you never pick the wrong one.
- **Per-app install pages** — tap a row to open a page with the install button, version and
  size, and a Sources section crediting the build pipeline and the patch authors.
- **Install state** — shows Installed / Open / Update per app, including patch-only updates
  (same base app version, newer patches).
- **Settings** — Downloads (see and delete what you downloaded), a dark-mode switch,
  My device (manufacturer, architecture, storage, Android version), an FAQ, and About.

The app never hosts, builds, or modifies any file. Every download comes straight from a
public GitHub release.

## Project layout

```
app/src/main/java/com/vmpro/app/
  VmproApp.kt                  # Application: analytics init
  MainActivity.kt              # Compose UI: Phone/TV nav, swipeable tabs, rows, install pages
  analytics/Analytics.kt       # PostHog event wrapper (debug-guarded)
  data/Model.kt                # Release / Asset models, version compare, byte formatting
  data/Source.kt               # Source repos (shown on install pages / About)
  data/Catalog.kt              # Curated app list + device-arch matching rules
  data/GithubRepository.kt     # OkHttp fetch + latest / arch-aware asset resolution
  data/InstalledPatchStore.kt  # Tracks installed base + patch version (patch-only updates)
  ui/ManagerViewModel.kt       # Per-tab state, catalog resolution, download/install actions
  ui/SettingsScreen.kt         # Settings: Downloads, My device, FAQ, About (all inline)
  ui/SharePrompt.kt            # Share-the-app prompt
  ui/Theme.kt                  # Material 3 theme (blue)
  util/DownloadController.kt   # DownloadManager + progress / completion tracking
  util/Downloads.kt            # Enumerate / delete downloaded files
  util/Downloader.kt           # Open URL, share APK, uninstall (PackageInstaller)
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

Analytics (PostHog) is injected at build time from `local.properties` (gitignored):

```properties
posthog.key=phc_XXXXXXXXXXXXXXXX
posthog.host=https://us.i.posthog.com   # or eu.i.posthog.com / self-hosted
```

Leave the key blank to disable analytics entirely — the app runs normally either way.
Analytics is also disabled automatically in debug builds. Get a key free at
https://posthog.com (or self-host).

## Install on a device

Copy the APK to your phone and open it (enable "install unknown apps" for your file
manager), or via ADB:

```bash
adb install -r app/build/outputs/apk/release/vmpro-4.5.3.apk
```
