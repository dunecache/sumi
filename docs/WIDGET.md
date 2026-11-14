# Widget design reference — Sumi home-screen widget

**Artifact:** Android launcher home-screen widget
**Product:** Sumi
**Adopted:** 2026-11-14 (source: the pasted "Android Home Screen Widget Design Reference",
now canonical; resolves the reference's own §11 open decisions)
**Implementation:** M6 in `ROADMAP.md` (pulled forward from the Later backlog at the
owner's request)

---

## 1. Purpose

A quiet, glanceable view of phone usage on the home screen. It answers one question:

**How is my attention being spent today?**

It is a companion to the app, not a miniature Today screen. It exposes only what is
useful at a glance and provides a clear route into the full experience.

### Principles

- **Glanceable** — understandable in seconds.
- **Quiet** — the home screen is not a dashboard.
- **Useful** — real usage data, never decorative stats.
- **Honest** — the date and data freshness are never implied beyond what holds.
- **Respectful** — no guilt language, warnings, streaks, scores, or pressure.
- **Connected** — tapping opens the relevant Sumi destination.

## 2. Recommended concept (adopted)

**The daily overview widget** — a screen-time widget centered on today's total, with the
optional daily goal as a restrained progress indicator. Contents, in order:

1. A small Sumi identity label (`SUMI`).
2. Today's screen-time total (dominant element).
3. A thin horizontal progress rule when a daily goal exists — never when it does not.
4. A short supporting line: remaining time, goal met, or the overage (neutral wording).
5. No buttons, no charts, no app lists, no configuration UI, no "Updated ..." line.

No category summary on larger sizes either. Extra space is breathing room.

### Hierarchy

**Primary:** today's total. **Secondary:** goal progress (only with a goal).
**Tertiary:** one supporting line. Anything else belongs in the app.

## 3. Layouts and sizes

Responsive via Glance `SizeMode.Responsive` with three breakpoints; the launcher
allotments, not fixed pixels, select the layout:

| Breakpoint | Width | Height | Content |
|---|---|---|---|
| **Compact** | 140 dp | 110 dp | `SUMI`, total (28 sp), one status line. No bar. |
| **Medium** *(canonical)* | 250 dp | 110 dp | `SUMI`, total (32 sp), 3 dp progress rule, one `percent · remaining` line. |
| **Large** | 330 dp | 148 dp | `SUMI`, total (36 sp), 3 dp progress rule, full `62% of daily goal` line + status line. |

- `resizeMode="horizontal|vertical"`; `minWidth`/`minHeight` = 140/110 dp; initial
  footprint 2×2 cells.
- At minimum size: wordmark + total stay readable; the secondary line is removed before
  the total shrinks; labels never clip; no icon-only actions are substituted.
- Compact omits the bar (and the percent) when legibility demands; the status line
  carries the meaning in words.

## 4. Visual design

Mirrors Kanso Matcha exactly — the widget is a natural extension of the app.

### Light
Warm paper background (`background` / `#F4F1EA`), primary text `onBackground`
(`#36382F`), muted secondary text `onSurfaceMuted` (`#565145`), accent progress
`accentStrong` (`#5C664B`) with a matcha-1 track (`#DCE2D1`). Restrained tonal
separation; no pure white.

### Dark
Deep forest background (`background` / `#181F18`), primary text `onBackground`
(`#E8EEE3`), muted `onSurfaceMuted` (`#B1BAAE`), accent `accentStrong` (`#D2E4BC`),
matcha-1 track (`#3B4C35`). Distinguishable from the launcher without being a floating
generic card.

### Theme behavior
- Follows the app's theme preference: System, Light, or Dark.
- System → resolves the current system night mode at render time.
- Forced Light/Dark always renders the forced variant.
- Wallpaper dominant color is never consulted (decided 2026-11-14).
- Known limitation: between system day/night flips and the next widget re-render
  (app foreground, usage collection, theme change, or the 15-minute collection safety
  net), System mode can briefly show the previous variant. See ARCHITECTURE §19.

### Typography
- Numeric total is the dominant typographic element — system sans, bold, 28–36 sp.
- Identity wordmark 11 sp caps, medium weight, muted.
- Supporting lines 12 sp muted; percent + status combined on one line except on Large.
- Platform sans, not Inter (standard inferred Roboto); tabular-figure control is not
  available to RemoteViews text (documented deviation).

### Shape and surface
- 16 dp corner rounding (`r-md`), applied by the widget clip; launchers on Android 12+
  additionally mask widgets with the system corner radius.
- No nested cards, no pill containers, no decorative borders.
- Subtle track, clear fill. Progress **never switches to a red/terracotta punishment
  color**, including above the goal.

## 5. Content and data rules

1. **Total** = the same aggregate as the Today screen (`daily_usage` for today's date
   key, via `UsageRepository.observeDailyTotal`). Same definition, same format
   (`formatDuration`). Never a stale value presented as live — the widget re-renders on
   the app's refresh triggers only.
2. **Zero** is a real state (`0m` + "No usage recorded yet"), distinct from pending and
   from sub-minute usage.
3. **Goal** — optional, informational, never blocking. Under the goal: `remaining` +
   `N% of daily goal`. At the goal: "Goal met today". Above the goal: `over` in neutral
   wording ("45m over your 3h goal"). Remaining is clamped ≥ 0; percent is unclamped
   (honest "120% of daily goal"); the bar clamps at 100% for drawing.
4. **No goal** → no bar, supporting line "Usage today". No invented target, ever.
5. **Freshness** — no "Updated ..." label; tapping Through to Sumi is the honest route.
   No refresh interval is promised or configurable.

## 6. Interactions

- **Main tap** (anywhere on the widget) → opens Sumi; lands on Today.
  Accessibility label: "Open Sumi — today's screen time".
- No goal shortcut, no refresh button, no gear, no swipe actions, no secondary targets
  in compact. One tap, one destination.

## 7. States (all supported, both themes)

| State | Condition | Renders |
|---|---|---|
| Normal — goal set | access + row + goal | total, rule, `62% of daily goal` + pace line |
| Normal — no goal | access + row + no/paused goal | total + "Usage today" |
| Zero usage | access + row at 0 | `0m` + "No usage recorded yet" (tap-through kept) |
| Usage Access missing | checker false, any data | explanation + "Open Sumi to enable it in Settings"; never a stale total |
| Pending | access + no row yet | `—` + "Waiting for today's first sync" |
| Goal exceeded | used > goal | actual total; neutral over-wording; normal palette |
| Theme switch | pref or system changes | re-renders on next trigger with the matching palette |

## 8. Platform requirements

- Glance 1.2.0 (`androidx.glance:glance-appwidget`) — the only new dependency, added with
  this milestone; no other libraries, no new permissions.
- `minSdk 26`, dp-based sizing, both `horizontal|vertical` resize axes.
- `updatePeriodMillis="0"` — refresh is push-driven by Sumi's own triggers (app
  foreground / 60 s visible tick / usage collection incl. the collection worker /
  theme change). Never claims live tracking.
- `initialLayout`/`previewLayout` share one static RemoteViews layout: wordmark on the
  paper surface, **no numbers** — the picker cannot fabricate statistics.
- Launchers vary; rendering on multiple launchers unverified here (doc honestly).

## 9. Accessibility

- Root is one focusable with a content description carrying action + values:
  "Open Sumi — today's screen time. 3h 29m. 50 percent of daily goal, 1h 30m remaining."
- The progress rule is decorative (information carried by adjacent text); redundant
  announcements are avoided by construction.
- Contrast: all widget text pairs on both themes use the measured WCAG-validated
  Kanso Matcha token pairs.
- Large-font support is limited by RemoteViews; the wordmark and total remain the
  legible minimum. TalkBack behavior not device-verified (see ROADMAP M6).

## 10. Anti-patterns (rejected, not to be built)

A miniature Today dashboard · multiple charts · most-used list · streaks/scores/badges ·
goal-exceeded red · decorative rings · icon-only actions · nested cards/pills ·
fake live updates · hard-coded sample statistics anywhere incl. the picker ·
content clipped at minimum size · value hidden behind an interaction.

## 11. Resolved decisions (owner, 2026-11-14)

1. **Total + optional goal progress** — adopted default.
2. **No variant picker** — single goal-aware widget; no configuration activity.
3. **No category summary** at larger sizes.
4. **No Focus quick action.**
5. **No manual refresh.**
6. **No "Updated" timestamp.**

## 12. Quality checklist

See implementation in `app/src/main/java/com/sumi/platform/widget/`; unit-tested state
machine (`SumiWidgetStateTest`). Device-verification items are tracked in
`ROADMAP.md` (M6) and were not performed in the adopting change.
