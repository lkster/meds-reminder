# M26 FUNCTIONAL CONSTRAINTS — FINAL DESIGN FREEZE

Status: **FROZEN**  
Purpose: prevent the M26 visual/UX redesign from silently changing the proven M0–M25 functional foundation.

This document is implementation-facing. A visual reference or screen spec does not override these constraints.

---

## 1. Frozen repository baseline

Expected baseline at M26 design start:

```text
repository: lkster/meds-reminder
branch: master
expected application HEAD: 4baf79d7aaf2cc7222a99d62e6da4739ee596fec
versionName: 0.24-m24
versionCode: 2
Room schema: 2
```

M0–M24 are complete. M25 found no pre-redesign correctness/reliability blocker. The functional base is therefore considered **FUNCTIONALLY FROZEN** for the purposes of M26.

M27 planning must verify the real repository state rather than trusting these identifiers blindly if the repository has moved since the freeze.

---

## 2. Non-negotiable preservation rules

Unless a later milestone explicitly approves a functional/domain change, preserve all of the following:

- **Room is the durable logical authority.**
- **AlarmManager is a rebuildable scheduling projection**, not the durable domain source of truth.
- Medication ID, Reminder/Schedule ID, and Occurrence UUID remain distinct identities.
- Snooze creates a **new occurrence UUID**.
- Editor Save preserves the established **Phase A Room commit -> Phase B projection/reconciliation** behavior.
- Medication mutation/edit/delete behavior remains **stable-ID based**.
- Medication-list pre-emission/loading state is distinct from an authoritative empty list.
- History has its own independent loading boundary.
- AlarmActivity's current Room occurrence UUID remains the actionable authority.
- Notification payload data is not domain authority.
- AlarmRingingService owns audio, audio focus, vibration, wake lock, foreground-service state, notification, timeout, and cleanup.
- Only one ringing occurrence currently owns active presentation at a time in the frozen core.
- Direct Boot remains unsupported.
- Taken / Skip / Snooze end with the established natural return behavior; do not force MainActivity to foreground afterward.
- Redesign does not introduce backend, account, cloud sync, ads, or analytics.

A layout change may not weaken these contracts.

---

## 3. AlarmActivity authority rules

### 3.1 Initial resolution

Before the authoritative actionable occurrence is resolved:

- do not show actionable Taken/Snooze/Skip controls;
- do not trust notification text as a substitute for current domain state;
- use the fixed Alarm-theme loading state.

### 3.2 Scroll/reset behavior

Large/overflowing AlarmActivity content remains scrollable. Scroll position resets appropriately per occurrence UUID, including transitions to a different active occurrence.

### 3.3 Ringing ownership

Visual redesign must not move audio/vibration/wake-lock/notification lifecycle ownership into the Activity or arbitrary Compose UI state. Those remain RingingService responsibilities.

### 3.4 Completion

For a single occurrence, Taken or Skip ends AlarmActivity naturally under the established return behavior. There is no new success screen and no mandatory MainActivity launch.

---

## 4. Save and mutation ownership

UI state such as `Zapisywanie...` or `Usuwanie...` is presentation of an existing operation, not a new persistence model.

The UI must not visually declare success, dismiss a critical operation surface, or navigate as though complete before the actual authoritative mutation contract has completed.

Delete and edit operations remain ID-based; redesign must not regress to position/index-based list mutations.

---

## 5. Current supported alarm settings

The frozen repository already supports the following and M26 may redesign their presentation without treating them as new domain features:

- selected alarm sound URI;
- Android system default alarm-sound fallback;
- native Android alarm-ringtone picker;
- vibration enabled/disabled;
- default Snooze duration;
- allowed default Snooze values exactly **5 / 10 / 15 / 30 minutes**;
- default Snooze = **5 minutes**;
- notification readiness;
- alarm notification-channel importance readiness;
- exact-alarm access readiness;
- full-screen-intent readiness;
- Samsung Brief vs Detailed notification-presentation guidance.

Do not add unsupported controls such as app alarm-volume slider, custom vibration pattern, or mute lifecycle control.

---

## 6. Alarm Readiness semantics

Readiness is a product projection of existing capability checks.

Computed states:

### Ready

Required reliable-delivery capabilities are available and full-screen presentation is available.

### Limited

Required reliable-delivery capabilities are available but full-screen presentation is unavailable.

### Needs attention

At least one required reliable-delivery capability is missing.

Required capabilities:

