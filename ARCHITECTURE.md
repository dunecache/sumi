# ARCHITECTURE.md — Sumi

Modules, layers, the usage collection engine, and technical decisions.
Companion docs: `DATA_MODEL.md` (schema), `AGENTS.md` (constraints), `PRINCIPLES.md` (philosophy).

## 1. Overview

Sumi is a native Android app, local-first, with no backend. The most important component is the **usage collection and aggregation engine**. The UI is only its presentation, so the engine is built first, as an independent and thoroughly tested component.

```text
Android system
   └─ UsageStatsManager / UsageEvents
        └─ Usage data source (platform)
             └─ Normalization
                  └─ Aggregation engine (domain, pure Kotlin)
                       └─ Room (persist sessions + aggregates)
                            └─ Repository → ViewModel → Compose UI (Flow)
```

## 2. Layers

```text
Presentation   Compose screens · ViewModels · UI state
Domain         Use cases · Models · Business rules · Aggregation   (pure Kotlin)
Data           Repositories · Room · DataStore · Mappers
Platform       UsageStatsManager · UsageEvents · PackageManager · WorkManager · Notifications
```

Rules:
- `domain` must not know about Android APIs or Compose. That keeps usage math testable on the JVM.
- `platform` adapts Android APIs to interfaces defined in `domain` (e.g., `UsageEventSource`).
- `data` implements domain repository interfaces and owns Room/DataStore.
- `feature` modules depend on `domain` and `core/designsystem`, never on `platform` directly.
- Dependencies are injected with Hilt; time, time zone, and dispatchers are injected for testability.

### Pattern
MVVM + repository pattern with unidirectional data flow. Each screen has:
- `UiState` (sealed/immutable),
- `UiEvent` (user actions),
- a ViewModel exposing `StateFlow<UiState>` built from repository `Flow`s.

## 3. Project structure

Logical structure:

```text
sumi/
├── app/
│   ├── src/main/
│   └── build.gradle.kts
├── core/
│   ├── common/          # dispatchers, Clock, TimeZoneProvider, Result
│   ├── database/        # Room database, DAOs, entities, migrations
│   ├── datastore/       # preferences
│   ├── designsystem/    # Kanso Matcha theme, tokens, components
│   ├── model/           # shared pure models
│   └── ui/              # shared composables
├── feature/
│   ├── onboarding/
│   ├── today/
│   ├── insights/
│   ├── focus/
│   ├── apps/
│   ├── app-detail/
│   └── settings/
├── domain/
│   ├── usage/           # session reconstruction, aggregation, repository interfaces
│   ├── insights/        # rule-based observations
│   ├── focus/           # intentions, limits, focus sessions
│   └── categories/
├── data/
│   ├── usage/
│   ├── applications/
│   ├── insights/
│   └── preferences/
└── platform/
    ├── usage/           # UsageStatsManager / UsageEvents adapters, permission check
    ├── notifications/
    └── background/      # WorkManager workers
```

**Gradle modules:** start small. Suggested initial split: `app`, `core` (common, model, designsystem, database, datastore, ui as packages), `domain`, `data`, `platform`, and `feature` (as packages). Extract modules only when a boundary proves useful. Do not create dozens of modules for the MVP.

## 4. Data collection pipeline

### 4.1 Two APIs, two purposes

| API | Used for |
|---|---|
| `UsageStatsManager` (`queryUsageStats`, interval aggregates) | Aggregated per-app durations over an interval; **historical** daily totals |
| `UsageEvents` (`queryEvents`) | Detailed foreground/background transitions and screen interactive events; **recent** session reconstruction |

Detailed events are retained by Android only for a few days. Never assume deep event history. Persist reconstructed sessions as soon as they are collected, and use `UsageStats` for older history.

### 4.2 Stages

1. **Source** (`platform/usage`): query a time range, emit raw platform events.
2. **Normalization**: convert to internal `RawUsageEvent(packageName, type, timestampUtcMs)`, drop irrelevant types, dedupe, sort by time (stable), map package names.
3. **Reconstruction** (`domain/usage`): build `UsageSession`s from the normalized stream (§5).
4. **Aggregation** (`domain/usage`): build `DailyUsage`, `DailyAppUsage`, `HourlyUsage` from sessions, splitting at day and hour boundaries.
5. **Persistence** (`data/usage`): transactional upsert into Room.
6. **Presentation**: repositories expose `Flow`; ViewModels map to UI state.

### 4.3 Triggers

