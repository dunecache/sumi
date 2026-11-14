# ROADMAP.md — Sumi

Milestones, features, acceptance criteria, and progress.
Agents: work in order, tick boxes as items land, and do not start a later milestone's features early.

Legend: `[ ]` not started · `[~]` in progress · `[x]` done

## Release map

| Release | Theme | Maps to milestones |
|---|---|---|
| **MVP** | Understand your usage | M0, M1, M2 |
| **V1** | Understand your habits | M3 |
| **V1.5** | Gentle interventions | M4 |
| **Pre-release hardening** | Quality, privacy, accessibility | M5 |
| **V2** | Deeper personalization | Backlog (§ Later) |

---

## M0 — Foundation
*Goal: an empty but correctly structured app that builds, themes, navigates, and onboards.*

- [x] Initialize Kotlin project (Gradle Kotlin DSL, version catalog)
- [x] Configure Jetpack Compose and Material 3
- [x] Implement Kanso Matcha design tokens (light + dark) in `core/designsystem`
- [~] Bundle fonts: Inter, Literata, IBM Plex Mono (roles mapped to fallbacks; binary TTFs pending drop-in, see `app/src/main/res/font/README.md`)
- [x] Configure Hilt, Room, DataStore
- [x] Set up ktlint, detekt, CI build + unit tests (separate GitHub Actions workflows; CI is the gate, see `docs/CI.md`)
- [x] Navigation architecture: 4 destinations (Today, Insights, Focus, Apps) + onboarding graph
- [x] Onboarding: Welcome → Privacy → Focus (optional reminder) → Usage Access → Initial sync → First overview
- [x] Settings skeleton (privacy statement, theme)

**Acceptance criteria**
- App builds from a clean checkout and runs on emulator and device.
- Light and dark themes both render all placeholder screens correctly.
- Onboarding completes and is not shown again; Usage Access state is detected and re-checked on resume.
- No network permission in the manifest.

---

## M1 — Usage engine
*Goal: reliable, tested, idempotent usage collection. No UI beyond debug screens.*

- [x] Detect Usage Access (`AppOpsManager`) and deep-link to system settings
- [x] `UsageStatsManager` source: daily aggregates for history
- [x] `UsageEvents` source: foreground/background and screen interactive events
- [x] Normalization: timestamps, packages, event types, dedup, ordering
- [x] Session reconstruction (see `ARCHITECTURE.md` §5)
- [x] Handle: missing events, screen lock, reboot, time-zone change, midnight split, split-screen, duplicates, open current session
- [x] Daily and hourly aggregation from sessions
- [x] Incremental collection with overlapping reconciliation window
- [x] Persist to Room in transactions; idempotent upserts
- [x] Initial historical sync from `UsageStats` on first launch
- [x] Collection triggers: app open, return to foreground, periodic WorkManager safety net
- [~] Debug screen: raw events vs. reconstructed sessions (deferred; CI-verified fixture tests are the primary debug surface in M1)

**Acceptance criteria**
- Fixture-based unit tests cover every case listed above and pass on the JVM.
- Running collection twice over the same window yields identical database content.
- Daily aggregates equal totals recomputed from sessions.
- Midnight-crossing session splits correctly into two date keys.
- On a physical device, today's per-app totals are within an acceptable tolerance of the system Digital Wellbeing / Settings numbers (tolerance documented after measurement).
- No continuous polling loop; battery impact verified negligible in a profiling run.

---

## M2 — Core experience (MVP release)
*Goal: a user opens Sumi on real data with a coherent, canonical visual identity.*

