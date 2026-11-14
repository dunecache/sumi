# DESIGN.md — Sumi

Visual, interaction, and content design for Sumi, built on the **Kanso Matcha** system
(Zen × Wabi-Sabi × Matcha). Companion docs: `PRINCIPLES.md`, `ROADMAP.md`.

> **Note on tokens:** the canonical **Kanso Matcha v1.3.0** semantic tokens (Morning Matcha unchanged; Evening Zen revised dark palette) are now the source of truth for `core/designsystem/theme/Color.kt`, mapped onto the full Material 3 color scheme. The values in the tables below are the original provisional placeholders kept for history; the shipped app uses the canonical set (see the design-system repository). Always reference tokens by name, never by hex, in feature code.

## 1. Personality

- Calm, not sterile.
- Warm, not overly playful.
- Informative, not overwhelming.
- Organic, not geometrically perfect.
- Helpful, not controlling.

A user should open Sumi, understand their situation in a few seconds, and leave.

## 2. Foundation (Kanso Matcha)

- Balanced contrast.
- Muted matcha accent.
- Warm ivory backgrounds.
- Organic surfaces (soft, slightly irregular feel through radii and tone, not decoration).
- Light and dark themes.
- Fonts: **Inter** (interface), **Literata** (reflective/editorial text), **IBM Plex Mono** (numbers and technical values).

Material 3 is the component base, customized by tokens. Do not ship default Material purple or dynamic color unless a deliberate "follow system" option is added later.

## 3. Design tokens

### 3.1 Color roles (semantic)

Use roles, not raw colors.

| Role | Purpose | Light (provisional) | Dark (provisional) |
|---|---|---|---|
| `background` | App background | warm ivory `#F6F2E8` | deep moss-charcoal `#1B1D18` |
| `surface` | Cards, sheets | `#FBF8F0` | `#23261F` |
| `surfaceVariant` | Subtle containers | `#ECE6D6` | `#2C3027` |
| `onBackground` / `onSurface` | Primary text | `#2A2B25` | `#E9E5D8` |
| `onSurfaceMuted` | Secondary text | `#6B6C5F` | `#A3A596` |
| `accent` | Muted matcha, primary action | `#7A8F55` | `#A3B97B` |
| `onAccent` | Text on accent | `#FFFFFF` | `#1B1D18` |
| `accentContainer` | Soft accent fill | `#DCE3C8` | `#38422B` |
| `outline` | Dividers, borders | `#D9D2BE` | `#3D4136` |
| `info` | Neutral highlight | `#7C8A8C` | `#9AA8AA` |

Rules:
- **No alarm red** for ordinary usage. There is no "danger" role in the usage UI. A `destructive` role exists only for data deletion confirmation.
- Category colors come from a muted, harmonious palette of primitive chart tokens (`matcha-4`, `mist`, `clay-2`, `wisteria`, `ochre`, `method`) named after the canonical charts palette; the positional `categoryColor{n}` tokens persisted before the sync still resolve. Hues are fixed to the category, never to rank; they must be distinguishable in both themes and never rely on color alone (add label or pattern).
- `matcha-2` is the sage accent (utilities/secondary), `stone-1` is the uncategorized residual, and the 7th palette slot `constant` is reserved — never assigned. `terracotta` is the warning hue: the over-goal state only (intention ring). Nothing else may use it.
- Charts are separated by the card-colored 2dp gap (muted hues blend); the intention ring's empty track uses `matcha-1`, not gray.
- Minimum contrast: 4.5:1 for body text, 3:1 for large text and chart marks against their background.

### 3.2 Typography

| Role | Font | Use |
|---|---|---|
| Display | Literata | Hero line on Today ("Your day, in perspective.") |
| Headline / Title | Inter (semibold) | Screen and section titles |
| Body | Inter | Default text |
| Reflection | Literata | Observations and gentle suggestions |
| Numeric | IBM Plex Mono | Durations, big numbers, chart labels |
| Label | Inter (medium, small caps optional) | Overlines like `TODAY · FRIDAY`, `MOST USED` |

