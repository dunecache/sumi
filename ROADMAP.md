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
- [x] Onboarding: Welcome → Privacy → Usage Access → Initial sync → First overview
- [x] Settings skeleton (privacy statement, theme)

**Acceptance criteria**
- App builds from a clean checkout and runs on emulator and device.
- Light and dark themes both render all placeholder screens correctly.
- Onboarding completes and is not shown again; Usage Access state is detected and re-checked on resume.
- No network permission in the manifest.

---

## M1 — Usage engine
*Goal: reliable, tested, idempotent usage collection. No UI beyond debug screens.*

- [ ] Detect Usage Access (`AppOpsManager`) and deep-link to system settings
- [ ] `UsageStatsManager` source: daily aggregates for history
- [ ] `UsageEvents` source: foreground/background and screen interactive events
- [ ] Normalization: timestamps, packages, event types, dedup, ordering
- [ ] Session reconstruction (see `ARCHITECTURE.md` §5)
- [ ] Handle: missing events, screen lock, reboot, time-zone change, midnight split, split-screen, duplicates, open current session
- [ ] Daily and hourly aggregation from sessions
- [ ] Incremental collection with overlapping reconciliation window
- [ ] Persist to Room in transactions; idempotent upserts
- [ ] Initial historical sync from `UsageStats` on first launch
- [ ] Collection triggers: app open, return to foreground, periodic WorkManager safety net
- [ ] Debug screen: raw events vs. reconstructed sessions

**Acceptance criteria**
- Fixture-based unit tests cover every case listed above and pass on the JVM.
- Running collection twice over the same window yields identical database content.
- Daily aggregates equal totals recomputed from sessions.
- Midnight-crossing session splits correctly into two date keys.
- On a physical device, today's per-app totals are within an acceptable tolerance of the system Digital Wellbeing / Settings numbers (tolerance documented after measurement).
- No continuous polling loop; battery impact verified negligible in a profiling run.

---

## M2 — Core experience (MVP release)
*Goal: a user opens Sumi and understands their day with zero configuration.*

- [ ] **Today**: total screen time, comparison with previous day, most-used apps, usage distribution, daily timeline, one small observation
- [ ] **Apps**: searchable library with icon, name, today and weekly usage, category
- [ ] **App detail**: individual usage history
- [ ] **Category detail**: group usage
- [ ] App categories: default mapping + user override
- [ ] Daily and weekly charts (Compose Canvas)
- [ ] Usage timeline (activity across the day)
- [ ] Weekly overview: compare days
- [ ] Loading, empty, permission-missing, and partial-data states
- [ ] Hide app (excluded from stats display)
- [ ] Settings: privacy, data management (export, delete)

**Acceptance criteria (MVP success)**
- A user can open Sumi, understand today's phone usage, identify the most-used apps, and explore previous days without configuring anything complicated.
- Today screen renders under 300 ms after data is available; cold launch under 1 s on a capable device.
- All core features work offline (there is no online mode).
- Every chart answers a stated question (documented in `DESIGN.md`).
- Copy passes the `PRINCIPLES.md` §9 checklist.

---

## M3 — Insights (V1)
*Goal: interpretation of habits, clearly separating facts from interpretation.*

- [ ] Usage patterns: when usage tends to occur (time-of-day rhythm)
- [ ] Session tracking views (estimated sessions, longest uninterrupted session)
- [ ] Pickups, only where reliably measurable (otherwise omit or label as estimate)
- [ ] Daily comparison and weekly trends
- [ ] Monthly overview
- [ ] App group / category analysis
- [ ] Rule-based personal observations from local data
- [ ] Goals: personal usage intentions (optional, per app or category)
- [ ] Historical browsing

**Acceptance criteria**
- Every insight is generated by a deterministic rule with unit tests.
- Every insight is tagged fact vs. interpretation internally; interpretations are visibly framed as such.
- No insight uses unsupported psychological claims.
- Insights degrade gracefully with limited history (e.g., fewer than 7 days).

---

## M4 — Gentle interventions (V1.5)
*Goal: help users change habits, optionally and without force.*

- [ ] Daily intentions: define what to prioritize today
- [ ] App reminders: optional notifications after a chosen duration
- [ ] Soft limits: notify when an app or category reaches a threshold
- [ ] Focus sessions: encourage staying away from selected apps (no blocking)
- [ ] Quiet periods: suppress reminders
- [ ] Gentle suggestions based on usage patterns
- [ ] Notification permission requested only when the user enables a reminder (Android 13+)
- [ ] Notification channels with sensible defaults and a global off switch
- [ ] Focus tab: intentions, sessions, limits, quiet periods, active reminders

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

