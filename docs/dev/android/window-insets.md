# Window Insets & Safe Zones

The app is edge-to-edge (`enableEdgeToEdge()` in `MainActivity`, no `WindowInsetsController` calls anywhere). System bars and cutouts are never inset automatically by the system.

## 1. Golden Rules

- **Account for insets at container edges**: Any composable aligned to a container edge must account for system insets itself, unless an ancestor layout demonstrably already has. `Modifier.align(...)` + `padding(n.dp)` inside a full-bleed `Box` is the classic antipattern that causes overlaps.
- **Encapsulate insets in shared components**: Put the inset handling in the shared component, not in each caller. For example, `ImmersiveHero` insets its own `overlay` slot; `PlayerCoverOverlay` takes the top inset for the buttons it floats over the cover, and `PlayerControlsPanel` the bottom one.
- **Selective edge insets**: Prefer `windowInsetsPadding(WindowInsets.safeDrawing.only(...))` over blanket `safeDrawingPadding()` when only specific edges matter (e.g. a top hero has no business insetting its bottom edge).
- **Never clear siblings with hard-coded heights**: Two composables aligned to opposite edges of a `Box` have no layout relationship. A constant written from paddings will be wrong because the real height includes the dynamic system bar insets. Measure with `onGloballyPositioned` at the head of the modifier chain and pass that value.
- **Test against both navigation modes**: When adding a control to a full-bleed layout, always test against both a gesture-navigation device and a three-button navigation device (the bottom inset differs by ~30dp).
- **Keep the side edges**: The shell's content padding carries start/end insets (a landscape three-button nav bar, a side cutout). `headerInset()` and `listInset()` keep them; a list inside a container already padded with `headerInset()` takes `nestedListInset()` (bottom only) so it is not inset twice.