- notifications;
- alarm notification channel at required importance;
- exact alarms.

Full-screen access is **limited/degraded**, not a required reliable-delivery blocker.

Samsung Brief/Detailed guidance is informative only and is not part of this computed state.

---

## 7. Existing Snooze semantics

Allowed durations remain:

```text
5 min
10 min
15 min
30 min
```

The main AlarmActivity default Snooze action executes the configured duration. Alternate duration selection executes Snooze immediately after the user chooses a value.

Snooze produces a new occurrence UUID and must preserve occurrence identity separation.

---

## 8. Target grouped same-time wizard — explicit future change

M26 freezes target UX semantics, but this does not mean the frozen M0–M25 core already supports group ownership/progress.

Target semantics:

```text
current group member Snoozed
-> defer that member as a new occurrence UUID
-> advance immediately to the next still-current member
-> resolve remaining current members now
-> finish AlarmActivity when current applicable members are resolved
-> deferred occurrence rings later as its own actionable occurrence
```

The current core remains sequential single-active-occurrence ownership. Introducing an explicit grouped total/progress model therefore requires a later implementation/domain review.

---

## 9. Historical data semantics

Frozen core History outcomes remain distinct:

- Taken;
- Skipped;
- Timed out / No response;
- After-snooze context where applicable.

M26 target correction semantics are **future functionality**:

- user may intentionally select Taken or Skipped;
- Timed out is system-originated and not an intentional manual status;
- a timed-out event may be corrected to Taken or Skipped;
- Taken correction may include actual intake date/time;
- changing one historical occurrence must not change the medication's permanent schedule;
- actual intake may cross midnight while occurrence identity still follows the original scheduled occurrence;
- no delete-history-event action is introduced by M26.

These changes may require persistence/domain work and must be planned explicitly.

---

## 10. One-off occurrence rescheduling — future functionality

Target contract for `Change time for this dose`:

- scope is exactly one occurrence;
- initialize from current planned occurrence time;
- allow same day or next day only;
- explicitly support midnight crossing;
- no arbitrary date picker for this quick operation;
- permanent schedule remains unchanged;
- explicit Save.

This is not a visual-only change. It requires occurrence/domain scheduling support and must be planned as such.

---

## 11. Richer medication schedule model — future functionality

M26 target UX separates medication identity from schedule-specific dose/rules.

Target schedule capabilities include:

- start date;
- selected weekdays;
- every-N-days recurrence;
- one or more times;
- dose per schedule;
- end indefinitely;
- end by date;
- end after N days;
- end after N scheduled intakes/doses;
- multiple schedules per medication where different dose/rules require them.

Do not fake these capabilities in UI if the data model cannot yet represent them. They may require Room schema/domain changes and dedicated migration/planning work.

---

## 12. Day plan and calendar — future functionality warning

The target Today screen includes scheduled occurrences across a selected day/week/month and factual day-status markers.

If the current repository does not already materialize/query the necessary occurrence model, M27 must not treat the calendar as a simple cosmetic replacement for the medication list.

Potentially new capabilities include:

- day-plan/occurrence generation;
- full month calendar backed by scheduled occurrences;
- factual status aggregation per day;
- Today Taken/Skip actions outside AlarmActivity.

These require explicit repository-backed planning.

---

## 13. Theme implementation constraints

Normal app follows system Light/Dark. AlarmActivity uses a fixed Alarm theme.

This is a presentation-system change and must not change alarm lifecycle ownership or domain state.

M26 does not require Figma, a design-export pipeline, Material You dynamic color, a theme selector, or an AMOLED mode.

---

## 14. Error/loading state constraints

Loading and failure presentation must respect existing authoritative boundaries:

- medication list not-yet-emitted != authoritative empty;
- History loading is independent;
- AlarmActivity unresolved occurrence != empty/invalid medication;
- stale notification payload must not substitute for Room authority.

A skeleton is presentation only; it must not invent or expose guessed domain data.

---

## 15. M27 planning rule

M27 should first classify each planned change as one of:

```text
A. visual/component/theme refactor preserving current behavior
B. interaction/presentation of already-supported behavior
C. new domain/persistence/scheduling capability
```

Category C work must be explicitly planned, tested, and migrated as functional work. Do not smuggle Category C into a “redesign implementation” diff simply because M26 already defined its target UX.

The redesign may be implemented incrementally as long as each slice preserves the frozen functional contracts and converges toward the final M26 screen/design specifications.
