# DATA_MODEL.md — Sumi

Room entities, DataStore preferences, constraints, and derivation rules.
Companion docs: `ARCHITECTURE.md` (collection engine), `AGENTS.md` (constraints).

Entities marked **(added)** extend the original concept to cover cases the concept implies but does not list. Review them before M1.

## 1. Global rules

- All timestamps are **UTC epoch milliseconds** (`Long`).
- Calendar days are **date keys** (`"YYYY-MM-DD"`, ISO local date) computed from the device's time zone **at aggregation time**.
- Durations are milliseconds (`Long`).
- Sessions are the source of truth for recent usage. Daily and hourly aggregates must be **reproducible from sessions**.
- Aggregates are preserved independently of raw events (events expire in a few days).
- Processing is **idempotent**: unique constraints + upserts.
- Multi-row reconciliation always runs in a single `@Transaction`.
- Room schema export is enabled; every schema change ships a migration and a migration test.

## 2. Entities

### 2.1 Application

| Field | Type | Notes |
|---|---|---|
| `packageName` | String (PK) | |
| `displayName` | String | Label at last sync |
| `categoryId` | String? | FK → `AppCategory.id`, null = uncategorized |
| `isHidden` | Boolean | User-hidden app |
| `isCategoryUserSet` | Boolean **(added)** | Prevents default mapping from overwriting a user choice |
| `isInstalled` | Boolean **(added)** | Keep history for uninstalled apps |
| `isSystemApp` | Boolean **(schema v4)** | Launcher-visible system app; hidden from the Apps library by default (Settings → Apps → Show system apps). Collection and exclusions are unaffected |
| `firstSeenAt` | Long **(schema v4)** | UTC ms the row was first created; drives the quiet "New" marker. 0 = unknown (pre-v4 rows never read as new) |

Icons are loaded from `PackageManager` at display time and are not stored.

### 2.2 UsageSession

| Field | Type | Notes |
|---|---|---|
| `id` | Long (PK, auto) | |
| `packageName` | String | FK → `Application` |
| `startTime` | Long | UTC ms |
| `endTime` | Long? | null = open current session |
| `durationMs` | Long | `endTime - startTime`, or computed up to last known time while open |
| `dateKey` | String | Date key of `startTime` in the zone used at aggregation |
| `isEstimated` | Boolean **(added)** | Reconstructed via a fallback rule (`ARCHITECTURE.md` §5) |

Constraints and indexes:
- **Unique** `(packageName, startTime)` — makes upserts idempotent.
- Index `(dateKey)`, index `(packageName, dateKey)`, index `(startTime)`.

A session crossing midnight is stored once; aggregation splits its duration across date keys.

### 2.3 DailyUsage

| Field | Type | Notes |
|---|---|---|
| `dateKey` | String (PK) | |
| `totalDurationMs` | Long | Total screen time that day (union of intervals, see `ARCHITECTURE.md` R8) |
| `lastCollectedAt` | Long | UTC ms of last successful collection covering this day |
| `source` | String **(schema v5)** | `SESSIONS` — re-derived from sessions on every collection (default); `USAGE_STATS_AGGREGATE` — approximate upper bound from the import (sum of per-app time), written only for import-only days |

### 2.4 DailyAppUsage **(added)**
Per-app per-day totals. Needed for the Apps list, weekly charts, and for historical days that only have `UsageStats` aggregates (no sessions).

| Field | Type | Notes |
|---|---|---|
| `dateKey` | String | PK part |
| `packageName` | String | PK part |
| `durationMs` | Long | |
| `source` | String | `SESSIONS` or `USAGE_STATS_AGGREGATE` |

- PK `(dateKey, packageName)`. Index `(packageName, dateKey)`.
- When session data exists for a day, `source = SESSIONS` and values are derived from sessions. Session-derived rows replace aggregate-only rows for the same day.

### 2.5 HourlyUsage

| Field | Type | Notes |
|---|---|---|
| `dateKey` | String | PK part |
| `hour` | Int | 0–23 in the zone used for `dateKey` |
| `packageName` | String | PK part |
| `durationMs` | Long | |

- PK `(dateKey, hour, packageName)`. Index `(dateKey)`.
- Only exists for days with session data. Sessions are split at hour boundaries.
- DST days have 23 or 25 hours; handle by deriving hour buckets from zone rules, not by assuming 24.

### 2.6 AppCategory

