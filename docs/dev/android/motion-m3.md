# Material 3 Expressive & Motion

## 1. Theming and Motion Scheme

- Use `MaterialExpressiveTheme` and `MotionScheme.expressive()`.
- Retrieve spring specifications directly from `MaterialTheme.motionScheme` — do not hand-roll raw `spring()` parameters in screen implementations.

## 2. Screen Transitions & Navigation

- Employ shared-element transitions for Mini-Player → Now Playing transitions.
- Support predictive back navigation across all application screens.

## 3. Expressive Components

Prefer Material 3 Expressive components over older or static equivalents:
- `ShortNavigationBar`
- Wavy progress bars
- `FloatingToolbar`
- Expressive `LoadingIndicator`
- `MaterialShapes` polygonal morphs
