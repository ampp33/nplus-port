# N+ Port

A Kotlin/LibGDX port of **N** — the minimalist ninja platformer originally created by Metanet Software. Play as a nimble ninja, survive lethal traps, and collect gold across hundreds of handcrafted levels.

Original game: [https://www.thewayoftheninja.org/](https://www.thewayoftheninja.org/)

---

## Playing the Game

Pre-built binaries are attached to every [GitHub Release](../../releases/latest). Download the file for your platform and follow the steps below.

### Windows

1. Download `nplus-<version>.exe` from the release.
2. Run the installer — no admin rights required for a user-only install.
3. Launch **nplus** from the Start menu or the install folder.

### macOS

1. Download `nplus-<version>.dmg` from the release.
2. Open the DMG and drag **nplus** to your Applications folder.
3. On first launch, right-click → **Open** to bypass Gatekeeper.

### Linux

1. Download `nplus_<version>-1_amd64.deb` from the release.
2. Install it:
   ```bash
   sudo dpkg -i nplus_*.deb
   # or
   sudo apt install ./nplus_*.deb
   ```
3. Run `nplus` from a terminal or your application launcher.

### Android

1. Open **Settings → Security** (or **Apps → Special app access**) and enable **Install unknown apps** for your browser or file manager.
2. Download `nplus-android.apk` from the release directly to your device.
3. Tap the downloaded APK and follow the install prompt.

Requires Android 8.0 (API 26) or later.

---

## Development Setup

### Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| JDK | 17 | `JAVA_HOME` must point to a JDK 17 installation |
| Android SDK | API 34, build-tools 34.0.0 | Path set in `local.properties` |
| Android Studio / IntelliJ IDEA | Latest | Recommended IDE |
| Gradle wrapper | 8.7 | Use `./gradlew` — no separate Gradle install needed |

### Clone and configure

```bash
git clone https://github.com/ampp33/nplus-port.git
cd nplus-port
```

Create `local.properties` at the project root (this file is not committed):

```properties
sdk.dir=/path/to/your/android-sdk
```

Common SDK locations:

| OS | Default path |
|----|-------------|
| Linux | `~/Android/Sdk` |
| macOS | `~/Library/Android/sdk` |
| Windows | `%LOCALAPPDATA%\Android\Sdk` |

### Open in your IDE

- **Android Studio**: File → Open → select the `nplus-port` folder.
- **IntelliJ IDEA**: same — the project is a standard Gradle multi-module project.

Accept any SDK/JDK prompts and let Gradle sync complete.

---

## Project Structure

```
nplus-port/
├── core/        # Game logic, physics, renderer — shared by all targets
├── android/     # Android launcher + AGP build config
├── desktop/     # Desktop LWJGL3 launcher
└── assets/      # Shared sprites, levels, sounds — referenced by both targets
```

---

## Gradle Tasks

Run all tasks from the project root with `./gradlew` (Linux/macOS) or `gradlew.bat` (Windows).

### Desktop

| Task | Description |
|------|-------------|
| `./gradlew :desktop:run` | Run the game directly (assets/ is the working directory) |
| `./gradlew :desktop:jar` | Build a self-contained fat JAR with all dependencies and assets bundled |
| `./gradlew :desktop:build` | Compile, test, and assemble the desktop module |
| `./gradlew :desktop:packAtlas` | Re-pack sprite sheets into `assets/atlas/sprites.atlas` using the LibGDX TexturePacker |

The fat JAR is written to `desktop/build/libs/desktop.jar`. You can run it on any platform that has Java 17+:

```bash
java -jar desktop/build/libs/desktop.jar
```

### Android

| Task | Description |
|------|-------------|
| `./gradlew :android:assembleDebug` | Build a debug APK signed with the local debug key |
| `./gradlew :android:assembleRelease` | Build an unsigned release APK |
| `./gradlew :android:build` | Compile, lint, and assemble the Android module |
| `./gradlew :android:copyAndroidNatives` | Extract `.so` native libraries from LibGDX JARs into `android/libs/<abi>/` (runs automatically before every build) |

APK output locations:

```
android/build/outputs/apk/debug/android-debug.apk
android/build/outputs/apk/release/android-release-unsigned.apk
```

Install a debug APK to a connected device or emulator:

```bash
adb install -r android/build/outputs/apk/debug/android-debug.apk
```

### Core library

| Task | Description |
|------|-------------|
| `./gradlew :core:test` | Run all unit tests (physics, collision, math, input, level parsing) |
| `./gradlew :core:build` | Compile and test the shared game logic |

### Useful root-level tasks

| Task | Description |
|------|-------------|
| `./gradlew build` | Build all modules |
| `./gradlew test` | Run tests across all modules |
| `./gradlew tasks` | List every available task with descriptions |

---

## Tech Stack

| Component | Library / Tool | Version |
|-----------|---------------|---------|
| Game framework | [LibGDX](https://libgdx.com/) | 1.12.1 |
| Gamepad support | gdx-controllers | 2.2.1 |
| Language | Kotlin | 1.9.23 |
| JVM target | Java 17 | — |
| Desktop backend | LWJGL3 | via LibGDX |
| Android Gradle Plugin | AGP | 8.3.2 |
| Android min SDK | — | 26 (Android 8.0) |
| Android target / compile SDK | — | 34 |
| Build system | Gradle wrapper | 8.7 |
| Test framework | Kotest + JUnit 5 | 5.8.1 |

---

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| `sdk.dir` not found | Create or update `local.properties` with the correct Android SDK path |
| `JAVA_HOME` error | Set `JAVA_HOME` to a JDK **17** directory (not JRE, not JDK 21) |
| `Unable to strip libgdx.so` | Warning only — packaging still succeeds; strip tools are not in PATH |
| Android Manifest not found | Do not move `android/AndroidManifest.xml`; its location is declared in `android/build.gradle.kts` |
| Desktop window doesn't open | Make sure you run `:desktop:run`, not `:desktop:jar`; the JAR task produces the binary but does not launch it |
