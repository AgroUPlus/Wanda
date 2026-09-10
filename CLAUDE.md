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

## Window insets (safe zones)

The app is edge-to-edge (`enableEdgeToEdge()` in `MainActivity`, no `WindowInsetsController` calls
anywhere). Nothing is inset for you. Two bugs have shipped from forgetting this — a share button in
the status bar, a back arrow behind the clock — so:

- **Any composable aligned to a container edge must account for the insets itself**, unless a parent
  demonstrably already has. `Modifier.align(...)` + `padding(n.dp)` inside a full-bleed `Box` is the
  shape this bug takes every time.
- **Put the inset in the shared component, not in each caller.** `ImmersiveHero` insets its own
  `overlay` slot; `PlayerOverlayButtons` takes measured insets from its caller. A rule each new
  caller has to remember is a rule that gets forgotten.
- Prefer `windowInsetsPadding(WindowInsets.safeDrawing.only(...))` over `safeDrawingPadding()` when
  only some edges matter. A hero at the top of a page has no business insetting its bottom.
- **Never clear a sibling by a hard-coded height.** Two composables aligned to opposite edges of a
  `Box` have no layout relationship, so a constant written from the paddings a layout is declared
  with will be wrong — the real height includes the system bar inset too. Measure with
  `onGloballyPositioned` at the *head* of the modifier chain (so the figure includes the padding
  inside it) and pass that. This is exactly how the lyrics toggle landed on the like button.
- When you add a control to a full-bleed layout, check it against a gesture-nav device *and* a
  three-button one — the bottom inset differs by ~30dp and only one of them will look right by luck.

## Battery

- No polling loops. Position updates come from `Player.Listener` + a ticker that runs **only**
  while playing **and** while the UI is `STARTED` (`repeatOnLifecycle`).
- Audio offload enabled; `WAKE_MODE_NETWORK` only while streaming, `WAKE_MODE_NONE` for local.
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

Research Before Implementation (MANDATORY)

Before implementing code, researching code, or answering technical questions, the AI agent MUST follow this research workflow:
Step 1: Look up official documentation

    Use MCP Context7 (resolve-library-id → query-docs) to fetch up-to-date documentation for any library/framework about to be used
    Understand the latest API surface, breaking changes, and recommended usage patterns

Step 2: Evaluate pros, cons, and alternatives

    Use WebSearch to research:
        Pros and cons of the library/approach
        Alternative libraries or approaches that solve the same problem
        Known issues, performance concerns, or deprecation notices
    Compare and evaluate whether the chosen library/approach is the best fit for this project

Step 3: Study OSS best practices

    Use Grep (on GitHub via web search) or WebSearch to find how well-known open-source projects implement similar features
    Verify the approach follows established best practices before adopting it
    Pay attention to patterns used in projects with similar architecture (Clean Architecture, Compose Multiplatform, etc.)

Step 4: Make a decision and justify

    Only proceed with implementation after completing steps 1-3
    If a library/approach has significant drawbacks or better alternatives exist, recommend the better option to the user before proceeding
    Document the rationale briefly when introducing new dependencies or patterns

This workflow applies to: Adding new libraries, choosing architectural patterns, implementing new features with unfamiliar APIs, answering "how should we do X?" questions, and evaluating technical approaches.

This workflow does NOT apply to: Simple bug fixes in existing code, minor refactoring, or tasks using libraries already well-established in the project.
Verification After Code Changes

    Do NOT build the app to verify code changes. Instead, use JetBrains MCP tools (get_file_problems, getDiagnostics) to check for compile errors and warnings in real-time.
    Only run Gradle build when explicitly requested by the user or for final release verification and never do so inside wsl.

Testing

    Unit tests for Domain layer (Use cases)
    Repository tests with fake data sources
    UI tests with Compose Testing

JDK 17 and Android SDK 36 are required (`org.gradle.java.home` is set in `gradle.properties`).