Guidelines:
- Respect system font scale up to at least 200%; layouts must not clip.
- Large duration figures (e.g., `3h 42m`) use Plex Mono with tabular numerals.
- Bundle fonts locally (no downloadable fonts, no network).

### 3.3 Spacing, shape, elevation

- 4 dp base grid; common steps: 4, 8, 12, 16, 24, 32, 48.
- Screen horizontal padding: 20–24 dp.
- Corner radii: small 12, medium 20, large 28 dp (soft, slightly generous).
- Elevation: prefer tonal surfaces over shadows; at most one subtle level.
- Touch targets ≥ 48 dp.

### 3.4 Motion

- Minimal. Short fades and gentle position transitions (150–250 ms, ease-out).
- No bouncing, confetti, counting-up number animations for "achievement" feeling.
- Respect system "remove animations".
- Chart transitions may animate on first draw only, subtly.

## 4. Navigation

Four-destination bottom navigation:

| Tab | Purpose |
|---|---|
| **Today** | The day at a glance |
| **Insights** | Patterns over time |
| **Focus** | Intentions and gentle boundaries |
| **Apps** | Searchable application library |

Labels are text + icon. Selected state uses `accentContainer` pill, not a loud color. Onboarding and settings sit outside the tab bar.

## 5. Screen specifications

### 5.1 Today (primary)

Order, most important first:
1. Overline `TODAY · FRIDAY` (label style).
2. Hero line (Literata): "Your day, in perspective."
3. Total screen time (Plex Mono, large) with caption "Screen time today".
4. Comparison with the previous day (neutral wording, no up/down arrows in red/green).
5. **Daily intention** card: configured target, time used, remaining (never negative), one elegant progress indicator, edit/remove in place. Copy stays neutral; only the progress indicator switches to terracotta once the self-set target is crossed. When unset, a quiet call-to-action.
6. **Where your time goes** (category distribution): total tracked, segmented proportional bar, labelled list (color dot + name + duration + %, never color alone). Tap through to category detail; "See all" leads to the deeper analytics destination.
7. **Most used** list: top 3–5 apps with icon, name, duration. Tap opens app detail.
8. Daily timeline (activity across the day).
9. One small reflection or observation (Literata, optional).

Charts here answer: *where did my time go today, and when?*

### 5.2 Insights

Sections: daily overview, weekly overview, monthly overview, app categories, usage rhythm, personal observations.
Each chart states its question as a caption (e.g., "Is my usage going up, down, or staying steady?"). No decorative charts.
Observations carry a subtle marker distinguishing **observed** from **interpretation** (see §7).

### 5.3 Focus

Sections: today's intention, focus sessions, app limits, quiet periods, active reminders.
Must **not** look like a control panel of restrictions. Prefer calm cards and plain language, nothing enabled by default, no red, no lock icons.

### 5.4 Apps

Searchable list. Each row: icon, name, today's usage, weekly usage, category chip, optional personal target.
Tap opens **App detail** (history chart, sessions summary, category, optional target, hide toggle).

### 5.5 Other screens

Welcome, Permissions, Category detail, Focus session (active), Intentions editor, Limits editor, Notification settings, Settings, Data management. Editors are simple bottom sheets or full screens with one clear primary action.

## 6. Onboarding

Short and transparent. Four setup steps, each labeled "Setup · n of 4":
1. **Introduction** — "Understand your attention. Find your balance." State what Sumi does and does not do.
2. **Privacy** — usage is processed and stored on this device only.
3. **Usage Access** — explain why Android requires it; button opens system settings. Provide a clear "back from settings" state.
4. **Initial synchronization** — calm progress, no spinner theatrics. **Setup completes here** (the flag is persisted when sync finishes).

After setup: **First overview** — a preview of the Today screen, headed "Setup complete / This is Today." It is visually distinct from Today (no tab bar, different copy) and its button only opens the app.

