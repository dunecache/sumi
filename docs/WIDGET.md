# Widget design reference — Sumi home-screen widgets

**Version 3 — "Screen time" compact & expanded, 2026-11-15.** Implements the pasted
"Sumi — Home-Screen Widget Reference: Screen Time (Compact & Expanded)". Supersedes
the single quiet-glance widget (v1, 2026-11-14; history in git) and the discarded v2
editorial sketch (never shipped, 2026-11-15). Two picker entries are published; they
share one render core and one content logic.

**Artifacts:** two Android launcher widgets (Jetpack Glance 1.2.0), package
`app/src/main/java/com/sumi/platform/widget/`.
**Design standard:** Warm Editorial Minimalism, Zen, Material 3, Accurate Data — as
measured for Sumi (Kanso Matcha).

---

## 1. Purpose

The widgets are Sumi's smallest rooms: one surface, one honest number, one fair
comparison, and the shape of the day beneath. They mirror Today's numbers on the
home screen and are never a miniature Today, a dashboard, or a nag.

### Principles

- **Accurate** — every value is grounded in the shared pipeline; the widget cannot
  show a number the app would not show for the same moment. Exact durations, never
  approximations; formatting through the shared formatters only.
- **Internally consistent** — the bar, the listed rows, and any remainder all reconcile
  to the headline total. The bar always sums to the visible total.
- **Quiet** — muted colors, serif anchor, no alarms, no red, no terracotta, ever.
- **Honest** — silence is never substituted for data; missing access, pending first
  sync, and staleness are explicit, calm states.
- **Connected** — taps route into the app's existing destinations; no widget-internal state.

## 2. The two versions (published as two picker entries)

| Entry | Picker title | Purpose | Default | Minimum |
|---|---|---|---|---|
| **Compact** | `Sumi — Screen time` | The glance: distribution, the number, the comparison. No list. | 4×1 | 3×1 |
| **Expanded** | `Sumi — Screen time (expanded)` | The glance plus the answer to "which applications." | 4×2 | 4×2 |

Both are resizable in both axes. Both share the render core (`WidgetRender.kt`), the
snapshot loader (`WidgetSnapshotLoader.kt`), the reconciliation state machine
(`SumiWidgetState.kt`), and the drawn elements (`WidgetBrushes.kt`). They differ only
in layout and density.

## 3. Shared content rules

- **Total** — today's tracked usage at render time from `UsageRepository.observeDailyTotal`
  (the same definition of "today" as Today: device-zone day key at render time, UTC epoch
  stored). Formatted with the shared `formatDuration` — `2h 30m`, whole hours drop ` 0m`,
  exactly zero renders `0m`, non-zero sub-minute renders `Under a minute`.
- **Comparison** — today-so-far vs yesterday clipped to the same wall-clock window:
  `InsightsRange.TODAY.comparisonWindow(zone, now)` bounds; `IntervalMath.unionMsInWindow`
  over yesterday's stored sessions. Difference within **five minutes or ten percent**
  of the larger side → "About the same as yesterday at this time." Otherwise the shared
  `NeutralComparison` phrasing ("About 2h 56m less than yesterday at this time.").
  Yesterday with no recorded sessions → the line is omitted entirely. A partial day is
  never compared against a full day. In-app surfaces are unaffected by the 10% opt-in band
  (it is a widget-only parameter of the shared rule).
- **Application list** — the top ranked apps for today by tracked foreground duration,
  from today's per-app rows only, with the pipeline's exclusions applied (launcher, Sumi,
  IME). Ranking/ties resolve exactly as Today resolves them (`topApps`, name-ascending on
  ties, duplicate names disambiguated by package suffix). Durations use the compact
  formatter: `<1m`, `12m`, `2h 14m`, `45m`.
