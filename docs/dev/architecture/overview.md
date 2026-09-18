# Wanda Architecture Overview

Single Gradle module `:app`, package root `com.wander.android`.

## 1. Package Structure

```
core/       audio (Media3), cache, database (Room), network (Ktor/OkHttp), security, permissions
data/       model · sources/<name> (one per backend) · repository
di/         Hilt modules, one per concern
ui/         theme · navigation · components · screens/<screen>
```

## 2. Core Architectural Invariants

- **`IMusicSource` single abstraction**: Adding a music source means adding a package under `data/sources/` and an `@IntoSet` Hilt binding.
- **Capabilities-driven UI**: Every source declares a `SourceCapabilities`. The UI checks capabilities to show, hide, or disable actions. Sources never fake capabilities.
- **Offline truth in Room**: Remote network responses are persisted into SQLite/Room first, then observed by UI layers as Flows.
- **Media3 owns playback**: `PlaybackService` exclusively instantiates and controls the `ExoPlayer` instance. The UI communicates through a `MediaController`.
- **Repository barrier**: ViewModels communicate only with repositories, never directly with DAOs or backend sources.

## 3. Battery & Hardware Constraints

- No polling loops. Playback position updates are driven by `Player.Listener` combined with a tick timer active only while playing and when the UI lifecycle is `STARTED` (`repeatOnLifecycle`).
- Hardware audio offload is enabled. `WAKE_MODE_NETWORK` is acquired only during active network streaming (`WAKE_MODE_NONE` for local file playback).
- Background jobs run via WorkManager constrained by `UNMETERED + charging + !battery-low`.

## 4. Security & Privacy

- Secrets (tokens, passwords, API keys) live exclusively in `EncryptedSharedPreferences` (`SecureStorage`). Never store secrets in Room, logs, or unencrypted preferences.
- Never log URLs, auth tokens, session cookies, passwords, or stream URLs.
- App flags: `allowBackup = false`, `usesCleartextTraffic = false` (domain opt-in for self-hosted Navidrome).
- Zero third-party telemetry, crash reporting, or analytics SDKs.
- Incognito mode completely suppresses scrobbles and play-count increments.
