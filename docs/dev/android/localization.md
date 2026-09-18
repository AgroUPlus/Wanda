# Localization Architecture

Every user-readable string lives in `res/values/strings.xml`. Crowdin translates that file exclusively; any string left as a Kotlin literal will ship in English permanently.

## 1. Core Localization Rules

- **Zero string literals in Kotlin UI**: In composables, use `stringResource(R.string.key)`. In background workers or contexts, use `context.getString(R.string.key)`.
- **Pass `@StringRes Int`, not `String`**: Models or enums representing display text must hold `@StringRes Int` (e.g. `SettingsCategory`, `PlayerGesture`, `SmartMix`). The string is resolved at the composable rendering it, allowing language switching without object recreation.
- **Format args inside strings**: Use `stringResource(R.string.key, count)` matching `%1$s` in XML. Never concatenate like `"$count " + stringResource(...)`, as word orders differ across languages.
- **Plurals**: Always use `<plurals>` tags, never `if (count == 1)`.

## 2. Hardcoded vs Localized Strings

- Persisted values (playlist keys in Room, internal state IDs) must stay hardcoded so they remain immutable across locale changes.
- Animation and layout labels (`label = "trackRowBackground"`) are developer-only and must not be extracted.

## 3. Dynamic Locale Support

- The supported language list is generated dynamically (`generateLocaleConfig = true`), not hardcoded.
- Per-app language switching uses `LocaleManager` on API 33+ and an `attachBaseContext` configuration wrapper on older versions via `AppLocaleStore`.
- `MainActivity` is a `ComponentActivity` and must not be converted to AppCompat.
- New translations arrive from Crowdin on the `l10n_translations` branch.