- **Bar** — segmented, proportional, with rounded ends and hairline surface-colored
  gaps (consistent with Today's category distribution). Segments are exactly the listed
  apps in list order plus a neutral remainder covering the rest of the total (stone-1,
  low emphasis). Tiny segments under **4%** of the visible total merge into the remainder
  rather than vanishing. The bar always sums to the headline total — by construction:
  remainder = (total − Σ listed rows) + merged tiny slices. The denominator is the
  headline total, so the bar and the list can never disagree with the number above them.
- **Reconciliation honesty** — unlisted/unattributable time is named by the remainder
  segment (and never hidden): pre-top-N apps, tiny merged slices, and aggregate-time with
  no per-app rows all land there. There is no "10m in other apps" text line in v3; the
  bar's remainder segment is the single reconciliation channel, and the accessibility
  announcement carries the same values.
- **Strings & localization** — widget copy uses the same Kotlin constants pattern as the
  rest of the app (English-only today, same as Today/Insights). Numeric formats that
  depend on locale (the staleness `Updated HH:mm`, date boundaries) already honor the
  device locale; time is rendered with `DateTimeFormatter.ofLocalizedTime(SHORT)`.
  Truthful state: the app has no translation infrastructure beyond `res/values/strings.xml`
  for the few picker-facing strings; those are localized as the app expands.
- **No fabricated layout numbers** — preview/initial layouts contain sample skeletons,
  clearly marked as sample in-file; never live-shaped, never shown as measurements.

## 4. Version A — Compact (`Sumi — Screen time`)

Default 4×1; minimum 3×1; horizontal/vertical resize. Layout, top to bottom:

1. Segmented proportional bar (~6 dp tall, rounded ends) — spans the content width.
2. Total in the editorial serif (bundled Literata, ~26 sp at default font scale) —
   the largest element.
3. Comparison line, small and muted (~10–10.5 sp); omitted per the shared rules when
   there is no baseline.

At the 3×1 minimum, padding tightens rather than dropping elements; the total never clips.
The bar and the total share one tap target (the whole tile) — the bar is not separately
interactive. No list.

## 5. Version B — Expanded (`Sumi — Screen time (expanded)`)

Default 4×2; minimum 4×2. Layout, top to bottom:

1. Eyebrow **Screen time** — small, muted, sans-serif.
2. Total in the editorial serif (~26 sp; ~30 sp at 4×3 and taller).
3. Comparison line, small and muted — same rules as A.
4. Segmented proportional bar (~8 dp tall) — same reconciliation rules.
5. Three application rows (up to five at 4×3+). Each row: app icon (launcher icon when
   resolvable; otherwise a monogram drawn in the app's category color with the initial
   letter), human-readable name (ellipsized at a measured budget, never wrapped), and the
   right-aligned duration. A small **colored dash** in the row's category color sits
   directly above the duration, linking row ↔ bar segment. Row height ≈ 40 dp at tall,
   30 dp comfortable, 24 dp tight (density ladder §6).
6. Optional goal line — **only when a daily goal is enabled**: one quiet line
   "Daily intention · 1h 18m remaining", or "… goal met today", or "… 18m over".
   Neutral styling; never red, never terracotta, never punitive. Uses the same goal
   state (`DailyGoal.evaluate`) as Today.
7. Optional staleness footer — **only when the last committed collection pass is older
   than one hour**: "Updated HH:mm", smallest muted type, trailing edge. Otherwise hidden.

Row taps open that application's detail screen (`app-detail/{packageName}` via the
existing navigation — explicit Intent extras, no deep-link scheme, no widget-internal
state). Every other region opens Today.

## 6. Density ladder (expanded)

The default 4×2 keeps all elements but tight-packed; taller tiles breathe. Nothing
clips the total at 1.3× font scale.

| Height | Ladder | Rows | Row height | Total | Footer |
|---|---|---|---|---|---|
| ≥ 190 dp | tall | up to 5 | 40 dp | 30 sp | shown |
| ≥ 140 dp (default 4×2 ≈ this) | comfortable | 3 | 30 dp | 26 sp | shown |
| < 140 dp | tight | 3 | 24 dp | 26 sp | omitted |

Compact behavior at minimum: horizontal padding 10 dp, comparison 9.5 sp, same elements.

## 7. Theming and typography

- Follow the app's persisted theme setting when one exists; otherwise the system
  light/dark configuration (System resolves night at render time; forced Light/Dark
  always forced). Resolution happens at load time, exactly as on v1.
