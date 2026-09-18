# Wanda

Android music player unifying **Navidrome/Subsonic**, **local files**, **YouTube Music**, and **Internet Archive**. Material 3 Expressive throughout. Battery-first, privacy-first, zero telemetry.

## 1. Non-Negotiable Hard Rules

| Rule | Why |
| --- | --- |
| **300 lines max per file** | Split when file reaches 250 lines; 1 concept per file. |
| **Room is offline source of truth** | Network results are persisted, then read back as Flows. |
| **Media3 owns playback state** | `PlaybackService` owns `ExoPlayer`; UI uses `MediaController`. |
| **`IMusicSource` single abstraction** | Backends declare `SourceCapabilities`; UI checks flags, never fakes. |
| **Explicit window insets** | Edge-to-edge layout requires manual container-edge padding. |
| **Zero secrets in plaintext** | Store only in `SecureStorage`; never log URLs, tokens, or streams. |
| **Strings in `strings.xml`** | Pass `@StringRes Int`, not `String`; never hardcode user text in UI. |
| **No dead code & no fake fallbacks** | Surface explicit errors; never swallow exceptions with generic catch. |
| **Research before implementation** | Follow mandatory 4-step research workflow before adding dependencies. |
| **No AI attribution in git** | Comply with `CLA.md` Section 8; never add `Co-Authored-By` AI tags. |

## 2. Documentation Directory Map

Detailed developer guides and architectural specifications in `docs/dev/`:

| Topic | Pointer / Specification |
| --- | --- |
| **Coding Style & Conventions** | [`docs/dev/process/coding-style.md`](docs/dev/process/coding-style.md) |
| **Mandatory Research Workflow** | [`docs/dev/process/research-workflow.md`](docs/dev/process/research-workflow.md) |
| **Git & Authorship Policy** | [`docs/dev/process/git-and-authorship.md`](docs/dev/process/git-and-authorship.md) |
| **Window Insets & Safe Zones** | [`docs/dev/android/window-insets.md`](docs/dev/android/window-insets.md) |
| **Material 3 Expressive & Motion** | [`docs/dev/android/motion-m3.md`](docs/dev/android/motion-m3.md) |
| **Localization & Crowdin** | [`docs/dev/android/localization.md`](docs/dev/android/localization.md) |
| **Architecture, Battery & Security** | [`docs/dev/architecture/overview.md`](docs/dev/architecture/overview.md) |
| **Commands & Testing** | [`docs/dev/tools/commands-and-testing.md`](docs/dev/tools/commands-and-testing.md) |

## 3. Quick Commands

```bash
./gradlew :app:assembleDebug          # Build debug APK
./gradlew :app:testDebugUnitTest      # Run local unit tests
./gradlew :app:lintDebug              # Run lint checks
```
