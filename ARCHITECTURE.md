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
| R5 | Device reboot / shutdown | Close open sessions at the last known event time before the gap; never extend across the reboot. API 29+ `DEVICE_SHUTDOWN` closes all sessions at the shutdown timestamp; `DEVICE_STARTUP` closes them at the last event before it. The reconstruction also never extends across `BootReceiver`-recorded instants or a boot the monotonic clock (`SystemClock.elapsedRealtime`) regressed for. |
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
| Reminders / focus notifications | `POST_NOTIFICATIONS` on Android 13+; requested in exactly two places — the Focus tab once the user flips the reminder on, or the same flip during onboarding's Focus step. Never on its own, never at launch |
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
  next session start (`SessionReconstruction.finalizeOpenSessions`; was
  `closeOpenAtNextSessionStart`, 2026-11); event-level
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

### Verification findings (agents, 2026-11-14) — pipeline correctness pass

- ~~Gap rule cuts long sessions (U-01)~~ → the 30-minute silence close is removed.
  Apps emit no events while merely staying in the foreground; long silences are bounded
  by screen-off (R3), reboot (R5), reconciliation finalization, or the retention-floor
  stale-open close — never by an arbitrary gap.
- ~~Aggregates derived from the window slice (U-02/U-16)~~ → after upserting sessions,
  every touched day (derived from the window's time span, not from output rows) is
  re-derived from **all** sessions overlapping that day (`UsageSessionDao.getOverlapping`)
  and replaced wholesale. Days backed by sessions are owned by the sessions (their
  `USAGE_STATS_AGGREGATE` rows are superseded); import-only days keep their import rows.
- ~~Window could not reach back (U-03)~~ → the event window is
  `[min(from, cursor - overlap), now]`, bounded below by the platform retention floor
  (~7 days); a missing cursor starts at the floor; the cursor is not advanced during
  the `UsageStats` import.
- ~~Rebuild erased imported history (U-04)~~ → `rebuildAggregates` drops session-derived
  rows (`source = SESSIONS`) and re-derives them; `USAGE_STATS_AGGREGATE` rows are kept.
  The zone-change path re-keys sessions, then does the same.
- ~~Estimated closes were permanent (U-05)~~ → the merge prefers a real close over an
  estimated close over an open row, and finalization prefers the earliest screen-off
  after a session's start; a stored estimated close is replaced when a later run sees
  the real BACKGROUND event.
- ~~Open rows escaped reconciliation forever (U-06)~~ → still-open rows are always loaded
  (`UsageSessionDao.getOpen`, and `getOverlapping` includes open rows regardless of
  start); the event window widens to the earliest open row (bounded by the floor); rows
  older than the floor close at `min(now, start + MAX_PLAUSIBLE_MS)`, flagged estimated.
- ~~Imported days showed 0 in bars (U-08)~~ → the import additionally writes an
  approximate `DailyUsage` row per import-only day (sum of per-app time — an upper
  bound; the per-day union is unknowable from aggregates) with
  `daily_usage.source = USAGE_STATS_AGGREGATE`.
- ~~Worker bypassed the mutex (U-09)~~ → the `Mutex` lives in `UsageRepositoryImpl` and
  every collection path serializes through it; label resolution (PackageManager IPC)
  happens before the write transaction.
- ~~Midnight-crossing sessions invisible on the second day (U-10)~~ → all session
  date-key queries are time-overlap queries (`observeOverlapping`,
  `observeOverlappingForPackage`); callers clip to the day when rendering.
- **Schema:** migration 4→5 adds `daily_usage.source` and `collection_state.pipelineVersion`.
  Below `CURRENT_PIPELINE_VERSION` the repository runs a **one-time repair**:
  retained-range sessions and session-derived aggregate rows are deleted (they may be
  underestimated/wrong from the old rules) and the cursor is reset, so the next run
  rebuilds the retained range from the platform's events. `USAGE_STATS_AGGREGATE` rows
  are kept. Rows older than the retention floor keep whatever they held — their events
  are gone and cannot be recovered. A fresh install never runs the repair
  (`pipelineVersion` is born current).
- **U-07 (reboot)** → `PlatformRebootSource` unions `BOOT_COMPLETED` records with a
  monotonic-clock regression detector (`SystemClock.elapsedRealtime()` resets on reboot;
  the boot wall instant ≈ `now − elapsedRealtime()`, sanity-bounded — `RebootDetection`,
  pure and unit-tested). API 29+ `DEVICE_SHUTDOWN` events close all sessions at the
  shutdown timestamp (`UsageEventType.DEVICE_SHUTDOWN`, not estimated); `DEVICE_STARTUP`
  closes them at the last event before it like a reboot boundary (estimated). The
  between-events `CrossedReboot` rule still guards every window gap.
- **U-08 remainder** → the first day Sumi ever sees events is a coverage boundary: when
  an imported upper-bound row exists for that day, it wins (sessions cannot reconcile
  the pre-event remainder of the day); days after the boundary are fully
  session-backed. Uses the recomputed `earliestSessionTime` — no extra state. The
  import logs the oldest day it actually returns (tag `SumiUsage`) for device-truth
  retention checks. Day-straddling boundary mechanics remain an approximation (an
  upper-bound total instead of a union on exactly that day) — accepted and documented.
- **U-17** → a package now closes only when its last resumed activity leaves
  (per-package resume counter — an act1 → act2 hand-off no longer truncates the
  session); adjacent same-package fragments separated by ≤ 2 s (activity hand-offs,
  PAUSED/RESUMED ordering inversions) are coalesced, keeping the first start (stable
  storage key).
- **U-18** → per-app day totals union session intervals per (day, package) instead of
  summing them — overlapping same-package fragments never inflate shares.
- **U-19** → the trailing-7-day focus roll-up counts sealed sessions too
  (`COALESCE(endedAtUtcMs, startedAtUtcMs)`), and the at-most-one-open invariant is held
  atomically in one transaction (`insertSealingOpen`).
- **U-20** → `UsageAccessChecker` falls back to the permission check when
  `checkOpNoThrow` returns `MODE_DEFAULT` (some OEMs).
- **U-21** → the launcher exclusion covers **every** package that handles HOME
  (`queryIntentActivities`), not only the resolved default.
- **U-22** → a fall-back day folds the duplicated local hour 01:00 into one bucket (up
  to 120 min); a spring-forward day has no hour-02:00 bucket. Documented; consumers
  scale buckets by value, never assume 60 minutes.
- **U-23** → label resolution hoisted out of the write transaction; `setInstalled`
  chunked; the prune's DISTINCT scans are memoized per process (re-run on set change or
  restart).