- Surfaces: warm ivory (`SurfaceElevatedLight` `#FAF9F4` with a hairline `BorderSubtleLight`
  outline — legible on pale wallpapers) in light; warm charcoal (`SurfaceDark` `#273327`)
  in dark. Never pure black/white, never off-token hexes. Gap lines in the bar read in
  the tile surface color; the remainder segment is `stone-1`.
- **Typography.** Glance `Text` cannot carry bundled fonts (system fonts only — platform
  limitation, see §11), so the serif total is rendered as a **bitmap drawn in the app
  process with the bundled Literata TTF** and placed as an image, at both densities
  (26/30 sp), honoring font scale and density. Supporting text uses the platform sans
  (Inter-class face via the system, consistent with in-app behavior of system-referenced
  type on widget surfaces). Durations are not forced into tabular figures (glyphs
  differ per platform — alignment rests on the trailing right edge).
- Category colors come from the established palette (seeded token → palette index,
  stone-1 for neutral/uncategorized/residual) — never service hues, always the same
  index the in-app category distribution uses. Hard-coding colors per app/service is
  forbidden by the category system itself.
- Corner radius fixed at 24 dp — closest constant approximation to the system
  app-widget radius; Android 12+ additionally masks the tile to the launcher radius.
- No decorative shadows; no nested cards; no pills; no badges.

## 8. Update strategy

Honesty first: the widget never shows fresher than its last update, and it never
promises a cadence the system will not honor. Two independent background paths exist —
WorkManager and the system widget broadcast — and neither is a live tracker.

