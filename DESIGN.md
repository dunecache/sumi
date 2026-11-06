# DESIGN.md — Sumi

Visual, interaction, and content design for Sumi, built on the **Kanso Matcha** system
(Zen × Wabi-Sabi × Matcha). Companion docs: `PRINCIPLES.md`, `ROADMAP.md`.

> **Note on tokens:** the canonical Kanso Matcha v1.0 token spec lives in its own source. The values below are **provisional placeholders** that show the structure and intent. Replace them with the canonical tokens when wiring `core/designsystem`. Always reference tokens by name, never by hex, in feature code.

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
- Category colors come from a muted, harmonious palette of `categoryColor1…n` tokens (earth, moss, clay, stone, sky-gray, bamboo). They must be distinguishable in both themes and never rely on color alone (add label or pattern).
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
5. **Most used** list: top 3–5 apps with icon, name, duration.
6. Usage distribution (by category).
7. Daily timeline (activity across the day).
8. One small reflection or observation (Literata, optional).

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

Short and transparent:
1. **Introduction** — "Understand your attention. Find your balance." State what Sumi does and does not do.
2. **Privacy** — usage is processed and stored on this device only.
3. **Usage Access** — explain why Android requires it; button opens system settings. Provide a clear "back from settings" state.
4. **Initial synchronization** — calm progress, no spinner theatrics.
5. **First overview** — show real usage with a one-line guide to reading it.

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