| Field | Type | Notes |
|---|---|---|
| `id` | String (PK) | Stable slug: `social`, `communication`, `entertainment`, `productivity`, `education`, `reading`, `games`, `utilities`, `ai`, `finance`, `browser` (seeds; user-created ids are slugs too) |
| `name` | String | Display name, user-editable |
| `colorToken` | String | Design-system token name, **not** a hex value (`categoryColor1..7` or `categoryNeutral`) |
| `sortOrder` | Int | **(schema v2)** Display order, 0-based; seeded order is the canonical default |
| `isUserCreated` | Boolean | **(schema v2)** True for user-created categories; defaults are deletable too |

Seeded on first launch (and on first upgrade to v2, where the table was never populated) —
`INSERT ... IGNORE`, so user renames/recolors/reorders are never overwritten by seeding.
Categories are neutral; no category is labeled good or bad.

### 2.7 UsageGoal (not implemented)

Reserved shape for app/category-scoped goals. **Not implemented.** The daily screen-time goal
ships as a DataStore-only value (§3) — an awareness instrument that gates nothing. This table
would be the extension path for per-app goals; it is not created in schema v2.

| Field | Type | Notes |
|---|---|---|
| `id` | Long (PK, auto) | |
| `packageName` | String? | Set for an app goal |
| `categoryId` | String? | Set for a category goal |
| `targetDurationMs` | Long | Personal intention, not a mandate |
| `enabled` | Boolean | |

- Exactly one of `packageName` / `categoryId` must be non-null (enforce in code and with a check constraint or DAO validation).
- Used from V1 (goals) and V1.5 (soft limits).

### 2.8 FocusSession **(added, v3)**

Shipped in migration 2→3 (`focus_sessions`, index on `startedAtUtcMs`). The planned
per-app shape was dropped with M4's "no blocking" direction: a session is an activity
the user is in, not a restriction on anything else. Drift awareness measures *leaving
Sumi*, not which app the user went to.

| Field | Type | Notes |
|---|---|---|
| `id` | Long (PK, auto) | |
| `startedAtUtcMs` | Long | UTC ms |
| `endedAtUtcMs` | Long? | **null while the session is open** — the open-session invariant (at most one row with `outcome = 'open'` at a time) |
| `intendedMinutes` | Int | Minutes the user aimed for; doubles as the remembered default for the next session |
| `exitCount` | Int | Gentle exits (a Sumi→other-app transition) observed during the session; never package names |
| `outcome` | String | `open` (marker), `completed`, `ended_early`, `away_limit` (auto-end after 2 minutes away), `interrupted` (no end timestamp — the end instant is unknowable; never fabricated), `reboot` (sealed at the recorded boot instant) |

Timestamps are UTC; the trailing-7-day roll-up uses the same local-day boundaries as the
aggregation pipeline. The daily goal is a different instrument (see ARCHITECTURE.md §14)
and shares no state with these rows; starting/ending sessions never touches goal state.

### 2.9 FocusSessionApp — superseded, never built

The earlier draft paired sessions with a selected-app list. **Dropped by design**: Sumi
does not block or target individual apps in Focus. Kept here only as a historical note;
no such table exists.

### 2.10 CollectionState **(added)**

Single-row table for the collection cursor.

| Field | Type | Notes |
|---|---|---|
| `id` | Int (PK, always 1) | |
| `lastEventTimeCollected` | Long | Global cursor, UTC ms |
| `earliestSessionTime` | Long? | Where session history begins (for "history begins on …" UI) |
| `lastTimeZoneId` | String | Detect time-zone changes and trigger re-keying |
| `initialSyncCompleted` | Boolean | |
| `pipelineVersion` | Int **(schema v5)** | Version of the collection pipeline that produced the stored rows; below the current version the one-time repair runs (delete retained-range session-derived rows, reset the cursor). Migrated databases read `0` |

## 3. DataStore preferences (not in Room)