Do not ask for notification permission here.

## 7. Content and copy

### Voice
Calm, plain, kind. Questions over commands. No exclamation marks in system text.

### Never use
*wasted, addicted, excessive, too much, failed, overuse, "you exceeded", "warning", streak, score.*

### Prefer

| Instead of | Write |
|---|---|
| "You wasted 2h on YouTube" | "You spent 2h on YouTube today." |
| "Limit exceeded!" | "You've reached the time you set for social apps today." |
| "Take a break now." | "Would you like to take a break?" |
| "You failed your goal." | "Today went differently than you intended. Want to adjust?" |

### Observed vs. interpreted
- Observed facts are stated plainly: "Your longest uninterrupted session was 48 minutes."
- Interpretations use hedged phrasing and a distinct, quiet marker: "This might be an evening wind-down pattern."
- Never assert psychological conclusions.

### Empty and partial states
- No data yet: "Sumi starts learning your day from now. Come back later."
- Permission missing: explain what is paused, offer a single button to fix it; keep old data visible.
- Partial history: "Detailed history begins on <date>."

## 8. Data visualization

General rules:
- Every chart has a purpose stated in a caption.
- Muted palette, direct labels over legends where possible.
- Rounded bars, thin gridlines (`outline` token), no 3D, no gradients for data.
- Show comparisons neutrally; no "good/bad" colors.
- Provide accessible summaries (content description with the key takeaway) for each chart.

Chart set:
- **Daily total**: big number + previous-day comparison.
- **Distribution**: horizontal stacked bar or donut by category with labels.
- **Timeline**: 24-hour strip with usage intensity per hour.
- **Weekly bars**: 7 days, today highlighted with `accent`, average line.
- **App history**: bars over the last 7/30 days.
- **Usage rhythm**: hour-of-day heat strip.

## 9. Components (core/designsystem)

- `SumiTheme`, `SumiColors`, `SumiTypography`, `SumiShapes`
- `SumiCard`, `SumiSectionHeader`, `SumiLabel`
- `DurationText` (Plex Mono, tabular)
- `AppRow`, `CategoryChip`, `AppIcon`
- `SumiBottomBar`
- Chart primitives: `BarChart`, `StackedBar`, `TimelineStrip`, `HeatStrip`
- `EmptyState`, `PermissionState`, `LoadingState`
- `InsightCard` (with observed/interpretation marker)
- Bottom sheet editors

Every component ships previews for light, dark, large font scale, and loading/empty variants.

## 10. Notifications

- Channels: `reminders`, `focus`. Default importance low-to-default, no sound by default for gentle reminders.
- Copy follows §7. Maximum frequency bounded and user-adjustable; quiet periods respected.
- Notifications use the app's quiet icon; no colored urgency.
- Never used for re-engagement ("come back to Sumi").

## 11. Accessibility

- TalkBack labels for all controls and charts (with takeaway summaries).
- Contrast per §3.1; do not rely on color alone for category or state.
- Touch targets ≥ 48 dp; supports font scaling to 200%.
- Reduce-motion respected.
- Test light and dark in both, plus high-contrast system settings.

## 12. Do / Don't summary

**Do:** show before suggest, keep surfaces quiet, use soft tonal depth, write like a calm friend, make charts answer a question.

**Don't:** use red for usage, add streaks/badges, add celebratory animation, guilt the user, hide the off switch, stack dialogs, or turn the Focus tab into a wall of toggles.


---

## 14. Design Reference Standard v1.1 (2026-11-14) — adopted

The 2026-11-14 Design Reference Standard v1.1 (a pixel-level critique and implementation-ready
correction of the shipped screens, reviewed independently of this file) was adopted on branch
`design/reference-standard-v1.1`. This section is the delta from §2–§13; it supersedes the
values there where they conflict.

### 14.1 Color (both themes, canonical)

