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
| `id` | String (PK) | Stable slug, e.g. `social`, `entertainment`, `learning`, `work`, `communication`, `other` |
| `name` | String | Display name (localizable resource key preferred) |
| `colorToken` | String | Design-system token name, **not** a hex value |

Seeded on first run. Categories are neutral; no category is labeled good or bad.

### 2.7 UsageGoal

| Field | Type | Notes |
|---|---|---|
| `id` | Long (PK, auto) | |
| `packageName` | String? | Set for an app goal |
| `categoryId` | String? | Set for a category goal |
| `targetDurationMs` | Long | Personal intention, not a mandate |
| `enabled` | Boolean | |

- Exactly one of `packageName` / `categoryId` must be non-null (enforce in code and with a check constraint or DAO validation).
- Used from V1 (goals) and V1.5 (soft limits).

### 2.8 FocusSession

| Field | Type | Notes |
|---|---|---|
| `id` | Long (PK, auto) | |
| `startTime` | Long | UTC ms |
| `endTime` | Long? | null = active |
| `status` | String | `ACTIVE`, `COMPLETED`, `ENDED_EARLY` (no "failed" state) |

### 2.9 FocusSessionApp **(added)**

Selected apps for a focus session.

| Field | Type |
|---|---|
| `focusSessionId` | Long (FK, PK part) |
| `packageName` | String (PK part) |

### 2.10 CollectionState **(added)**

Single-row table for the collection cursor.

| Field | Type | Notes |
|---|---|---|
| `id` | Int (PK, always 1) | |
| `lastEventTimeCollected` | Long | Global cursor, UTC ms |
| `earliestSessionTime` | Long? | Where session history begins (for "history begins on …" UI) |
| `lastTimeZoneId` | String | Detect time-zone changes and trigger re-keying |
| `initialSyncCompleted` | Boolean | |

## 3. DataStore preferences (not in Room)

| Key | Type | Notes |
|---|---|---|
| `onboarding_completed` | Boolean | |
| `theme_mode` | Enum | `SYSTEM`, `LIGHT`, `DARK` |
| `notifications_enabled` | Boolean | Global switch, default off |
| `quiet_periods` | Serialized list | Start/end local times and days; small enough for DataStore |
| `daily_intention` | String? + date key | Today's intention text |
| `reminders_per_day_cap` | Int | Bounded notification rate |
| `week_start_day` | Enum | |
| `show_hidden_apps` | Boolean | |

Rule of thumb: preferences go to DataStore; anything queried, joined, or aggregated goes to Room.

## 4. Relationships

```text
AppCategory 1 ── * Application 1 ── * UsageSession
Application 1 ── * DailyAppUsage
Application 1 ── * HourlyUsage
DailyUsage   1 ── * DailyAppUsage   (via dateKey)
FocusSession 1 ── * FocusSessionApp * ── 1 Application
UsageGoal * ── 0..1 Application / AppCategory
```

Foreign keys use `ON DELETE` rules that preserve history: deleting an `Application` row is not done; uninstalled apps are flagged `isInstalled = false`. Full data deletion truncates everything.

## 5. Derivation rules

1. `UsageSession.durationMs = endTime - startTime` (open sessions: up to the last known time, finalized on the next reconcile).
2. `DailyAppUsage[d, pkg]` = sum of session overlap with day `d` for `pkg` (sessions split at midnight in the active zone).
3. `HourlyUsage[d, h, pkg]` = sum of session overlap with hour `h` of day `d`.
4. `DailyUsage[d].totalDurationMs` = length of the union of all session intervals overlapping day `d` (avoids double counting in multi-window).
5. All of the above are recomputed for **touched days only** during reconciliation.
6. A "rebuild aggregates" operation recomputes 2–4 from sessions for all days with `source = SESSIONS` and must match stored values exactly.

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

- `exportSchema = true`; schemas committed to the repo.
- `MigrationTestHelper` test for every version bump.
- DAO tests with an in-memory database for: idempotent upsert, midnight split queries, aggregate rebuild equality, cascade/flag behavior for uninstalled apps.
- Seed data (categories) tested via a `Callback`.

## 9. Export format (M5)

User-initiated export produces a local file (JSON, optionally CSV for aggregates) containing: `DailyUsage`, `DailyAppUsage`, `HourlyUsage`, categories, goals. Sessions optionally included. A version field and schema version are included. Export never leaves the device unless the user shares the file.

