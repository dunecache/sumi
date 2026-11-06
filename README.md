# Sumi

*Understand your attention. Find your balance.*

Native Android digital-wellbeing app — local-first, no backend, no accounts, no analytics, no network.
Not a task manager, productivity tracker, parental-control tool, or app blocker.

Read first: `AGENTS.md`, `PRINCIPLES.md`, `ARCHITECTURE.md`, `DATA_MODEL.md`, `DESIGN.md`, `ROADMAP.md`.

## Module layout

- `:app` — Android app (Compose UI, features, platform adapters, Room/DataStore).
- `:domain` — pure Kotlin (no `android.*`), JVM-unit-testable (usage math, time, results).

Start small; extract more Gradle modules only when a boundary proves useful.

## Build, test, lint (always in CI)

GitHub Actions is the gate. See `docs/CI.md`.

- Build: `.github/workflows/build.yml` → `./gradlew assembleDebug`
- Test: `.github/workflows/test.yml` → `:domain:test` + `:app:testDebugUnitTest`
- Lint: `.github/workflows/lint.yml` → `:app:lintDebug` + `ktlintCheck` + `detekt`

## Local (fast iteration only, not a gate)

```sh
./gradlew :domain:test
./gradlew assembleDebug  # requires local Android SDK
```

## Status

M0 foundation scaffolded. See `ROADMAP.md` for milestone checklist and progress log.
