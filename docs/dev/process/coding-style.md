# Coding Style & Architecture Rules

## 1. File Structure & Boundaries

- **Hard cap 300 lines per file.** Split when a file passes 250 lines.
- One concept per file, named for it — no `Components.kt`, `Entities.kt`, or `Utils.kt` grab-bags.
- Default to `internal`. Use `public` only for genuine cross-package APIs.

## 2. Robustness & Error Handling

- **No speculative fallbacks.** If something is unsupported or fails, return an empty result or `Result.failure` and surface it explicitly.
- Never invent placeholder data.
- Never swallow errors with blanket `catch (e: Exception)` that hides root causes.
- **No dead code.** If a helper or feature has no caller, it does not get written.

## 3. UI & Jetpack Compose

- UI consists of stateless composables driven by `StateFlow<UiState>`.
- No side effects inside composition.
- Every item in a `LazyColumn` or `LazyRow` must have a stable, unique `key`.
- Prefer immutable `data class` state; annotate with `@Immutable` / `@Stable` where it assists recomposition.

## 4. Coroutines & Concurrency

- Coroutines and Flow only.
- `Dispatchers.IO` is applied strictly at the repository or data source boundary, never inside a composable or a ViewModel body.
- ViewModels communicate with repositories only, never directly with data sources or DAOs.
