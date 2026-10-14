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

## Coverage Floor (Kover)

```bash
./gradlew :app:koverHtmlReportDebug   # app/build/reports/kover/htmlDebug/index.html
./gradlew :app:koverVerifyDebug       # fails below the floor in app/build.gradle.kts
```

Line coverage was 5.3% when the floor was set to 5. Raise `minBound` whenever coverage rises, never lower it.

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