- **U-25** → while the app is visible, collection re-runs every 60 s, so today's
  figures advance with the open session.
- **U-10 annotations** → Insights/App-detail annotations clip sessions to the window /
  day and bucket coverage by every spanned day (previously a spill-over session was
  attributed to its start day in full).
- **Still open** → Digital Wellbeing tolerance is still unmeasured on a physical device;
  `Migration45Test` has not been executed (no emulator); the coverage boundary keeps an
  upper-bound total for exactly the first day Sumi ever saw (accepted trade — no union
  is possible); hidden-apps policy (R13) is unchanged; OEM event-pathologies (PAUSED/
  RESUMED interleavings beyond the coalescing window, dropped screen events) are
  bounded by finalization, never eliminated.

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
- Seeded color tokens use the primitive names (`matcha-4`, `mist`, `clay-2`, `wisteria`,
  `ochre`, `method`, `matcha-2`); rows still on the 2026-11 positional tokens
  (`categoryColor{n}` / `categoryNeutral`) are recolored at bootstrap by
  `CategoryRepository.syncSeededColors()` — only rows the user never touched, and
  `sortOrder` is preserved. Legacy tokens resolve forever via `categoryColorIndex`.
- Uncategorized always renders in `stone-1`; `constant` (7th palette slot) is reserved
  and never assigned to a category.
- Exclusion set: see §15 — device furniture (Sumi itself, the launcher, the IME, System UI)
  is never tracked. Everything else — including ordinary system apps — is tracked unless
  the user hides it (R13).
- **Application rows are created by every collection path**, not just the events reconcile:
  the 90-day `UsageStats` import creates them too (`planApplicationRows`/`applyApplicationRows`,
  was `ensureApplicationRows`, 2026-11), and each
  collection run self-heals rows that exist only in imported history
  (`dailyAppDao.getPackagesMissingApplicationRows`). Without this, apps whose usage lives
  only in history (events expire after days) were invisible in the Apps library and to
  Apps search.
- **Install-library autodetection** runs at app start (`CategoryBootstrap`) and after
  the first-sync import: every launchable installed app gets an `applications` row, even
  with no usage history. Discovery never overwrites user edits; rows whose package left
  the launcher catalog are flipped to `isInstalled = 0` (hidden from the library, never
  deleted — the stored assignment and history survive a reinstall, which flips the flag
  back). `InstalledAppsSource` uses the launcher query (`<queries>` MAIN/LAUNCHER,
  the same visibility the app already declares; no `QUERY_ALL_PACKAGES`).
