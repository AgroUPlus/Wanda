# Wanda

An all-in-one Android music player unifying **Navidrome/Subsonic**, **local files**, **YouTube Music**
and **Internet Archive** behind one library, one queue and one player.
Material 3 Expressive throughout. Battery-first, privacy-first, no telemetry, no accounts of our own.

Four screens: **Home · Library · Search · Settings**, plus a Now Playing destination.

## Architecture

Single Gradle module `:app`, package root `com.wander.android`.

```
core/       audio (Media3), cache, database (Room), network (Ktor/OkHttp), security, permissions
data/       model · sources/<name> (one per backend) · repository
di/         Hilt modules, one per concern
ui/         theme · navigation · components · screens/<screen>
```

Rules of the road:

- `IMusicSource` is the **only** source abstraction. Adding a backend means adding one package
  under `data/sources/` and one `@IntoSet` binding — nothing else changes.
- Every source declares a `SourceCapabilities`. The UI reads capabilities to hide or disable
  actions. A source never fakes a feature it lacks.
- ViewModels talk to **repositories only**, never to sources or DAOs directly.
- **Room is the offline source of truth.** Network results are persisted, then read back as Flows.
- **Media3 owns playback state.** `PlaybackService` owns the `ExoPlayer`; the UI holds a
  `MediaController`. Nothing else constructs a player.

## Coding style

- **Hard cap 300 lines per file.** Split when a file passes 250. One concept per file, named for it —
  no `Components.kt` / `Entities.kt` / `Utils.kt` grab-bags.
- **No speculative fallbacks.** If something is unsupported or fails, return empty/`Result.failure`
  and surface it. Never invent placeholder data, never swallow with a blanket `catch (e: Exception)`
  that hides the cause.
- **No dead code.** If it has no caller, it does not get written.
- UI = stateless composables driven by a `StateFlow<UiState>`. No side effects in composition.
  Every `LazyColumn`/`LazyRow` item has a stable `key`.
- Coroutines + Flow only. `Dispatchers.IO` is applied at the repository/source boundary,
  never inside a composable or a ViewModel body.
- Default to `internal`. `public` only for genuine cross-package API.
- Prefer immutable `data class` state; `@Immutable`/`@Stable` where it helps recomposition.

## Motion (Material 3 Expressive)

- Use `MaterialExpressiveTheme` + `MotionScheme.expressive()`. Take spring specs from
  `MaterialTheme.motionScheme` — do not hand-roll `spring()` values in screens.
- Shared-element transitions for mini-player → Now Playing. Predictive back everywhere.
- Prefer expressive components (`ShortNavigationBar`, wavy progress, `FloatingToolbar`,
  `LoadingIndicator`, `MaterialShapes` morphs) over stable equivalents.

## Battery

- No polling loops. Position updates come from `Player.Listener` + a ticker that runs **only**
  while playing **and** while the UI is `STARTED` (`repeatOnLifecycle`).
- Audio offload enabled; `WAKE_MODE_NETWORK` only while streaming, `WAKE_MODE_NONE` for local.
- Visualizer FFT subscribes only while Now Playing is on screen.
- Background work is WorkManager with `UNMETERED + charging + !battery-low` constraints.

## Security

- Secrets live only in `EncryptedSharedPreferences` (`SecureStorage`). Never in Room, logs or prefs.
- **Never log** URLs, tokens, cookies, passwords or stream links — they carry credentials.
- `allowBackup=false`, `usesCleartextTraffic=false` (per-domain opt-in for self-hosted Navidrome).
- No analytics, no crash reporting, no third-party SDK that phones home.
- Incognito mode suppresses scrobbles and play-count writes.

## Commands

```bash
./gradlew :app:assembleDebug          # build
./gradlew :app:testDebugUnitTest      # unit tests
./gradlew :app:lintDebug              # lint
./gradlew :app:installDebug           # install on connected device
```

JDK 17 and Android SDK 36 are required (`org.gradle.java.home` is set in `gradle.properties`).
