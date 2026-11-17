# TuxunDL for Windows

The Windows edition of TuxunDL, developed in the open in this same
repository under the same GPLv3 license as the Android edition.

- Package identity: `com.tuxundl.windows`
- Versioning restarts at 1.0.0 and is independent of the Android app
- Releases are tagged `win-v<version>` (Android uses plain `v<version>`)

## Stack

- Kotlin + Compose Multiplatform Desktop, the same language and UI
  toolkit family as the Android app, so design, mascot and interaction
  patterns carry over instead of being re-invented in another stack.
- The proxy core is mihomo, the same core the Android edition is built
  on. On Windows it ships as a bundled `mihomo.exe` sidecar that the app
  drives over the core's loopback REST API.
- Both a system-proxy mode (no elevation) and a TUN mode (elevated core,
  `wintun.dll` bundled) are in scope for 1.0.0.

## Layout

- `settings.gradle.kts` / `build.gradle.kts` — a standalone Gradle
  build. It is intentionally not a subproject of the Android root build,
  whose `subprojects` block applies an Android plugin to every module.
  Neither build can break the other.
- `src/main/kotlin/com/tuxundl/windows/Main.kt` — the window, sidebar,
  home page with the power button and the window-pane mascot.
- `src/main/kotlin/com/tuxundl/windows/CoreBridge.kt` — the seam
  between the UI and the mihomo sidecar process.

## Build

From the repository root:

```text
gradlew -p desktop run
gradlew -p desktop packageMsi
gradlew -p desktop packageExe
```

Packaging runs on a Windows runner in GitHub Actions, like every other
build in this project. Installers are published on the `win-v*` GitHub
release with full release notes, next to the Android releases.

## What is ours, and what is upstream

Everything in this `desktop/` folder — the interface, the core bridge,
subscriptions, system proxy handling, the tray — is original code
written for TuxunDL in this repository. The Windows edition does not
copy or port any other Windows client.

The traffic engine is not ours: the app drives the open-source
[mihomo](https://github.com/MetaCubeX/mihomo) core (GPLv3), the same
core the Android edition builds on, bundled at build time by the
`fetchCore` task in `build.gradle.kts` and never committed to this
repository. TUN mode uses `wintun.dll`, © WireGuard LLC, distributed
under the Wintun prebuilt binaries license (see wintun.net). Because
the core is GPLv3, this project stays GPLv3 and fully open source.