| Key | Type | Notes |
|---|---|---|
| `onboarding_completed` | Boolean | |
| `theme_mode` | Enum | `SYSTEM`, `LIGHT`, `DARK` |
| `notifications_enabled` | Boolean | Global switch, default off |
| `daily_goal_minutes` | Int? | Daily screen-time intention in minutes; absent = no goal. Read alongside `daily_goal_enabled` |
| `daily_goal_enabled` | Boolean | Default true. False = goal kept but paused; UI shows the disabled state. The goal gates nothing, ever (see ARCHITECTURE.md §14) |
| `daily_intention` | String? | Focus-tab intention text, persisted only with its date |
| `daily_intention_date` | String? | `YYYY-MM-DD` in the zone at save time; the intention renders on that day only, next day it starts blank |
| `focus_last_duration_min` | Int | Remembered duration for the next session; default 25 |
| `focus_reminder_enabled` | Boolean | Default false; one daily reminder at `focus_reminder_minute_of_day`, asked for POST_NOTIFICATIONS only when the user opts in (Android 13+) |
| `focus_reminder_minute_of_day` | Int | Minutes after local midnight (0–1439); default 540 (09:00) |
| `quiet_periods` | Serialized list | **Not yet implemented** |
| `reminders_per_day_cap` | Int | **Not yet implemented** (M4) |
| `week_start_day` | Enum | **Not yet implemented** |
| `show_hidden_apps` | Boolean | **Not yet implemented** |

Rule of thumb: preferences go to DataStore; anything queried, joined, or aggregated goes to Room.

## 4. Relationships

```text
AppCategory 1 ── * Application 1 ── * UsageSession
Application 1 ── * DailyAppUsage
Application 1 ── * HourlyUsage
DailyUsage   1 ── * DailyAppUsage   (via dateKey)
FocusSession — standalone; no foreign keys (deliberately: holds no package data)
UsageGoal * ── 0..1 Application / AppCategory   (reserved, not implemented)
```

Foreign keys use `ON DELETE` rules that preserve history: deleting an `Application` row is not done; uninstalled apps are flagged `isInstalled = false`. Full data deletion truncates everything.

## 5. Derivation rules

1. `UsageSession.durationMs = endTime - startTime` (open sessions: up to the last known time, finalized on the next reconcile).
2. `DailyAppUsage[d, pkg]` = sum of session overlap with day `d` for `pkg` (sessions split at midnight in the active zone).
3. `HourlyUsage[d, h, pkg]` = sum of session overlap with hour `h` of day `d`.
4. `DailyUsage[d].totalDurationMs` = length of the union of all session intervals overlapping day `d` (avoids double counting in multi-window).
5. All of the above are recomputed for **touched days only** during reconciliation — the touched days are derived from the collection window's time span (and, for zone changes, from the stored sessions' spans), never from the output rows. A day with sessions is owned by the sessions: all its rows (sessions- and aggregate-sourced) are replaced. A day without sessions keeps its `USAGE_STATS_AGGREGATE` rows.
6. A "rebuild aggregates" operation recomputes 2–4 for every day spanned by a stored session, from sessions, and must match stored values exactly. `USAGE_STATS_AGGREGATE` rows (and days with no sessions at all) are left untouched.
7. **Category totals are derived at read time, never stored.** For a window W, `CategoryUsage(c) = Σ durationMs FROM daily_app_usage JOIN applications ON packageName WHERE dateKey ∈ W AND applications.categoryId = c`, with `NULL` categoryId folded into the *Uncategorized* bucket. Percentages use Σ durationMs of the same window (including Uncategorized) as the denominator. Consequences: reassigning an app retroactively re-derives all historical category views; raw usage rows are never rewritten; `Σ category durations == Σ per-app durations` for every window by construction. (Implemented in `:domain` — `CategoryAnalytics.breakdown`.)
8. **Excluded packages never reach the database.** Device furniture — Sumi itself, the default launcher, the current IME, System UI (`com.android.systemui`) and the `android` framework — is dropped before session reconstruction, dropped on `UsageStats` aggregate import, and pruned from history (sessions, per-app daily/hourly rows, library rows) with the affected days' daily totals recomputed from the remaining sessions. The rules live in `:domain` (`UsageExclusions`); the device-specific set is resolved per collection run by the platform layer (`DeviceExcludedPackagesResolver`), because the launcher and the IME can change while the app runs. Pruning is one-way: rows already removed are not restored if the device set changes later.

## 6. Time zone handling

- Store UTC; derive date keys with the current zone.
- When the zone changes (`CollectionState.lastTimeZoneId` differs), recompute `dateKey` on sessions and rebuild affected aggregates in one transaction.
- Never store local clock times for events; `quiet_periods` is the only local-time data and is expressed as local time-of-day by design.

## 7. Retention