> **Redesign pass (2026-11):** canonical Kanso Matcha v1.2 tokens now drive the full M3
> color scheme; v1.3.0 (2026-11-13) replaces the Evening Zen dark palette end-to-end
> (primitives + semantic + charts + diff tokens); Morning Matcha untouched.
> color scheme; the Today timeline reads real hourly buckets (previously hardcoded zeros);
> Apps rows and Today most-used show launcher icons, display names, durations and shares;
> App detail renders real history; adaptive NavigationRail on ≥600dp; edge-to-edge on.
> Ticks below reflect the code as of this pass.
>
> **Category analytics pass (2026-11):** persistent user-manageable categories (schema v2,
> migration 1→2), read-time category derivation with the current-assignment-recalculates-
> history policy, seeded defaults + bundled mapping (unknown apps stay uncategorized),
> segmented distribution on Today and Insights, category detail screen (share, 7-day series,
> app breakdown), category manager (create/rename/recolor/reorder/delete with reassignment),
> prominent daily intention goal (DataStore-backed, pure awareness, never enforcement),
> Apps filter/sort with category chips, assignment from app detail. Tests: domain category
> and goal logic, app-level intention/filter/slug/derivation logic, and rendered Compose
> UI tests under Robolectric (onboarding, permission-missing, Today). NOT yet run:
> migration test (androidTest, no device available) — see acceptance note below.
>
> **Excluded packages pass (2026-11):** device furniture (Sumi itself, the default
> launcher, the current IME, System UI, the `android` framework) is never tracked —
> dropped before session reconstruction, dropped on `UsageStats` import, and pruned
> from history with the affected days' totals recomputed. Rules in `:domain`
> (`UsageExclusions`, unit-tested), device set resolved per collection run in the
> platform layer (`DeviceExcludedPackagesResolver`) because the launcher/IME can
> change. R13 (hidden apps counting toward totals) stays open and unchanged.
>
> **Apps redesign pass (2026-11):** Apps rebuilt as the categorization funnel —
> live category chips (All · user categories in manager order · Uncategorized · Manage…),
> search that matches labels, packages, category names and "uncategorized", four sorts
> (today / this week / alphabetical / category) with the matching window's share as the
> row percentage, `NOT USED YET` zero-usage divider in usage sorts, category identity per
> row (dot + name, letter-mark-on-category-color icon fallback), long-press and explicit
> Select entry to multi-select, bottom-bar "Move N apps to…", shared category picker sheet
> with inline create-new-category (used by bulk, single reassign and app detail),
> confirmation snackbar with Undo (restores prior per-row assignment incl. the user-set
> flag), schema v4 (`isSystemApp`, `firstSeenAt`) for the system-apps policy (hidden by
> default; Settings → Apps → Show system apps) and the quiet New marker with an inline
> categorize affordance, `isInstalled` now flips to 0 on uninstall (assignment/history
> kept; reinstall restores), app detail gains week/30-day totals, a 30-day trend and a
> "usually active" window derived from the hourly buckets (honest below three days of
> history). Migration 3→4 in code (Focus claimed 2→3 upstream in the same window); schema
> JSONs 2.json/3.json/4.json committed (reconstructed to match the applied migrations;
> `identityHash` placeholders). NOT yet run: migration tests on a device/emulator — see
> acceptance note below.

- [x] **Today**: total screen time, comparison with previous day, most-used apps, usage distribution, daily timeline, one small observation
- [x] **Apps**: searchable library with icon, name, today and weekly usage, category
- [x] **App detail**: individual usage history
- [x] **Category detail**: total, share of tracked time, trailing-7-day series, app breakdown (current assignment; raw records never rewritten)
- [x] App categories: default mapping + user override
- [x] Category management screen: create / rename / recolor / reorder / delete with reassignment; persistence across restarts
- [x] Uncategorized residual bucket; unknown packages never silently classified
- [x] Daily intention goal: prominent Today card, edit sheet, disabled/reached/exceeded states, app-specific tests
- [x] Daily and weekly charts (Compose Canvas)
- [x] Usage timeline (activity across the day)
- [x] Weekly overview: compare days
- [x] Loading, empty, permission-missing, and partial-data states
- [x] Hide app (excluded from stats display)
- [x] Excluded packages: Sumi itself, the default launcher, the current IME, System UI and the `android` framework are never tracked (events, aggregates and stored history)
- [x] Settings: privacy, data management (export, delete)