- **New taxonomy (2026-11):** seeds gained `ai` (AI assistants), `finance` and `browser`;
  fresh installs get the full taxonomy, existing installs receive them at bootstrap via a
  backfill that inserts only ids that never existed before — ids the user deleted are
  never resurrected, and a user-created category sharing a new id wins. Colors come from
  the existing palette (`wisteria`, `ochre`, `clay-2`).
- **Uncategorized is actionable, never silent.** When the residual ≥ 15% of the week's
  tracked time, Insights surfaces a quiet prompt (duration + a deep-link to Apps filtered
  to Uncategorized). The residual row in the distribution card also taps through to that
  same list. Apps supports multi-select bulk reassign ("Move N apps to…"); assignments land
  as user-assigned rows and all views re-derive instantly (read-time derivation).

## 14. Goals versus restriction (implemented 2026-11)

The daily screen-time goal is an **awareness instrument**: it gates nothing, blocks
nothing, and notifies no one. It is evaluated from the same daily aggregate as the
headline statistic (never a separate source), computed in `:domain` (`DailyGoal`), and
stored in DataStore (`daily_goal_minutes`, `daily_goal_enabled`). Exceeding the goal is
reported in neutral language; remaining time is clamped at zero and never renders as a
negative value. The progress indicator uses the calm accent and switches to terracotta
(the warning hue, unused elsewhere) only once the self-set target is crossed. The Focus
destination is conceptually separate and the goal is never a precondition for anything
there. Any future enforcement feature would require explicit
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

## 16. Focus sessions (implemented 2026-11)

Focus is a practice, not a restriction surface. Starting, completing, or abandoning a
session **never** touches the daily goal and never counts as "usage" anywhere else; the
goal's instrument lives on Today (§14). A session ends with a fact, never a judgment:
`completed`, `ended_early`, `away_limit`, `interrupted`, or `reboot` — there is no
"failed" outcome and no score.

### 16.1 Intention (per local day)

The Focus tab holds today's intention, stored as `daily_intention` +
`daily_intention_date` (`YYYY-MM-DD`, zone resolved at save time — same boundary rule as
aggregation). A new day starts blank; the intention renders only on its own date; saving
a blank clears it. The editor prefills only when the stored date is today.

### 16.2 Sessions: storage and lifecycle

Room `focus_sessions` (schema v3, migration 2→3). UTC timestamps throughout. The
**open-session invariant** — at most one row with `outcome = 'open'` and a NULL
`endedAtUtcMs` — is enforced in `FocusSessionRepository.startSession()`, which closes
any existing open row as `ended_early` before inserting the new one. Table writes are
indexed on `startedAtUtcMs`; the history feed (newest, limit 50) and the trailing-7-day
roll-up (`weekStart` from `:domain` `FocusHistory`, same boundary rule as Insights)
both read from it. The open/close state machine rules live in `:domain`
(`FocusMath` — remaining time, completion, exits, progress) and are JVM-tested.

### 16.3 The reliability layer — foreground service

The session runs in `platform/focus/FocusSessionService`, a user-chosen foreground
service with `SPECIAL_USE` type (countdown for a task the user explicitly started; not
data sync, not media, not location). Two deliberate choices:

- **Manifest** declares `foregroundServiceType="specialUse"` with the
  `PROPERTY_SPECIAL_USE_FGS_SUBTYPE="focus_session_timer"` and the matching
  `FOREGROUND_SERVICE_SPECIAL_USE` permission on Android 14+; the app also calls the
  API-34 3-argument `startForeground` with the literal `0x10` on API 34+ only and the
  deprecated 2-argument call below, so `minSdk = 26` devices are unaffected.
