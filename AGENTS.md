# AGENTS.md — Sumi

Instructions and constraints for coding agents working on this repository.
Read this file first. Then read `PRINCIPLES.md`, `ARCHITECTURE.md`, `DATA_MODEL.md`, `DESIGN.md` and `ROADMAP.md` as needed.

## 1. What Sumi is

Sumi is a native Android digital-wellbeing app: *Understand your attention. Find your balance.*

- It makes phone usage visible (awareness) and offers optional, gentle tools to change habits (intentionality).
- It is **not** a task manager, productivity tracker, parental-control tool, or app blocker.
- It is **local-first**: no backend, no accounts, no analytics, no ads, no network dependency.

If a change conflicts with `PRINCIPLES.md`, the change is wrong. Raise it instead of implementing it.

## 2. Hard constraints (never violate)

1. **No network code.** Do not add `INTERNET` permission, HTTP clients, analytics SDKs, crash reporters that upload, ad SDKs, or Firebase. If something seems to need the network, stop and ask.
2. **No `QUERY_ALL_PACKAGES`** unless the maintainer explicitly approves it after reviewing distribution policy. Use `<queries>` with the launcher intent filter for app discovery.
3. **No gamification.** No streaks, badges, achievements, leaderboards, scores that reward or punish.
4. **No guilt language and no alarm colors for ordinary usage.** No red warning states for "too much" screen time. See `DESIGN.md` copy rules.
5. **No forced blocking.** Focus sessions and limits are nudges (notifications, soft prompts). Do not build an accessibility-service or overlay blocker.
6. **Domain layer is pure Kotlin.** `domain/*` must not import `android.*`, `androidx.*`, or Compose. This keeps usage math unit-testable on the JVM.
7. **Usage aggregation must be idempotent.** Running collection twice over the same window must produce identical database state.
8. **Timestamps are UTC epoch milliseconds.** Calendar-day boundaries are computed from the device's current time zone at read/aggregation time, never stored as local times.
9. **Do not request notification permission at first launch.** Ask only when the user enables a reminder.
10. **Do not invent psychological conclusions.** Insights state observed facts; any interpretation is labeled as such.

## 3. Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 (customized with Kanso Matcha tokens) |
| Architecture | MVVM + repository pattern, unidirectional data flow |
| DI | Hilt |
| Database | Room |
| Preferences | DataStore (Preferences) |
| Async | Coroutines + Flow |
| Background | WorkManager |
| Navigation | Navigation Compose |
| Charts | Compose-native (Canvas), no chart library unless approved |
| Tests | JUnit, MockK, Compose UI tests, Room in-memory tests |
| Build | Gradle Kotlin DSL, version catalog (`libs.versions.toml`) |

Do not add dependencies without a reason written in the PR description. Prefer the standard library and AndroidX.

## 4. Repository layout

Logical structure (see `ARCHITECTURE.md` §3). Start with few Gradle modules and split when a boundary proves useful.

```text
sumi/
├── app/                 # Application, MainActivity, nav graph, Hilt root
├── core/
│   ├── common/          # Result types, dispatchers, time utils
│   ├── database/        # Room DB, DAOs, entities
│   ├── datastore/       # Preferences
│   ├── designsystem/    # Kanso Matcha theme, tokens, base components
│   ├── model/           # Shared pure models
│   └── ui/              # Shared composables
├── feature/             # onboarding, today, insights, focus, apps, app-detail, settings
├── domain/              # usage, insights, focus, categories (pure Kotlin)
├── data/                # usage, applications, insights, preferences (repositories, mappers)
└── platform/            # usage (UsageStatsManager), notifications, background (WorkManager)
```

Dependency direction: `feature → domain ← data → platform/core`. `domain` depends on nothing Android.

## 5. Conventions

### Code
- Kotlin official style, `ktlint` + `detekt` clean before commit.
- Prefer immutable data classes and sealed interfaces for UI state and events.
- ViewModels expose a single `StateFlow<UiState>`; one-off effects via a `Channel`/`SharedFlow` only when needed.
- Inject `CoroutineDispatcher`s; never hard-code `Dispatchers.IO` in logic you want to test.
- Inject a `Clock` and `TimeZoneProvider`; never call `System.currentTimeMillis()` or `TimeZone.getDefault()` directly in domain or data code.
- Public functions in `domain` and `data` get KDoc when behavior is non-obvious (especially time/boundary handling).