**Acceptance criteria (MVP success)**
- A user can open Sumi, understand today's phone usage, identify the most-used apps, and explore previous days without configuring anything complicated.
- Today screen renders under 300 ms after data is available; cold launch under 1 s on a capable device.
- All core features work offline (there is no online mode).
- Every chart answers a stated question (documented in `DESIGN.md`).
- Copy passes the `PRINCIPLES.md` §9 checklist.

---

## M3 — Insights (V1)
*Goal: interpretation of habits, clearly separating facts from interpretation.*

- [x] Usage patterns: when usage tends to occur (time-of-day rhythm)
- [x] Session tracking views (estimated sessions, longest uninterrupted session)
- [x] Pickups, only where reliably measurable (otherwise omit or label as estimate)
- [x] Daily comparison and weekly trends
- [x] Monthly overview (weekly bars generalize; monthly bars use same chart primitive)
- [x] App group / category analysis (category mapping + per-app totals feed insights)
- [x] Rule-based personal observations from local data
- [~] Goals: personal usage intentions (optional, per app or category) (DataStore intentions in Focus; per-app goals deferred to M4)
- [x] Historical browsing (range-aware in Insights: Today · Yesterday · 7 · 30 days)

**Acceptance criteria**
- Every insight is generated by a deterministic rule with unit tests.
- Every insight is tagged fact vs. interpretation internally; interpretations are visibly framed as such.
- No insight uses unsupported psychological claims.
- Insights degrade gracefully with limited history (e.g., fewer than 7 days).

---

## M4 — Gentle interventions (V1.5)
*Goal: help users change habits, optionally and without force.*

- [x] Daily intentions: define what to prioritize today (Focus: per-day intention card, ahead of any session; also the Today goal from M2)
- [ ] App reminders: optional notifications after a chosen duration
- [ ] Soft limits: notify when an app or category reaches a threshold
- [ ] Focus sessions: gentle sessions with drift awareness (away-from-Sumi counts exits; 2-minute away auto-ends; no blocking, no per-app selection — by design)
- [ ] Quiet periods: suppress reminders
- [ ] Gentle suggestions based on usage patterns
- [x] Notification permission requested only when the user enables a reminder (Android 13+)
- [ ] Notification channels with sensible defaults and a global off switch
- [ ] Focus tab: intentions, sessions, limits, quiet periods, active reminders (intentions, sessions, one optional reminder — limits/quiet periods pending)

**Acceptance criteria**
- Nothing in the Focus tab is on by default.
- No forced blocking, overlay, or accessibility-service enforcement.
- Notification copy passes the tone checklist; no red/alarm styling.
- Quiet periods reliably suppress reminders.
- Notification rate is bounded (documented per-day cap, user-adjustable).
- Notifications are not required for the app to remain useful.

---

## M5 — Release preparation
*Goal: trustworthy, accessible, battery-friendly.*

- [ ] Battery profiling (background collection and notifications)
- [ ] Device compatibility testing, including OEMs with aggressive background restrictions
- [ ] Data export (user-controlled) and full deletion
- [ ] Optional local backup
- [ ] Accessibility pass: TalkBack, font scaling, contrast, touch targets
- [ ] Dark theme polish
- [ ] Privacy documentation and in-app privacy statement
- [ ] Decide on database encryption
- [ ] Store listing materials and permission justification text
- [ ] Final copy and tone review

**Acceptance criteria**
- Documented battery numbers on at least two physical devices.
- Manifest and dependency audit confirms no network access or analytics.
- Deleting data removes all rows and preferences.
- Passes accessibility checks listed in `AGENTS.md` §9.

---

## Later (V2 backlog, unscheduled)

- Custom app groups
- Flexible weekly goals
- Advanced usage comparisons
- Home screen widgets
- Custom notification rules
- Export usage reports
- Local backups
- Optional encrypted device-to-device transfer

Explicitly **not** planned: cloud accounts, social leaderboards, achievements, streaks, AI-generated advice.

