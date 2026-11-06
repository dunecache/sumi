# PRINCIPLES.md — Sumi

The product philosophy that governs every feature, screen, string, and notification.
When a design or implementation decision is unclear, resolve it here first.

> **Sumi** — A quiet companion for your digital life.
> *Understand your attention. Find your balance.*

## 1. The idea

Sumi combines two ideas:

- **Awareness** — understand how you spend your time across apps.
- **Intentionality** — make small, deliberate changes to your habits without feeling constantly monitored or restricted.

Sumi is not a task manager, productivity tracker, parental-control tool, or app blocker. It makes digital habits visible and offers tools to change them *when the user chooses*.

## 2. The problem we address

People spend a large share of their time on their phones without a clear picture of:

- which apps take their attention,
- when they use them,
- how often they switch,
- which use is intentional and which is habitual,
- whether their habits match their priorities.

Existing tools lean on numbers, restrictions, competitive streaks, or productivity framing. Sumi makes usage *understandable*, gives users control over their own boundaries, and encourages reflection without judgment.

## 3. Core principles

| # | Principle | Meaning |
|---|---|---|
| 1 | **Awareness before restriction** | Show what is happening before suggesting any change. |
| 2 | **Gentle intervention** | Never make the user feel guilty about their usage. |
| 3 | **Intentionality over productivity** | Entertainment and rest are valid uses of time. |
| 4 | **Privacy by default** | Usage information stays on the device. |
| 5 | **Simplicity with depth** | A simple surface, meaningful functionality underneath. |
| 6 | **User autonomy** | Every intervention is optional and controllable. |
| 7 | **Quiet design** | The app must not become another source of distraction. |

### How to apply each principle

**1. Awareness before restriction**
- Ship understanding (MVP, V1) before intervention (V1.5).
- A limit or reminder feature may only appear in a context where the user can already see the relevant usage.

**2. Gentle intervention**
- Notifications are suggestions, phrased as questions or neutral observations.
- No red, no warning icons, no "you exceeded" language for ordinary usage.
- Reaching a limit is information, not failure.

**3. Intentionality over productivity**
- Do not label categories as "good" or "bad". Entertainment is not a problem category.
- Three hours on a phone is not inherently wrong. Help the user distinguish learning, reading, communication, and habitual scrolling instead of treating all time as waste.
- Never rank users or apps by "productivity".

**4. Privacy by default**
- No server, no account, no analytics SDK, no advertising identifier.
- Export and deletion are user-controlled and easy to find.
- Explain every permission before requesting it, in plain language.

**5. Simplicity with depth**
- The most important information appears first. Detail lives one tap deeper.
- Every chart must communicate something useful. No decorative charts.
- Configuration is optional; the app is useful with zero setup beyond Usage Access.

**6. User autonomy**
- Every reminder, limit, and focus feature can be turned off.
- No mandatory daily goals. No forced blocking.
- The user decides what counts as a good day.

**7. Quiet design**
- A user should be able to open Sumi, understand their situation in a few seconds, and leave.
- No notification spam, no excessive animation, no unnecessary confirmation dialogs.
- Reminders are rare, and quiet periods are respected.

## 4. Honesty rules for insights

Insights must distinguish **observed facts** from **interpretations**.

- Fact: "Most of your social app usage happened between 20:00 and 23:00 this week."
- Fact: "Your longest uninterrupted session was 48 minutes."
- Interpretation (allowed only when clearly framed and supported): "This may be a wind-down habit." — use sparingly, optional, never presented as certain.
- **Not allowed:** unsupported psychological claims ("you are addicted", "you use your phone to avoid stress").
- When data is incomplete or an estimate (sessions, pickups), say so.
- Prefer comparisons that help the user decide, not that shame them.

## 5. Language and tone

- Calm, not sterile. Warm, not overly playful. Informative, not overwhelming.
- Plain words. Short sentences. No exclamation marks in system messages.
- Questions over commands: "Would you like to take a break?" not "Take a break."
- No judgmental terms: *wasted, addicted, excessive, too much, failed, overuse*.
- Numbers are shown in a human way: `3h 42m`, not `13320 seconds`.

## 6. Interaction rules

- No aggressive red warning states for ordinary usage.
- No guilt-inducing language.
- No mandatory daily goals.
- No streaks or punishment for missing goals.
- No excessive animation.
- No unnecessary confirmation dialogs (except destructive actions like deleting data).
- No notification spam.
- Every chart communicates something useful.

## 7. Scope discipline

Deliberately excluded from the initial releases:

| Excluded | Reason |
|---|---|
| AI assistant / AI-generated advice | Not necessary to deliver value |
| Cloud synchronization | Conflicts with local-first simplicity |
| Social features, leaderboards | Distract from personal awareness |
| Gamification (streaks, achievements) | Conflicts with the philosophy |
| Forced app blocking | Too restrictive |
| Complex automation | Premature |
| Account system | No need |
| Wearable integration | Not central |

New feature proposals are evaluated against §3. A feature that needs an exception to a principle is rejected or redesigned.

## 8. Success criteria

Sumi does **not** measure success by how much it reduces screen time. Success is:

- Can users understand their digital habits?
- Do they discover patterns they were not aware of?
- Can they set boundaries without frustration?
- Do they feel in control rather than judged?
- Does the app remain useful without notifications?
- Can they trust that their information stays private?

The most important measure: **does Sumi help users make more intentional decisions?**

## 9. Feature review checklist

Before accepting any feature or string, ask:

- [ ] Does it show before it restricts?
- [ ] Is it optional and reversible?
- [ ] Could any wording make the user feel judged?
- [ ] Does it respect that rest and entertainment are valid?
- [ ] Does it keep data on-device?
- [ ] Does it add noise (a notification, an animation, a dialog)? Is it worth it?
- [ ] Does each chart or number answer a real question?
- [ ] Is the interpretation clearly separated from the observation?