- **30-minute periodic re-render + collect** — `WidgetRefreshWorker` (WorkManager, KEEP
  unique work, enqueued from `SumiApplication.onCreate`). It first runs the same
  incremental 7-day overlap reconcile as `CollectionWorker` (serialized by the
  repository's mutex with all other collection paths) and then re-renders. Numbers,
  footer and day key therefore advance between app opens whenever the system runs the
  job. Failures are logged (`WidgetRefreshWorker` tag), never silently swallowed.
- **`updatePeriodMillis=1800000`** — the platform floor for widget broadcasts; the
  system delivers `APPWIDGET_UPDATE` at most every 30 minutes and may coalesce further
  in Doze or on devices with aggressive battery management or a rare/restricted
  app-standby bucket. A system-mediated path independent of WorkManager: when it fires,
  the widget re-renders committed state.
- **Forced updates (expedited one-time work, coalesced)** on: local date change,
  timezone change, locale change (`DATE_CHANGED` / `TIMEZONE_CHANGED` / `LOCALE_CHANGED`
  via `WidgetRefreshReceiver`), boot completed (`BootReceiver` enqueues the same work),
  usage collection in the foreground or in `CollectionWorker` (existing), app foreground
  + 60 s visible tick (existing), theme change (existing), and goal setting changes
  (today's three write sites re-render the widget).
- **Receiver → worker, not receiver → `goAsync()`.** Both widget receivers intercept
  `ACTION_APPWIDGET_UPDATE` and enqueue the expedited one-shot `WidgetRefreshWorker`
  (collect + `updateAll`) instead of letting Glance compose inside the broadcast.
  Glance 1.2.0's own source contains a vendor workaround for exactly this — its
  `maybeLaunchAsyncRequestWorker` routes widget broadcasts through WorkManager because
  "goAsync is broken on certain OEMs" — but the built-in condition is
  `MANUFACTURER == "vivo" && SDK_INT < 35`. On the owner's vivo V2322 (Android 15,
  SDK 35) that workaround is skipped; our interception applies it regardless of version.
  `DEBUG_UPDATE`, locale and click actions still use Glance's default receiver handling.
- Debug builds log the whole chain (`logcat -s SumiWidgetDiag`): receiver action, worker
  start with standby bucket and battery-optimization state, and each render with its
  total. Release builds compile the logging out.
- A wall-clock midnight chain (one-time 00:00 work re-enqueued by each run) was
  deliberately **not** added: `DATE_CHANGED` plus the 30-minute paths cover the same
  guarantee with less machinery; worst case a fresh day appears ≤ 30 minutes late and the
  number itself is always computed from the render-time day key.
- All content is computed at snapshot time, off the composition path; icons and bitmaps
  are prepared once per update.

**Why the widget can still look stale after this** — and what the user can do about it.
The platform has no push channel for usage events: the only ways today's numbers advance
are an app foreground tick, a WorkManager wake, or the system widget broadcast. Doze
defers all of them, and OEMs with aggressive battery management or a rare/restricted
app-standby bucket may defer them for hours. When nothing wakes, the widget shows the
last committed state with its staleness footer (never silently stale). The user-facing
mitigation is system-side: exclude Sumi from battery optimization (Settings → Apps →
Sumi → Battery → Unrestricted). This cannot be requested from code — it is a user choice
about their device, documented for the case where freshness matters to them.

## 9. Interaction

- Tap anywhere outside a row → Sumi (Today). If usage access is missing, any tap opens
  the app — the permission fix lives inside it (no interstitial).
- Tap an expanded-version row → that app's detail screen via an explicit Intent extra
  (`com.sumi.widget.extra.OPEN_APP_DETAIL`); `MainActivity` consumes the extra on create
  or `NewIntent` and routes through the existing nav graph — the same pattern as the
  notification `EXTRA_OPEN_FOCUS` deep-style. No scheme, no manifest filter, no network.
- No refresh button, no overflow menu, no settings gear, no swipe actions. Launcher
  long-press behavior is the launcher's own.

## 10. Accessibility

- The whole surface carries one sentence announcement: "Screen time today 2h 30m. About
  the same as yesterday at this time. Top applications: YouTube 1h 6m, Drive 45m,
  Mail 16m. [Daily intention sentence]." — action-first ordering preserved on rows.
- Each expanded row is an individual accessibility node with its own label and tap
  action: "Open {name} — {duration} today."
- Category colors are never the only channel: rows carry icons + names + durations; the
  bar always accompanies the list; the remainder carries no sole-meaning color cue.
- Bitmaps drawn at snapshot time honor `fontScale` (bitmap sizes are multiplied by the
  configuration font scale) and `density`. Layouts avoid fixed-width text where 1.3×
  scale would clip by construction (names ellipsize, the total image grows, the bar
  stretches).

## 11. States (both versions, both themes)

| State | Data | Renders |
|---|---|---|
| Normal | access + aggregate + per-app | Header/total/bar/(rows)/optional goal line |
| Zero usage | access + zero row | `0m` honestly; comparison only if a baseline exists; no rows, unlit bar |
| Import-only day | aggregate + per-app rows, no sessions | Same — rows/bar from per-app rows (import writes them) |
| Unattributable total | aggregate only | Rows omitted; bar = 100% neutral remainder segment |
| Usage Access missing | checker false | "Usage access needed" + "Tap to review your permissions." Calm; never a fabricated 0m |
| Fresh install / pending first sync | access, no aggregate row | "Waiting for data" + "Your screen time will appear here shortly." |
| Goal exceeded | used > goal | Actual total; "Daily intention · 18m over"; palette unchanged; never red |
| Stale data (> 1 h) | collection older than 1 h | Expanded only: "Updated HH:mm" smallest/muted, trailing edge |
| Theme switch | pref/system flip | Next trigger re-renders with the matching palette |

## 12. Engineering notes

- Two Glance providers (`CompactScreenTimeWidget`, `ExpandedScreenTimeWidget`) share
  one render core and one snapshot loader; receivers `SumiCompactWidgetReceiver`,
  `SumiExpandedWidgetReceiver` (exported per the AppWidget contract).
- Picker previews are static XML layouts with sample content (values marked as sample
  in-file) — no numbers from the pipeline are required to fill them; `initialLayout`
  shares the same skeleton.
- **Glance system-fonts limit:** `androidx.glance.text.FontFamily` resolves system
  families only, and RemoteViews text with bundled typefaces is not supported. The
  editorial serif therefore exists in the widget as a `Canvas`-drawn bitmap using the
  bundled `literata_regular.ttf`. Supporting text uses system sans. This is the
  deliberate cost of the reference's "bundled serif renders outside the app process"
  requirement; the alternative (platform `serif`) is a one-line change if it disappoints.
- Fractional bar segments cannot be expressed as `Row` children in Glance (its weights
  are uniform `Expand`), so the bar is a rounded-caps/hairline-gaps bitmap drawn at
  1200×36 px and stretched with `ContentScale.FillBounds`; flat colors keep the stretch
  invisible. Row-level alignment rests on trailing-edge text; 1.3× font scale enlarges
  the total bitmap.
- Icons: `AppIconLoader` → launcher icon bitmaps; monogram fallback (category-colored
  circle/rounded tile, initial letter, contrast-picked glyph) when unresolvable —
  consistent with "letter-mark on the category color."
- Row name budget is measured (≈110 dp at 12 sp) with the platform paint; longer names
  ellipsize deterministically.
- Registrars: two `appwidget-provider` XMLs with `targetCellWidth/Height` (4×1 / 4×2),
  `resizeMode="horizontal|vertical"`, `widgetCategory="home_screen"`, 30-minute
  `updatePeriodMillis` (the platform floor; the independent system backstop alongside
  `WidgetRefreshWorker`).
- `allowBackup="false"` is already in force; all processing stays on-device, no network,
  no new permissions, no new stored data, no analytics.
- Code isolated in `platform/widget/` (plus a repository member `lastSuccessfulCollectionUtcMs()`
  on `UsageRepository`); nothing outside presentation imports it.

## 13. Testing

- JVM (`app/src/test/.../SumiWidgetStateTest.kt`): ranking/order, limit 3/5, fractions
  summing to 1 with remainder, tiny-merge (<4%), full-remainder unattributable bar,
  compact durations, letter marks, zero/pending/permission states, goal states
  (remaining/met/over, clamped fraction), staleness threshold, a11y sentences.
- Domain (`domain/src/test/.../NeutralComparisonTest.kt`): the widget's opt-in 10-percent
  band in addition to the 5-minute floor.
- CI (build/test/lint) is the only verification available in this environment. **Not
  verified on a device:** Glance composition output, bar stretch/seam behavior, 4×2 fit
  at minimum density, 1.3× font-scale rendering, icon loading at scale, picker preview
  rendering, TalkBack traversal, midnight rollover end-to-end, and the refresh cadence.
  These need an on-device pass against the reference checklist in §13 of the reference.

## 14. Anti-patterns (rejected)

Streaks, scores, warnings, goal-exceeded red/terracotta, fabricated or approximated
numbers, hidden remainder, list percentages, hard-coded service colors, widget-internal
state/config, multiple actions, refresh gestures, "Updated" lines unrelated to real
staleness, scrollable content, clipped totals, any "first sync needed" banner that shows
stale numbers.

## 15. Provenance and deviations

- **v1** (2026-11-14): single quiet widget (total + optional goal rule). Retired by the
  v3 reference (2026-11-15): two versions as two picker entries, per the owner.
- **v2** (2026-11-15): editorial sketch — briefly rewritten docs/state, discarded
  unbuilt by owner decision; history in git.
- **Deviations kept, all documented:** bitmap serif (no bundled-font path in Glance
  `Text`); fixed draw width for the bar (fractional weights unsupported in Glance);
  platform sans for supporting text; `stone-1` for both remainder and uncategorized rows;
  4% merge threshold; no wall-clock midnight one-time chain (DATE_CHANGED + 30-min
  paths cover it); comparison keeps "About …" phrasing (shared calm wording —
  the reference's literal "2h 56m less" without "About" is unfounded editorial, not a
  requirement); localization is English-constant (Sumi is English-only today);
  per-row deep link via explicit-intent extra rather than a new URL scheme; the 6 h
  `updatePeriodMillis` was tightened to the 30-minute platform floor and
  `WidgetRefreshWorker` now collects incrementally before re-rendering (owner report
  2026-11-15: widget appeared frozen until the app was opened — the 6 h net plus a
  collection-free re-render guaranteed exactly that); receiver broadcasts are routed to
  an expedited worker instead of Glance's `goAsync()` (the OEM workaround Glance applies
  only below SDK 35, while the owner's vivo runs SDK 35).
- **Open questions for device verification:** 4×2 fit at minimum density, 1.3× scale
  behavior, launcher-specific masking, TalkBack order of tile vs. row nodes, and the
  actual midnight correction latency.