- Light: destructive red re-tokened to **`red-600 #A63A2B`** (was 4.49:1 against `#F4F1EA`;
  5.71:1 now). `ink #36382F`, `ink-secondary #565145` (darkened for 7:1), ground
  `#F4F1EA`, bottom-nav surface `#E8E6DA`. On-accent light `#F5F1E6`. Disabled pair
  `#DCD7CB` / `#6F695B`.
- Dark (measured, canonical): ground `#181F18` · **`surface-2 #273327`** shared by module
  cards **and the bottom navigation bar** · ink `#E8EEE3` · ink-secondary `#B1BAAE` ·
  accent fill **`#D2E4BC`** (primary buttons, selected chips, progress, links) · on-accent
  `#2B3824` · nav pill **`#E5EEE2`** · chart bars **`#B0BBAD`** (light counterpart
  `#565145`) · destructive `#D1A9A3` · disabled pair `#2A2F2A` / `#79806F`.
- Category hues were re-synced to the measured dark values (sage-green `#B1C39D`, cyan
  `#A5C5C2`, tan `#D8B49A`, lilac `#C1B2D3`, amber `#D8BD7E`, mid-green `#829874`);
  positional token names (`categoryColor1…6`) and the seed sync are unchanged. Category
  colors remain a redundant data layer (dots, bar segments, chart series only).
- Chart bars and analytic progress fills use the new **`chartBar`** extended token, not the
  accent. The intention ring and focus progress stay accent by design (action/affirmation).
- Dynamic color stays opted out. Deliberately.

### 14.2 Typography — the two-voice rule, codified

Serif = the voice of the product (root-tab headlines, narrative summaries, observations,
empty states, setup steps 1–2, completion). Sans = the voice of the system (operational
screens, Settings, steps 3+, body, buttons, chips, metadata, diagnostics). Serif is
Literata (as bundled); the sans stays **Inter** — the standard inferred Literata + Roboto
from pixels, but the repo already bundles Inter (SIL-adjacent, OFL-compatible role coverage),
so Inter remains the sans voice. IBM Plex Mono continues to carry tabular numerics.

`displayLarge` is now 34/42 sp; `bodyLarge` is 17/24 sp; `bodySmall` is 15/20 sp with 0.4 sp
label tracking; hero numerics (`NumericLarge`) are 30/38 sp with `tnum`. Screen margin is
canonically **24 dp** and `sourceHorizontal` enforces it.

### 14.3 Components and behavior adopted

- **Primary button** — 48 dp tall pill (was effectively 40 dp), full width, unmistakably
  inert disabled state (designed disabled pair, never stock Material gray).
- **Status indicator** — 20 dp icon + one `body-sm` line; affirmative (olive check-circle,
  "…is on."), neutral (outline circle, "…is off.", never red), attention (warning,
  destructive-adjacent only). Color-independence satisfied by construction.
- **Progress module** — 2 dp hairline track, accent fill, 1 polite live line beneath.
  Used by the setup import (disabled CTA while running; Retry link on failure; empty-history
  completion reads "Nothing to import yet — Sumi starts counting today.") and the export sheet.
- **Fact stack** — scannable fact + qualifier rows separated by 1 dp hairlines (setup 2).
- **Metadata/diagnostics rows** — label left, value right, tabular figures, optional status
  tone. Settings `DIAGNOSTICS` renders these.
- **Chips** — single-select pill, 40 dp, selected = accent fill + on-accent label, weight
  500, announces selected state. Insights time range (Today/Yesterday/7/30 days) and Focus
  durations use them. Apps category filter chips follow the same spec (category dot shown
  only when unselected).
- **Duration slider** — dotted `chartBar` track on a hairline baseline, 5-minute steps,
  48 dp touch band, synced with the preset chips, announced to TalkBack in minutes.
- **Category dot** — shared 10 dp decorative component; adopted by the distribution rows.
- **Bottom navigation** — surface-bar strip + 1 dp hairline edge; selected state triple-coded
  (indicator pill + filled icon + weight-500 label); **origin tab stays marked on the
  Settings stack** (bottom bar now visible there).
