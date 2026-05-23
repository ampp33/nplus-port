# N+ Port — Build Guide

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| JDK | 17 | `JAVA_HOME` must point to a JDK 17 install |
| Android SDK | platform 34, build-tools 34.0.0 | path set in `local.properties` |
| Gradle wrapper | 8.7 | use `./gradlew`, no separate install needed |

**`local.properties`** (not committed) must exist at the project root:

```
sdk.dir=/home/ampp33/android-sdk
```

## Project structure

```
nplus-port/
├── core/        # Game logic, physics, renderer — shared by all targets
├── android/     # Android launcher + AGP build
├── desktop/     # Desktop LWJGL3 launcher
└── assets/      # Shared assets (levels, sounds) — referenced by both targets
```

## Desktop build & run

```bash
# Run from project root (assets/ is the working dir, set in desktop/build.gradle.kts)
./gradlew :desktop:run

# Build a fat jar
./gradlew :desktop:jar
java -jar desktop/build/libs/desktop.jar
```

## Android APK

```bash
# Debug APK
./gradlew :android:assembleDebug
# Output: android/build/outputs/apk/debug/android-debug.apk

# Release APK (unsigned — sign separately before distributing)
./gradlew :android:assembleRelease
```

Install to a connected device / emulator:

```bash
adb install -r android/build/outputs/apk/debug/android-debug.apk
```

### Native libs

`copyAndroidNatives` (runs automatically before `preBuild`) extracts `.so` files from
libGDX platform JARs into `android/libs/<abi>/`. ABIs bundled: `armeabi-v7a`, `arm64-v8a`,
`x86`, `x86_64`.

## Key dependency versions

| Library | Version |
|---------|---------|
| libGDX | 1.12.1 |
| gdx-controllers | 2.2.1 |
| AGP (Android Gradle Plugin) | 8.3.2 |
| Kotlin | 1.9.23 |
| minSdk | 26 (Android 8.0) |
| targetSdk / compileSdk | 34 |

## Running tests

```bash
./gradlew :core:test
```

Tests live in `core/src/test/kotlin/` and cover physics, collision, math, input, and level parsing.

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| `sdk.dir` not found | Create/update `local.properties` with the correct Android SDK path |
| `JAVA_HOME` error during build | Point `JAVA_HOME` to a JDK 17 directory that contains `bin/java` |
| `Unable to strip libgdx.so` | Warning only — packaging continues; strip tools not in PATH |
| Manifest not found | `sourceSets["main"].manifest.srcFile("AndroidManifest.xml")` is set in `android/build.gradle.kts`; do not move the manifest to `src/main/` |