Do **not** rely on WorkManager for live tracking (15-minute minimum period, system-delayed). Collect and reconcile on:

- app open,
- return to foreground,
- a periodic WorkManager task as a safety net,
- after Usage Access is granted (initial sync).

No continuous polling loop. No foreground service for tracking.

## 5. Session reconstruction rules

Example input:

```text
09:00  YouTube foreground
09:20  YouTube background
09:25  Chrome foreground
09:40  Chrome background
09:45  YouTube foreground
10:00  YouTube background
```

Expected output:

```text
YouTube  Session 1: 09:00–09:20   Session 2: 09:45–10:00   Total: 35 min
Chrome   Session 1: 09:25–09:40                              Total: 15 min
```

Rules the engine must implement (each needs unit tests with fixtures):

| # | Situation | Rule |
|---|---|---|
| R1 | Foreground without matching background | Close the session at the next event that implies the app left the foreground (another app's foreground, screen non-interactive, shutdown/reboot), otherwise treat as the open current session. |
| R2 | Background without matching foreground | Ignore, or if the window start is known, start from window start only when the platform guarantees it; default is ignore and log. |
| R3 | Screen turns non-interactive (lock/off) | Close any open session at that timestamp. |
| R4 | Screen turns interactive | Does not open a session by itself; a later foreground event does. |
| R5 | Device reboot / shutdown | Close open sessions at the last known event time before the gap; never extend across the reboot. |
| R6 | Midnight boundary | Keep one session if it truly spans midnight, but aggregate by splitting its duration across date keys. |
| R7 | Time-zone change | Sessions stay in UTC. Re-derive date keys using the zone in effect when aggregating. Reprocess affected days when the zone changes. |
| R8 | Split-screen / multi-window | Overlapping foreground apps: count each app's own foreground time, but do not let `DailyUsage.totalDurationMs` exceed wall-clock interactive time. Document the chosen rule in code (total = union of intervals). |
| R9 | Duplicate events | Drop exact duplicates (same package, type, timestamp). |
| R10 | Out-of-order events | Sort by timestamp before processing; stable for equal timestamps. |
| R11 | Incomplete current session | Store with `endTime = null`; for display, compute duration up to `now`; finalize on next reconcile. |
| R12 | Very short sessions | Keep them, but allow a minimum display threshold; do not discard in storage. |
| R13 | System / launcher packages | Filter via a maintained exclusion set; user can also hide apps. Hidden apps are excluded from displayed totals only if the user chooses so (decide in M2, document here). |
| R14 | Implausibly long sessions | Cap or flag sessions beyond a sanity threshold (e.g., no screen-interactive evidence) as estimated. |

Mark any session reconstructed using a fallback rule as `isEstimated = true` (see `DATA_MODEL.md`).

## 6. Synchronization and reconciliation

Incremental collection with a reconciliation window:

1. Read `lastCollectedAt` (per day, plus a global cursor).
2. Query events from `max(cursor - overlapWindow, retentionStart)` to `now`.
3. Normalize and reconstruct sessions for that range.
4. In **one Room transaction**: upsert sessions (unique key makes this idempotent), recompute affected `DailyUsage`, `DailyAppUsage`, `HourlyUsage` for touched days, then advance the cursor.
5. A second run over the same window must produce identical state.

Suggested overlap window: long enough to include any open session at the previous cursor (e.g., the last 6 hours or since the last known session start, whichever is greater). Tune after device testing.

**Initial sync:** on first launch after permission is granted, import `UsageStats` daily aggregates for the maximum available history into `DailyAppUsage` (flagged as aggregate-only, no sessions), then collect recent events and build sessions.

**Reproducibility:** daily and hourly aggregates must be re-derivable from sessions. A debug/maintenance action "rebuild aggregates" recomputes them and must yield identical values for days that have session data.

## 7. Permissions and platform integration

| Capability | Mechanism |
|---|---|
| App usage statistics | `PACKAGE_USAGE_STATS` (special permission, "Usage Access") |
| Usage event history | Same Usage Access grant |
| Installed app discovery | Package visibility rules; `<queries>` for launcher intent; avoid `QUERY_ALL_PACKAGES` |
| Reminders / focus notifications | `POST_NOTIFICATIONS` on Android 13+, requested only when the user enables a reminder |
| App blocking | Out of scope |

Usage Access:
- Granted in system Settings, not via a runtime dialog.
- Explain first, then open `Settings.ACTION_USAGE_ACCESS_SETTINGS`.
- Detect with `AppOpsManager` (`OPSTR_GET_USAGE_STATS`). Re-check on every resume. Handle revoke gracefully (permission-missing state, existing data remains visible).

## 8. Privacy architecture

```text
User's Android device
  └─ Sumi
       ├─ Processing and business logic
       ├─ Room        (usage history)
       └─ DataStore   (preferences)

No account · No analytics SDK · No advertising SDK · No cloud dependency
```

- No `INTERNET` permission.
- No external transmission of usage data.
- No advertising identifiers; no third-party analytics.
- User-controlled export and deletion (delete means all rows and preferences).
- Optional encrypted backups (V2/M5).
- Android `allowBackup`: set explicitly (`false` unless a deliberate, documented backup design exists), so usage data is not silently uploaded by system backup.
- Database encryption at rest: evaluate separately from Android's device encryption. If the threat model justifies it, use an encrypted Room implementation with an Android Keystore-backed key. Decision deferred to M5.

## 9. Known limitations (document honestly)

- Usage data does not update instantaneously.
- Background collection can be delayed by battery optimization.
- Usage events have limited historical retention; history before install comes only from `UsageStats` aggregates.
- Some manufacturer Android builds behave differently.
- Session reconstruction requires handling incomplete event sequences; some sessions are estimates.
- Pickups are only reported where reliably measurable.
- Reliable app blocking is far more complicated than tracking and is out of scope.
- Sumi's totals may differ slightly from the system's Digital Wellbeing numbers; explain methodology in-app.

### Verification findings (agents, 2026-11-06) — hardened 2026-11-06

- ~~Missing BACKGROUND (R1)~~ → session-level finalization: stale open rows close at the
  next session start (`SessionReconstruction.closeOpenAtNextSessionStart`); event-level
  per-app open is kept so genuine split-screen (R8) is unaffected.
- ~~Stale-open finalization (H3)~~ → same fix.
- ~~Aggregate hygiene (H5)~~ → delete-then-insert per affected day in `collectRecent`;
  full delete in `rebuildAggregates`, both inside the transaction.
- ~~Timezone re-key (H6/§6)~~ → `collectRecent` compares `lastTimeZoneId`; on change it
  re-keys all stored sessions and rebuilds all aggregates in one transaction.
- ~~Historical UsageStats (H7)~~ → `loadDailyAggregates` queries one calendar day at a
  time (`INTERVAL_DAILY`) instead of attributing multi-day totals to one day.
- ~~Closed→open regression (H4)~~ → merge prefers closed rows.
- ~~Reboot (R5)~~ → `BootReceiver` records `BOOT_COMPLETED` instants;
  `PlatformRebootSource` feeds them to `SessionReconstruction.rebootUtcMs`.
- **Still open:** system/launcher exclusion set (R13) — launcher time is counted until M2 decides.

Sumi prioritizes reliable daily and weekly reporting over pretending to provide perfect real-time monitoring.

## 10. Performance and reliability targets

These are engineering targets, not guarantees. Benchmark on real devices.

| Area | Target |
|---|---|
| App launch | Under 1 s on capable devices |
| Dashboard | Under 300 ms after data is available |
| Database | Indexed queries; no full-table scans on hot paths |
| Background activity | Minimal and scheduled |
| Memory | No unnecessary in-memory event history |
| Battery | No continuous polling loop |
| Offline | All core features functional |
| Reliability | Idempotent usage reconciliation |

Test on a standard emulator **and** physical devices from manufacturers with aggressive background restrictions.

## 11. Architectural decisions

| Decision | Choice | Rationale |
|---|---|---|
| Platform | Native Android, Kotlin | Direct access to usage APIs |
| UI | Jetpack Compose + customized Material 3 | Modern, themable with Kanso Matcha tokens |
| Persistence | Room + DataStore | Relational history in Room; simple prefs in DataStore |
| Time storage | UTC epoch ms | Unambiguous; day boundaries computed in current zone |
| Raw events | Kept only as long as needed | Privacy and size |
| Aggregates | Preserved independently of events | Events expire; history must not |
| Background | WorkManager as safety net only | Not a live tracker |
| Backend | None | Local-first |
| Modules | Few at first, split later | Avoid premature modularization |
| Charts | Compose Canvas | No dependency, matches design system |

## 12. Open items

- ~~Default package → category mapping source~~ → decided: bundled list of well-known packages (`:domain` `DefaultPackageCategories`); `ApplicationInfo.category` was rejected as a sole source (unreliable, often unclassified).
- Split-screen total rule validation on real devices (R8).
- Overlap window length for reconciliation (§6).
- Whether hidden apps count in totals (R13).
- Database encryption decision.


## 13. Category analytics (implemented 2026-11)

Sumi has a user-manageable category system. Design tenets:

- **Single primary category per app** (`applications.categoryId`, null = uncategorized).
  This makes the no-double-count invariant structural, not a query convention.
- **Category totals are derived at read time, never stored** (DATA_MODEL §5 rule 7).
  `Domain` computes the breakdown from immutable `daily_app_usage` rows joined with the
  current assignment. Percentages are denominated on the same window's total tracked
  usage (uncategorized included).
- **Reassignment policy: current assignment wins.** Historical category views are
  re-derived from the untouched raw records at the next read; raw usage is never
  rewritten. This is deliberately *not* point-in-time category history. Deleting a
  category reassigns its apps to the user-chosen target (default: uncategorized);
  user-assigned rows keep `isCategoryUserSet = true`, auto-classified rows keep
  `isCategoryUserSet = false` so default classification can reclaim them.
- **Classification precedence: user override > bundled default > uncategorized.**
  `:domain`'s `DefaultPackageCategories` maps well-known packages to defaults and is
  applied at row creation and at bootstrap (`CategoryBootstrap`). Unknown packages are
  **never silently classified** — they stay uncategorized. Mapping runs on package
  identifiers, never on display names.
- Seeding is idempotent (`INSERT IGNORE`) and user edits are never overwritten.
- Exclusion set: see §15 — device furniture (Sumi itself, the launcher, the IME, System UI)
  is never tracked. Everything else — including ordinary system apps — is tracked unless
  the user hides it (R13).

## 14. Goals versus restriction (implemented 2026-11)

The daily screen-time goal is an **awareness instrument**: it gates nothing, blocks
nothing, and notifies no one. It is evaluated from the same daily aggregate as the
headline statistic (never a separate source), computed in `:domain` (`DailyGoal`), and
stored in DataStore (`daily_goal_minutes`, `daily_goal_enabled`). Exceeding the goal is
reported in neutral language; remaining time is clamped at zero and never renders as a
negative value. The Focus destination is conceptually separate and the goal is never a
precondition for anything there. Any future enforcement feature would require explicit
user consent, a separate design, and its own threat analysis — it is out of scope.

## 15. Excluded packages — device furniture (implemented 2026-11)

Sumi measures time spent in *apps*. Some packages are always in the foreground by the
system and are not apps the user chooses to open:

| Package | Why it is excluded |
|---|---|
| Sumi itself | Opening Sumi to look at your usage must not inflate your usage |
| The default launcher (HOME) | Time on the home screen is not app usage |
| The current input method | The keyboard is not an app |
| System UI (`com.android.systemui`), `android` | Status bar, notification shade, recents overlay, framework |

**Rules live in the domain, the device set lives in the platform.**
`:domain`'s `UsageExclusions` owns the rules and the filtering (`isExcluded`,
`filterEvents`, `filterAggregates`) and is unit-tested. The platform layer
(`DeviceExcludedPackagesResolver`) resolves the *device-specific* set — Sumi's own
package, the package owning the default HOME activity, and the package of the current
IME (`Settings.Secure.DEFAULT_INPUT_METHOD`) — per collection run, because the launcher
and the IME can change while the app runs. Resolution never throws: an unresolvable
launcher or IME simply means that package is not excluded.

**Where the rules are applied**
- Collection: excluded packages' events are dropped before session reconstruction, so
  they never become sessions and never reach the aggregates.
- Import: excluded packages' `UsageStats` aggregate rows are dropped on initial sync.
- History: rows stored before the policy existed are pruned on the next collection, and
  the affected days' daily totals are recomputed from the remaining sessions, so the
  headline stays the union of the sessions that are kept. Only packages in the excluded
  set are touched — never a user app. Library rows (`applications`) are pruned too, so
  device furniture never shows up as a zero-usage app in the library.

Pruning is **one-way**: rows already removed are not restored if the device set changes
later (e.g. the user switches to another launcher). The new launcher's own usage is
tracked from that point on.

**Why it matters for the category system.** Without this, Sumi's own foreground time
lands in *Uncategorized* and inflates the total the user is trying to understand. It is
a correctness issue, not a cosmetic one: the numbers have to describe the user's apps.

**Still open (R13).** Whether *hidden* apps (a user-facing "hide this app" switch)
count toward totals is a separate decision and is unchanged: hidden apps are still
counted today.
