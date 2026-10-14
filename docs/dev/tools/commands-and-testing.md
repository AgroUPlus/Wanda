# Build Commands & Testing Guidelines

## 1. Essential Gradle Commands

```bash
./gradlew :app:assembleDebug          # Build debug APK
./gradlew :app:testDebugUnitTest      # Run local unit tests
./gradlew :app:lintDebug              # Run Android Lint checks
./gradlew :app:installDebug           # Install onto connected adb device
```

## 2. Style Gate (ktlint)

ktlint runs as a standalone binary in CI, so it does not depend on the Kotlin or AGP versions.
Rules live in `.editorconfig`; existing violations are frozen in `ktlint-baseline.xml`, so only new
ones fail the build.

```bash
ktlint --baseline=ktlint-baseline.xml --relative "app/src/**/*.kt"            # check
ktlint --baseline=ktlint-baseline.xml --relative -F "app/src/**/*.kt"         # auto-fix
```

Shrink the baseline by fixing a rule's violations and regenerating it with `--baseline=...` on a
clean run (delete `ktlint-baseline.xml` first, since an existing file is only read, never rewritten).

## Static Analysis (detekt)

```bash
./gradlew :app:detekt            # fails on findings not in app/detekt-baseline.xml
./gradlew :app:detektBaseline    # re-freeze after fixing findings
```

detekt 2.0 is still alpha (`dev.detekt`). alpha.3 is pinned because it is the last one built against
Kotlin 2.3.x; move to a newer alpha only together with a Kotlin upgrade.

## Coverage Floor (Kover)

```bash
./gradlew :app:koverHtmlReportDebug   # app/build/reports/kover/htmlDebug/index.html
./gradlew :app:koverVerifyDebug       # fails below the floor in app/build.gradle.kts
```

Line coverage was 5.3% when the floor was set to 5. Raise `minBound` whenever coverage rises, never lower it.

## Startup Profile

`app/src/main/baseline-prof.txt` is the recorded cold-start profile (27k rules, source names); it is
compiled into the ART profile for release-like builds (`:app:compileFastDebugArtProfile` checks it)
and installed at runtime by `profileinstaller`.

`:baselineprofile` is a self-instrumenting macrobenchmark module. It needs a device or emulator on
API 28+. Record against the `nonMinified` build, never `fastDebug` or `release`: a minified build
records R8's renamed classes (`La0;`), which match nothing.

```bash
# Re-record (an emulator is fine; allow ~5 minutes):
gradlew.bat :baselineprofile:connectedNonMinifiedAndroidTest ^
  -Pandroid.testInstrumentationRunnerArguments.class=com.wander.android.baselineprofile.BaselineProfileGenerator ^
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR
# Then copy baselineprofile/build/outputs/connected_android_test_additional_output/nonMinified/connected/<device>/BaselineProfileGenerator_generate-startup-prof.txt
# to app/src/main/baseline-prof.txt

# Timing (physical phone only; an emulator's numbers are meaningless):
gradlew.bat :baselineprofile:connectedFastDebugAndroidTest
```

The Baseline Profile Gradle plugin is not used: its generated variants fail under this project's
pre-AGP-9 KSP setup (`kspBenchmarkReleaseKotlin`). The profile covers only the first screen so far;
add journeys (search, playback, the queue) to `BaselineProfileGenerator` and re-record.

## 3. Agro Contract

`app/src/test/resources/agro-schema.graphql` is a copy of Agro's `docs/api/schema.graphql`, and
`AgroSchemaContractTest` fails when Wanda sends an operation the server's schema no longer has.
After an Agro API change, refresh the copy:

```bash
cp ../Agro/docs/api/schema.graphql app/src/test/resources/agro-schema.graphql
```

## 4. Testing Layers

- **Domain Layer**: Unit tests for UseCases and pure domain logic.
- **Data Layer**: Repository tests with fake data sources and in-memory Room instances.
- **UI Layer**: Compose UI tests (`createComposeRule`).

## 5. Verification Guidelines for AI Agents

- **Do not build the app to verify simple code changes.** Use language server diagnostics or real-time IDE syntax checks.
- Only run full Gradle builds when explicitly requested by the user or when verifying final releases.
- Never run heavy Android Gradle builds inside resource-constrained WSL environments unless instructed.
- Requires JDK 17 and Android SDK 36 (`org.gradle.java.home` in `gradle.properties`).