- Raw platform events: not persisted beyond what reconciliation needs.
- Sessions: kept for the user-configured retention (default: indefinitely; consider a setting later).
- Aggregates: kept independently of sessions, so pruning sessions never erases history.
- Deletion: "Delete all data" removes all tables' rows and DataStore preferences, then returns to onboarding.

## 8. Migrations and testing

- `exportSchema = true`; schemas committed to the repo. Room 2.6 writes them to a package-scoped path: `app/schemas/com.sumi.core.database.SumiDatabase/<version>.json` (the same layout `MigrationTestHelper` expects in androidTest assets). `1.json` (v1), `2.json` (category system), `3.json` (Focus sessions), `4.json` (Apps columns) and `5.json` (pipeline hardening) are all committed. `2.json`–`4.json` were reconstructed to match the applied migrations (column lists, `DEFAULT 0`, index DDL), since only `1.json` had ever been committed; `identityHash` values in the JSONs are placeholders and carry no validation semantics — Room builds the reference database from `createSql`. `kspDebugKotlin` on any machine with the SDK will overwrite them with the compiler's own exports.
- `MigrationTestHelper` test for every version bump (`Migration12Test.kt` 1→2, `Migration23Test.kt` 2→3 Focus, `Migration34Test.kt` 3→4 Apps, `Migration45Test.kt` 4→5 pipeline — **not yet executed anywhere**, no device/emulator available; `Migration23Test` also verifies the `index_focus_sessions_startedAtUtcMs` index and that existing rows survive; `Migration34Test` verifies the Apps columns land with `DEFAULT 0`; `Migration45Test` verifies `daily_usage.source` and `collection_state.pipelineVersion` land with `'SESSIONS'` / `0` — see ROADMAP.md).
- DAO/repository tests with an in-memory database (`:app:testDebugUnitTest` → `data/focus/FocusSessionStoreTest`, `data/usage/UsageRepositoryImplTest`): open/close invariant, stale-close-on-restart, exit counting, double-close no-op, boot finalization at the recorded instant, trailing-7-day roll-up boundaries; and for the usage pipeline: two-window collections reproducing the full-day view, gap reach-back past the cursor, estimate→real close replacement, import-only days surviving rebuild/repair, stale-open close (in-window and pre-retention), always-excluded pruning, midnight-crossing overlap queries.
- Migration 2→3 creates `focus_sessions` (+ index on `startedAtUtcMs`) and touches no other table; `outcome = 'open'` with NULL `endedAtUtcMs` is the single-open-session invariant, sealed by `FocusSessionRepository.startSession()` closing any existing open row first.
- Migration 3→4 adds `isSystemApp` and `firstSeenAt` to `applications` (`INTEGER NOT NULL DEFAULT 0`) and touches nothing else; the Apps refresh also marks vanished packages `isInstalled = 0` (a behavioral change, not a schema change).
- Migration 4→5 adds `daily_usage.source` (`TEXT NOT NULL DEFAULT 'SESSIONS'`) and `collection_state.pipelineVersion` (`INTEGER NOT NULL DEFAULT 0`) and touches nothing else; the next collection on a migrated install runs the one-time pipeline repair (see ARCHITECTURE.md §9).
- Seed data (categories) is inserted at bootstrap (`CategoryBootstrap`): fresh installs get the full taxonomy; existing installs get only the seeds that are new in their version (`ai`, `finance`, `browser`) — ids the user deleted are never re-added; user-created rows with a colliding id win. `INSERT … IGNORE` keeps it idempotent; `isUserCreated` rows are never reseeded or overwritten. Focus bootstrap: any open session whose start precedes the newest recorded boot instant is sealed as `reboot` at that instant (`CategoryBootstrap` → `FocusSessionRepository.finalizeOpenAfterReboot`); the boot receiver itself stays read-only. The trailing-7-day roll-up counts rows with `outcome != 'open'` via `COALESCE(endedAtUtcMs, startedAtUtcMs)` — sealed rows without an end timestamp (interrupted/reboot) count from their start. `startSession` seals any stale open rows in the same transaction as the insert (`insertSealingOpen`), so at most one open row ever exists.

## 9. Export format (M5)

User-initiated export produces a local file (JSON, optionally CSV for aggregates) containing: `DailyUsage`, `DailyAppUsage`, `HourlyUsage`, categories, goals. Sessions optionally included. A version field and schema version are included. Export never leaves the device unless the user shares the file.