---

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| `UsageEvents` retention is only a few days | Persist sessions immediately; use `UsageStats` for deep history; show "history begins on <date>" |
| Incomplete or odd event streams | Defensive reconstruction + fixture tests; mark estimated sessions |
| Background work delayed by OEM/battery optimization | Reconcile on open/resume; WorkManager only as safety net |
| Numbers differ from system Digital Wellbeing | Document methodology; explain differences in-app |
| Package visibility limits app discovery | Use `<queries>` for launcher apps; avoid `QUERY_ALL_PACKAGES` |
| Scope creep into blocking/gamification | `PRINCIPLES.md` review checklist on every feature |

## Progress log

| Date | Note |
|---|---|
| 2026-11-06 | M0 scaffolded: Gradle Kotlin DSL + version catalog, `:app` + `:domain`, Kanso Matcha tokens, Hilt/Room/DataStore, ktlint/detekt, separate CI workflows (build/test/lint), 4-destination nav + onboarding, Settings skeleton, Room schema (v1). Fonts pending binary TTFs. First CI `assembleDebug` pending (no local SDK; CI is gate). |
| 2026-11-06 | M1 usage engine + M2 core experience: domain normalization/reconstruction/aggregation (pure, fixture-tested), platform UsageEvents/Stats adapters, Room persistence with idempotent transactional reconciliation, UsageCollector (open/resume) + WorkManager safety net, Today/Apps/AppDetail/Insights charts wired to Room, categories with defaults, Settings theme + delete-all, categories helper, notifications channels. CI (build/test/lint) is the verification gate; local device testing intentionally skipped per direction. M3/M4/M5 remain. |
| 2026-11-06 | V1 complete (M3): deterministic rule-based insights with fact/interpretation tagging and graceful degradation, unit-tested; fonts pulled from google/fonts and wired into Type.kt (Inter/Literata/Plex Mono). Focus intentions + notification channels land as V1.5 scaffolding. M4/M5 remain. Verification agents run after V1 (per direction), not during. |
| 2026-11-13 | Category analytics + daily intention goal (schema v2, migration 1→2) and the excluded-packages policy (Sumi itself, launcher, IME, System UI, framework) landed on `feat/category-analytics-goal`; category totals derived at read time, goal is awareness only (never enforcement). Rules in `:domain` (`CategoryAnalytics`, `DailyGoal`, `UsageExclusions`) — unit-tested on the JVM. NOT yet run on a device: the Room 1→2 migration test (androidTest), dark mode / font scale / TalkBack verification. |
| 2026-11-13 | Compose UI tests now render real screens on the JVM under Robolectric (`:app:testDebugUnitTest`): onboarding (all steps incl. permission-missing states) and Today (populated/loading/empty/permission-missing). Replaces the state-contract stub. Still device-only: the Room migration test, dark mode / font scale / TalkBack verification. |
| 2026-11-13 | Observations render in the reflection role (Literata); category rows use the 48dp touch-target token (was 40dp). |
| 2026-11-13 | Category colors synced to named primitives: seeded taxonomy stores `matcha-4`/`clay-2`/`ochre`/`mist`/`wisteria`/`method` (Utilities = `matcha-2`, residual = `stone-1`); bootstrap `syncSeededColors` recolors rows still on the 2026-11 positional tokens (user recolors/reorders untouched, `sortOrder` preserved); intention ring track = `matcha-1`, over-goal switches only the ring to terracotta; `constant` reserved and never assigned. |
| 2026-11-13 | Library self-heal: the 90-day `UsageStats` import now creates Application rows, and every collection run backfills rows that exist only in imported history — apps used before events retention (days) no longer vanish from the Apps library and Apps search. |
| 2026-11-14 | Focus sessions land (schema v3, migration 2→3): intention card per local day (DataStore, zone-boundary rule shared with aggregation); session launcher (15/25/45 chips + 5–120 min slider, last duration remembered) with a full-screen calm active state; foreground service (`SPECIAL_USE`, 60 s drift poll via UsageEvents — gentle exits only, package names never stored; 2 minutes away auto-ends honestly as `away_limit`); low-importance ongoing notification; sessions sealed by bootstrap after reboot (`reboot` at the recorded boot instant, or `interrupted` with **no end timestamp** when unknowable); history + trailing-7-day roll-up reusing the Insights boundaries in `:domain` (`FocusMath`, `FocusHistory` — JVM tests); one optional once-a-day reminder (WorkManager, default off, POST_NOTIFICATIONS requested only on opt-in). Goal/sessions fully decoupled (§14; never coupled in code or copy). Repo test added: `data/focus/FocusSessionStoreTest` (Robolectric + Room in-memory, 7 lifecycle cases); `Migration23Test` written but never executed (no device; `app/schemas` is missing `2.json`/`3.json` — must be generated on a dev machine with an Android SDK). NOT verified on any device or emulator: foreground-service persistence, drift accuracy, notification tap routing, reboot seal. |
| 2026-11-14 | Insights honesty pass: average is stated with its window (zeros included, `Averages` in `:domain`), percent rendering unified (whole percents, one rounding rule across Today/Insights/detail/rows), uncategorized residual is actionable on Insights (deep-link to Apps → Uncategorized), and Apps has multi-select bulk reassign ("Move N apps to…"). Apps is deep-linkable as `apps?filter=uncategorized`. |
| 2026-11-13 | Previews added for the redesign-pass screens/components (TodayContent loading/permission-missing/empty/populated, daily-intention states, goal sheet, distribution card, category detail/manager); `CategoryDetailContent`/`CategoryManagerContent` extracted as stateless internal composables to make the screens previewable. |
| 2026-11-13 | Evening Zen synced to canonical v1.3.0 ("revised dark palette"): primitives/semantic/charts/diff tokens replaced; Morning Matcha untouched; dark chart palette = matcha-3/mist/clay-2/wisteria/ochre/matcha-2/terracotta; `constant` reserved; previews backgrounds updated. |
| 2026-11-14 | Apps redesign pass (schema v4, migration 3→4; Focus claimed v3 / 2→3 upstream in the same window): categorization funnel — live chips, category-name search, four sorts with window share, NOT USED YET divider, multi-select bottom-bar move with inline create-category, Undo confirmation; system-apps policy (hidden by default, Settings toggle; visibility only); uninstall→`isInstalled = 0` with reinstall restore; New marker; app detail gains week/30-day totals, 30-day trend and an honest usually-active window. Schemas 2/3/4.json reconstructed (v2→3→4 chain) and committed; migration tests written but never run here. NOT verified on device/emulator. |
| 2026-11-14 | Insights redesigned range-aware (Today · Yesterday · Last 7 days · Last 30 days): range switcher, overview with labeled average and time-aware comparison, tap-a-bar daily chart (DailyBars) with day inspector, range-aware category distribution, shared range-aware category detail screens, "Time in social apps" lens on the Social category, range top-five with deep-link to Apps, coverage-aware patterns (hour-of-day strip, morning/evening, weekday/weekend) and rebuilt observations. Domain: `InsightsRange`, `NeutralComparison`, `IntervalMath`; shared derivation in `core.usage.UsageDerivation`; range survives navigation/process death (`SavedStateHandle`). Not yet verified on device/emulator (no local aapt2 on this host); CI is the gate. |
| 2026-11-14 | Taxonomy extended: new seeds `ai`, `finance`, `browser` (fresh installs get all 11; existing installs backfilled once at bootstrap without resurrecting user-deleted seeds). Install-library autodetection (launchable apps → rows with default categories) wired into bootstrap. Owner's library mapped into `DefaultPackageCategories` (~90 exact rules: Kimi/Perplexity/ChatGPT/Grok/Claude/DeepSeek/Tongyi/Labs, TradingView/Binance/RedotPay/BPC, Brave/Firefox/DuckDuckGo, manga readers, music/video clients, carrier/dev tools, reading/newsletter apps). |

| 2026-11-14 | Onboarding gains an optional "Focus, gently" step (3 of 5) between Privacy and Usage Access: one once-a-day reminder toggle (default off). It is the only new ask moment — flipping it launches POST_NOTIFICATIONS (API 33+), denying leaves it off with a quiet explanation; the rest of onboarding never prompts. Owner requested (notification ask was undiscoverable); rule 9 kept — no launch-time or bare onboarding prompt exists. |