- **Top app bar** — root: serif wordmark left + trailing gear (48 dp); subscreen: back arrow
  **left**, no wordmark. The back arrow is never on the right.
- **Destructive flows** — "Delete all data" opens a 28 dp-radius dialog, title "Delete all
  data?", body states scope and irreversibility, actions stacked full-width (Cancel above
  Delete), Delete filled with the destructive token. ≥ 24 dp separation from Export.
  Export opens a sheet; success closes it and shows "Saved to Downloads." inline.
- **Privacy blurb** in Settings reduced to one line + a "See what Sumi does not do" link
  opening the shared bottom sheet (four claims + closing line).

### 14.4 Shape, spacing, motion

`r-md` is now 16 dp (module cards, menus), `r-sm` 12, `r-lg` 28 (sheets, dialogs), `r-full`
the primary button and switch track only. Rows/sections stay at `r-none`. Motion tokens are
documented in §3.4; reduced motion is honoured via the animator-scale checks already in
place (nav fade-through, chart entrances).

### 14.5 Deviations from the reference standard (deliberate, minimal)

- The sans voice stays **Inter**, not Roboto (§14.2).
- Onboarding keeps its fifth step ("Focus, gently" — optional once-a-day reminder), so the
  "Setup · N of 5" metadata reads 1–5, not 1–4. The standard's 4-step flow predates the
  reminder opt-in decision (see `ROADMAP.md`); the two-voice rule and band layout apply to
  the reminder step as well.
- TheCompletion screen is the existing `FirstOverview` route restyled to the standard's
  completion spec (serif "Ready.", "Enter Sumi").
- Chips keep the standard's 40 dp height (its own §9.15 spec); the global 48 dp floor
  applies to buttons, links, rows, nav items and icon buttons.
- The custom dotted-track slider styles an otherwise stock M3 slider (transparent track,
  accent thumb); a fully bespoke gesture layer was judged not worth the accessibility risk.

### 14.6 Not yet verified

Room migration tests, TalkBack behavior, and 200% font scale remain device/emulator-bound;
CI's Robolectric harness covers onboarding and Today states. The Apps tab light/dark review
and the light counterpart of `surface-2` (module cards on a light elevated surface) remain
open (the standard marks light-theme tabs unreviewed).


## 15. Home-screen widget (adopted 2026-11-14)

The widget design reference is canonical as `docs/WIDGET.md`; it is implemented as the
M6 milestone in `ROADMAP.md` and intentionally shares the app's visual DNA:

- Warm paper background (`#F4F1EA`) in light; deep forest (`#181F18`) in dark — the
  canonical `background` tokens, not wallpaper-derived tints.
- `SUMI` wordmark (sans caps, muted) over the dominant numeric total (bold sans,
  28–36 sp); supporting lines 12 sp muted; a thin 3 dp goal rule in `accentStrong`
  with a matcha-1 track. **No red/terracotta on the widget ever**, including over-goal.
- Default footprint **4×2** (canonical medium landscape); content centered on both axes;
  totals scale 20/30/40/44 sp by breakpoint (Slim/Compact/Medium/Large). A one-row slim
  tile is supported below 80 dp height (no bar; status drops under 210 dp).
- 16 dp corner radius (`r-md`); no nested cards, pills, borders, or charts.
- Theme follows the app preference (System resolves night mode at render time; forced
  variants are always forced). Wallpaper color is never consulted.
- No "Updated" timestamp, no Focus action, no configuration, no category summary.

**Deviations (deliberate):** the sans voice renders as the platform sans (standard
inferred Roboto), not Inter; RemoteViews offers no tabular-figure control, so durations
do not force tabular numerals; the over-goal state shows the actual total with neutral
wording (the in-app card may switch its rule to terracotta, the widget never does).
Widget pixels are hosted by the launcher; device rendering is unverified here.