### Compose
- Stateless composables take state and lambdas. Hoist state to the ViewModel.
- Every screen has previews for light, dark, loading, empty, and error/permission-missing states.
- No hard-coded colors, spacing, or type sizes. Use tokens from `core/designsystem`.
- Respect font scale and TalkBack. Every interactive element needs a content description or label.

### Database
- Schema changes require a Room migration and a migration test. Export schemas (`room.schemaLocation`).
- DAOs return `Flow` for observed data and `suspend` for one-shot writes.
- Multi-row reconciliation runs in a single `@Transaction`.

### Git
- Conventional Commits (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`).
- Small, reviewable commits. One concern per commit.

## 6. Testing requirements

- **Usage engine (highest priority):** every rule in `ARCHITECTURE.md` §5 has a unit test. Include fixture event streams for: missing background event, duplicate events, out-of-order events, midnight crossing, reboot, time-zone change, screen lock, split-screen, still-open current session.
- **Idempotency test:** collect → reconcile same window again → assert identical rows.
- **Aggregation reproducibility test:** daily and hourly aggregates recomputed from sessions equal stored aggregates.
- Repositories: Room in-memory tests.
- ViewModels: Turbine or `runTest` with fake repositories.
- UI: Compose tests for onboarding, permission-missing state, and the Today screen.
- A feature is not done without tests for its non-trivial logic.

## 7. Working rules for agents

1. **Read before writing.** Check the relevant doc and existing code. Do not assume.
2. **Work milestone by milestone** from `ROADMAP.md`. Do not start a later milestone's features early.
3. **Build the usage engine as an independent, thoroughly tested component** before building screens on top of it. The dashboard is only its presentation.
4. **Ask when ambiguous** about product behavior. Do not guess on anything touching permissions, privacy, notifications, or copy tone.
5. **Keep changes scoped.** Do not refactor unrelated code in a feature PR.
6. **Never fabricate data in release code.** Fake/sample data is allowed only in previews, debug builds, and tests, behind clear naming (`Fake*`, `Sample*`).
7. **Update docs in the same change** when you change architecture, schema, or behavior. Tick progress in `ROADMAP.md`.
8. **Verify on a device or emulator** when touching `platform/*`. Unit tests alone do not prove `UsageStatsManager` behavior.
9. **Report limitations honestly.** If a platform behavior cannot be made reliable, document it in `ARCHITECTURE.md` §9 rather than hiding it.

## 8. Android platform notes

- Usage Access (`PACKAGE_USAGE_STATS`) is a special permission granted in system Settings via `Settings.ACTION_USAGE_ACCESS_SETTINGS`. Detect it with `AppOpsManager`; re-check on every return to foreground.
- `UsageEvents` retention is short (days). Long history comes from `UsageStats` aggregates and from Sumi's own persisted data. Persist sessions as soon as they are collected.
- WorkManager periodic work has a 15-minute minimum and may be delayed. It is **not** a live tracker. Collect on app open, on return to foreground, and via periodic work as a safety net.
- Package visibility (Android 11+) applies to app discovery and labels.
- OEM battery optimizations can delay or kill background work. Treat gaps as expected and reconcile on next open.

## 9. Definition of done

- Builds and passes lint, detekt, and all tests.
- Matches `DESIGN.md` (tokens, copy tone, light and dark).
- No new permissions, dependencies, or network use without approval.
- Accessibility basics pass (labels, contrast, touch targets ≥ 48dp, font scaling).
- Relevant docs and `ROADMAP.md` updated.

## 10. Open decisions (do not decide unilaterally)

- Monetization (not decided).
- Encrypted database (SQLCipher) vs. relying on device encryption.
- Whether `QUERY_ALL_PACKAGES` is ever needed.
- Final category taxonomy and default package-to-category mapping.

