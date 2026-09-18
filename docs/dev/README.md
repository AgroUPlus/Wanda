# Wanda Developer Documentation Index

This directory contains developer documentation and engineering guides for Wanda (Android music player), organized following Linux-kernel style modular documentation principles.

## Structure

```
docs/dev/
├── process/
│   ├── coding-style.md          # Kotlin, Compose, Flow conventions & 300-line limit
│   ├── research-workflow.md     # Mandatory 4-step research workflow
│   └── git-and-authorship.md    # Clean commits, CLA Section 8, no AI attribution
├── android/
│   ├── window-insets.md         # Edge-to-edge rules, safe padding, gesture vs 3-button nav
│   ├── motion-m3.md             # Material 3 Expressive motion schemes and shared elements
│   └── localization.md          # Crowdin workflow, strings.xml, @StringRes, plurals
├── architecture/
│   └── overview.md              # Subsystems, IMusicSource, Room, Media3, battery & security
└── tools/
    └── commands-and-testing.md  # Gradle tasks, test suites, IDE diagnostics
```

## Guiding Principles

1. **Lightweight Root Pointer**: Root `AGENTS.md` and `CLAUDE.md` act as fast navigation indexes and invariant guards; deep implementation details live here.
2. **Offline Source of Truth**: Room is the single source of truth; network results are cached then emitted as Flows.
3. **Hardware & Battery Care**: Audio offloading, zero polling loops, and strict WorkManager constraints.
