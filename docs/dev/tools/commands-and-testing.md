# Build Commands & Testing Guidelines

## 1. Essential Gradle Commands

```bash
./gradlew :app:assembleDebug          # Build debug APK
./gradlew :app:testDebugUnitTest      # Run local unit tests
./gradlew :app:lintDebug              # Run Android Lint checks
./gradlew :app:installDebug           # Install onto connected adb device
```

## 2. Testing Layers

- **Domain Layer**: Unit tests for UseCases and pure domain logic.
- **Data Layer**: Repository tests with fake data sources and in-memory Room instances.
- **UI Layer**: Compose UI tests (`createComposeRule`).

## 3. Verification Guidelines for AI Agents

- **Do not build the app to verify simple code changes.** Use language server diagnostics or real-time IDE syntax checks.
- Only run full Gradle builds when explicitly requested by the user or when verifying final releases.
- Never run heavy Android Gradle builds inside resource-constrained WSL environments unless instructed.
- Requires JDK 17 and Android SDK 36 (`org.gradle.java.home` in `gradle.properties`).