- **Database is the source of truth, not the service.** The service reads the open row,
  mounts an ongoing low-importance (silent, `onlyAlertOnce`) notification with the
  remaining time — tappable, returns to Focus via the `open_focus` extra through
  `MainActivity.onNewIntent` — and re-reads the row each tick. If the service or the
  process is killed, the row outlives it: the app seals any open session whose start
  precedes the newest recorded boot instant as `reboot` at that instant on the next cold
  start (`CategoryBootstrap` → `FocusSessionRepository.finalizeOpenAfterReboot`). The
  boot receiver itself records instants only. If no recorded boot follows the session's
  start (OEM kill, archive), the row is sealed as `interrupted` with **no end
  timestamp** — the actual end instant is unknowable and is never fabricated. The
  history list labels these honestly ("Ended when the phone restarted", "Couldn't
  confirm how it ended"), and interrupted rows are excluded from the weekly roll-up
  rather than counted with a guessed end.

### 16.4 Drift awareness — gentle exits

While a session is open the service polls `UsageEvents` every **60 seconds** (coarse,
battery-conscious — not a live tracker) and counts **gentle exits**: a transition from
Sumi to any other app. An `ExitState` machine (`:domain` `FocusMath.ExitState`)
guarantees staying away does not inflate the count, and returning to Sumi resets for the
next leave. Screen events (no package) never count. After **2 cumulative minutes away**
(`AUTO_END_AWAY_MS`, a named constant in `:domain`) the session ends with outcome
`away_limit`. Drift data is **counts and timestamps only** — never package names,
never windows. If usage access is revoked mid-session (or was never granted), exits
simply aren't observed: the session still runs, the UI says so honestly. `UsageAccess`
is re-checked at ~10 s cadence while on screen; drift awareness pauses silently on
failure rather than guessing.

### 16.5 The reminder (opt-in, bounded)

One optional once-a-day reminder at a user-chosen local time (default 09:00, **off by
default**), scheduled with WorkManager (`sumi_focus_reminder_daily`, periodic 24 h with
initial delay to the next occurrence of the time); the worker re-checks the preference
before posting and is a no-op otherwise. `POST_NOTIFICATIONS` is asked for **only when
the user flips the reminder on** — either in the Focus tab or in the onboarding Focus
step; both are user-initiated (Android 13+). Denial leaves the reminder off with a quiet
explanation and a pointer to system notification settings. Copy invites ("Time for a
session?"), never chases; the whole block hides itself when notifications are denied.

### 16.6 Weekly "time in focus"

`SUM of actual elapsed in sessions ended inside the trailing 7 local days`, computed at
read time (`FocusHistory` in `:domain`, unit-tested; trailing window start via
`LocalDate.minusDays`, start-of-day in the current zone) — same boundaries and
formatting as Insights. No targets, no comparisons, no streaks. The history list shows
up to 4 recent sessions: date, `aim` vs `actual`, exits, honest outcome labels for
interrupted/reboot rows. Empty state: "No sessions yet — that's fine."

### 16.7 Known limitations — focus, stated honestly

- **Polling, not push.** Foreground-notification and drift updates ride a 1 s/60 s
  cadence; exact wall-clock drift after process death is bounded by next-open healing,
  not by surviving process death (Android does not keep FGS guaranteed either).
- **WorkManager drift.** The reminder is minute-of-day at schedule time and drifts
  slowly across DST and OEM batching; it is not an alarm and must not be treated as one.
- **Device-specific.** OEMs with aggressive battery policies may kill the FGS; the
  session then reads as `reboot`/`interrupted` honestly rather than silently.
- **2-minute away threshold** is a named constant (`FocusMath.AUTO_END_AWAY_MS`), not a
  user-tunable — changing it changes the product's promise, not just a preference.
- **Manual verification pending.** None of this has run on a device or emulator in this
  environment; see ROADMAP.md.

## 17. Apps library policy (implemented 2026-11, "Apps redesign")

The Apps destination is the categorization funnel: it answers "which applications account
for my usage" and "is my library organized the way I want it", nothing else.

**System-app policy.** Launchable system apps are **hidden from the Apps library by
default**; Settings → Apps → *Show system apps* mixes them back in (no separate section —
the row list stays one list). Detection flags `FLAG_SYSTEM | FLAG_UPDATED_SYSTEM_APP` at
discovery time and refreshes the flag on every refresh (`isSystemApp`, schema v4). The
policy only affects *visibility*: system apps that are not Sumi/launcher/IME/SystemUI are
still collected and counted exactly like user apps, and hiding them changes no total and
no percentage. Collection and the §15 exclusion set are untouched; percentages on the
screen stay denominated on Σ per-app usage of the same window — exactly what Today
counts, so the two screens reconcile. The denominator is stated on-screen ("Totals
exclude your launcher, keyboard and Sumi itself.").

**Denominator.** Percentages shown in usage sorts are the share of the matching window's
total tracked usage (today for Most used today, the trailing 7 days for Most used this
week), including the uncategorized residual — never only the visible rows, and never
against "all usage including Sumi". Alphabetical and By-category sorts show no
percentages (near-zero shares there read as noise, not information).

**Zero-usage divider.** In usage sorts, apps with zero usage sit below a quiet
`NOT USED YET` divider, ordered alphabetically; ties elsewhere break by name
(deterministic). In alphabetical/category sorts there is no divider.

**Install lifecycle.** Discovery reconciles the library every refresh: rows for packages
that left the catalog are marked `isInstalled = 0` (rows are never deleted, so category
assignments and usage history survive an uninstall); a reinstall marks them installed
again without re-stamping `firstSeenAt`, so the quiet *New* marker never re-fires.
`firstSeenAt` also feeds the inline "New · Categorize" affordance on fresh apps. An
*empty* discovery result is treated as "we know nothing", not as "everything was
uninstalled" — no rows are flipped in that case.

**User assignments always win.** The refresh path only ever updates `isInstalled` /
`isSystemApp`; `categoryId`, `isCategoryUserSet`, and user-created categories are never
touched by discovery. Bulk reassignment ("Move N apps to…") writes one assignment per
selected row with `isCategoryUserSet = 1`; category history re-derives at read time
(§13), so the fix is retroactively visible in Today, Insights and category detail. The
confirmation snackbar offers Undo (restores the previous per-row assignment, including
the user-set flag) for as long as the app lives.

**Search** matches display name, package id, current category name, and the word
"uncategorized" (the residual bucket answers to its name).

**Apps are not specific events.** Nothing here reaches the network; the screen reads
only the local database and `PackageManager`.

## 18. Insights ranges and comparison rules (implemented 2026-11)

The Insights destination is range-aware. Ranges are canonical in `:domain`
(`InsightsRange`); every caller uses the same window definitions.

**Ranges** (all end today, inclusive; days split at local midnight via `DateKey`):

| Range | Window | Complete? | Comparison window |
|---|---|---|---|
| TODAY | [today, now) — partial | no | Yesterday, clipped to the same local wall-clock time (session granularity) |
| YESTERDAY | Yesterday, full day | yes | The day before yesterday |
| WEEK | Trailing 7 days | yes | The 7 days immediately before the window |
| MONTH | Trailing 30 days | yes | The 30 days immediately before the window |

**One number source.** Day totals are merged at read time in the repository: stored
session-backed rows always win (the collection writes them from the same sessions, so
they are identical), and days without a stored row derive from the per-app sum for the
same day — the only days that exist are the imported per-app history. The 90-day
`UsageStats` import writes per-app rows but never `daily_usage`, so without this merge
every total-driven surface (overview, bars, averages, comparison sums) would read an
empty table while categories and most-used summed the import — showing two totals on
one screen and an invented "more than the previous 7 days". Session-backed days behave
exactly as before; the merge only back-fills what was never stored. The Today screen's
yesterday comparison uses the same TODAY-clip rule as Insights (yesterday up to the
same wall-clock time, session granularity; omitted when yesterday holds no sessions).

- TODAY is never compared against a complete day. The clipped comparison uses
  `IntervalMath.unionMsInWindow` over yesterday's sessions (union, R8); if no session
  rows exist for yesterday (events retention), the comparison line is omitted — not fudged.
- Complete windows compare zeros-included: days without records count as zero, but the
  comparison is shown only when the previous window recorded *at least one* day (a daily
  or per-app row). Otherwise it is omitted.
- Averages always state their window: "X per day across N days", zeros included
  (`Averages.dailyAverage`). A 1-day window has no average line (it is the total).
  The bar chart's average rule renders only when the average line exists.
- Every share uses the documented denominator — the same window's total tracked usage
  (`Σ per-app usage`, uncategorized included) — through `Averages.percentOf[Share]`
  (whole percents, one rounding rule). 26-vs-27 cannot recur.
- Category views for any range re-derive from untouched per-app records against the
  CURRENT assignment (§13 policy). TODAY's category detail is never compared to a
  complete day either; comparisons elsewhere use the previous equivalent window.
- Coverage is counted (`coverageDays` = days with any row). When history is thinner than
  the window, Insights says so ("Based on the N days …") and pattern observations are
  withheld until ≥ 3 days of data exist.
- The range survives navigation to detail screens (query arg `category/{id}?range=…`)
  and process death (`SavedStateHandle` key `insights_range`). Sessions and hourly
  buckets are fetched per range; the 90-day `UsageStats` import supplies complete-window
  coverage beyond events retention.

**Social lens.** "Time in social apps" is a curated lens on the Social category, never a
parallel data system. It hides entirely when the Social category row no longer exists
(the user deleted it), honors user renames, and never shows a today-vs-complete-day
comparison. It shows the top three Social apps by tracked time and counts the rest as
"and N more apps" (distinct Social packages with range usage beyond the three shown) so
the card reconciles visibly against its own total.

**Known limitation.** The "See all" action in Insights' top-five block links to the Apps
destination unfiltered (`apps?filter=all`); a matching-window sort cannot be expressed
until that destination's sort model can pin a window (the Apps and Focus destinations
have since shipped — see §16 and §17).
