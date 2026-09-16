# M26 SCREEN SPEC — FINAL DESIGN FREEZE

Status: **FROZEN**  
Scope: complete target-product screen and interaction specification after M26 redesign.

This document describes what the product should look and behave like at the UX level. Features that exceed the M0–M25 frozen domain are explicitly identified as target future functionality and must not be silently implemented as visual-only work.

---

## 1. Top-level information architecture

Primary bottom navigation:

```text
Dzisiaj | Leki | Historia
```

`Dzisiaj` is the default Home.  
`Settings` is a secondary full-screen destination opened from a gear affordance; it is not a fourth bottom-navigation tab.

Primary roles:

- **Dzisiaj** — day-oriented plan and outcomes for the selected date.
- **Leki** — medication library and schedule configuration.
- **Historia** — factual chronological results and, in target UX, correction of user-owned historical data.
- **Settings** — alarm sound, vibration, default Snooze, Alarm Readiness and related configuration.

---

# 2. DZISIAJ

Canonical reference: `reference/M26_REF_TODAY_FINAL.png`

## 2.1 Core content model

The content unit is a scheduled dose/intake, not a medication record.

If one medication is taken multiple times, it appears multiple times chronologically. Multiple doses at the same time share a time heading, but each dose remains its own item/card.

## 2.2 Collapsed calendar

Default presentation shows one week.

Rules:

- selected day uses a soft rounded rectangular highlight around weekday + date;
- horizontal week movement and selected-day changes are supported;
- subtle day-status markers may appear;
- calendar expansion must have a visible affordance; swipe may supplement but never be the only discovery mechanism.

## 2.3 Expanded calendar

The same screen expands to a month; it is not a separate Calendar tab.

When expanded:

- calendar receives more vertical space;
- selected-day visual language remains the same rounded rectangle;
- the dose list below becomes more compact/reduced-density;
- selecting a date updates the content below.

## 2.4 Day-status markers

Factual hierarchy:

```text
green check       -> all completed doses Taken
amber marker      -> at least one Skipped and no Timed out
red attention     -> at least one Timed out; outranks Skipped
neutral marker    -> future date has scheduled doses
no marker         -> no scheduled doses
```

Future doses on the current in-progress day do not create warning/error semantics.

No adherence score, streak, or “good/bad day” framing.

## 2.5 Dose cards

Use card-based scheduled-dose presentation under chronological time groups.

A compact daily summary/progress treatment may be shown as in the accepted reference, but it must remain factual and not become gamified scoring.

## 2.6 Dose quick actions

Tapping a dose opens a contextual bottom sheet.

Target actions:

- Mark as Taken;
- Change time for this dose;
- Skip this dose;
- Medication details;
- close/cancel.

`Change time for this dose` must explicitly state that the permanent schedule does not change.

Taken/Skip directly from Today and one-off rescheduling are **target future functionality** relative to the frozen M0–M25 core.

## 2.7 One-off time change — target interaction

Scope: exactly one scheduled occurrence.

Rules:

- initialize from the occurrence's planned time;
- allow the same calendar day or the next day only;
- explicitly support crossing midnight (e.g. 23:30 -> 00:30 next day);
- do not expose an arbitrary calendar picker for this quick action;
- when moving to the next day, state this explicitly, e.g. `Ta dawka zostanie przeniesiona na jutro, 00:30`;
- state that the permanent medication schedule remains unchanged;
- explicit `Zapisz` action.

This requires future occurrence/domain support.

## 2.8 Loading and empty states

Initial loading:

- keep top bar/date/calendar/bottom navigation shell;
- skeletonize only the summary and dose region.

Empty distinctions:

1. No medications configured -> stronger first-run state with `Dodaj pierwszy lek`.
2. Medications exist but selected date has no doses -> calm `Brak dawek w tym dniu` style state, without an oversized creation CTA.

A critical Alarm Readiness problem may surface as a compact contextual prompt, but must not force a large readiness panel into the established Today layout.

---

# 3. LEKI

Canonical reference: `reference/M26_REF_MEDICATIONS_FINAL.png`

## 3.1 Medication list

This is a medication library, not a day/status feed.

Rules:

- medication identity icon plus name;
- icon may use user-selected color;
- **no circular colored icon background on the medication list**;
- do not show Taken/Skipped/Timed-out semantics here;
- concise schedule summary may be shown;
- search field is accepted;
- FAB `+` is accepted for creation.

No-medications state uses an actionable first-run empty state. Search with no matches uses a local `Brak pasujacych lekow` style state and must not imply that the user should create a new record solely because a filter matched nothing.

## 3.2 Medication details

Header:

- medication identity icon;
- medication name;
- form/type.

Do **not** show one global medication dose in the header.

Body may include:

- general notes/information;
- schedules;
- add schedule;
- edit medication;
- delete medication.

Dose belongs to schedule rules.

## 3.3 Medication identity model

Target conceptual split:

```text
Medication
- name
- icon
- icon color
- form/type
- general notes

Schedule
- start
- recurrence
- one or more times
- dose
- end condition
```

If morning and evening require different doses, model them through appropriate schedule rules rather than a fake medication-wide dose.

## 3.4 Edit medication

Fields/concepts:

- medication icon/appearance;
- medication name;
- form/type;
- optional general notes;
- `Zapisz`.

Explicit exclusion: **no dose field**.

Saving keeps the form visible; action becomes `Zapisywanie...` and conflicting mutations are disabled while the established save contract completes.

If the user attempts to leave a dirty editor, use the unsaved-changes contract from `M26_DESIGN_SYSTEM.md`.

## 3.5 Medication appearance picker

Open one `Wyglad leku` sheet:

- live preview;
- curated medication icon grid;
- curated color swatches;
- `Gotowe`.

`Gotowe` applies appearance to the editor only. The medication is durably committed only by the editor's main `Zapisz`.

## 3.6 Edit schedule — target product direction

Target structure:

### Start
- start date.

### Recurrence
- `Dni tygodnia` multi-select;
- or `Co N dni`.

Weekday selection:

```text
Pn Wt Sr Cz Pt Sb Nd
```

Minimum one day is required when weekday recurrence is active. Large text may wrap chips to multiple rows.

### Times
- one or more times;
- add-time affordance.

### Dose
- dose for this schedule.

### End condition
- indefinite;
- end on date;
- for N days;
- after N scheduled intakes/doses.

Avoid inventory wording such as `after N tablets` when an intake may contain more than one tablet.

The richer schedule model is **future functionality** and may require domain/schema changes.

## 3.7 Delete medication

Use the frozen destructive dialog contract:

```text
Usun lek?
<name> zostanie usuniety z listy lekow i przyszle przypomnienia dla tego leku zostana anulowane.

Anuluj | Usun
```

During deletion:

- `Usun` -> `Usuwanie...` with local progress;
- disable both actions and outside dismissal;
- on failure, keep dialog open and show inline error;
- on success, return naturally to the medication list/details context with no separate success screen.

---

# 4. HISTORIA

Canonical reference: `reference/M26_REF_HISTORY_FINAL.png`

Supporting reference for details/correction: `reference/M26_REF_INTERACTIONS_PROVISIONAL.png` with the written overrides below.

## 4.1 List

Chronological list grouped by day.

Outcomes remain distinct:

- Taken;
- Skipped;
- Timed out / `Brak reakcji`.

Rows may show:

- medication identity;
- dose snapshot/relevant dose;
- scheduled time;
- actual result/intake time where meaningful;
- `After snooze` context.

History is factual, not gamified.

## 4.2 Filters

Simple status filters equivalent to:

```text
Wszystkie | Wziete | Pominiete | Brak reakcji
```

If History contains data but a filter returns zero matches, show a compact local `Brak wpisow dla tego filtra` state, optionally with a route back to `Wszystkie`. Do not reuse the global first-run empty state.

## 4.3 Global empty state

When History truly has no events:

- clearly state that History is empty;
- explain that resolved reminders will appear here;
- provide an action back to `Dzisiaj`.

## 4.4 History event details

Tapping an event opens a bottom sheet containing, when applicable:

- medication icon/identity;
- dose snapshot;
- scheduled date/time;
- final outcome;
- actual intake/resolution time;
- `After snooze` context;
- `Edit entry`;
- `Medication details`.

The bottom sheet must visually follow the common normal-app sheet language. Do not preserve accidental spacing/container differences from the provisional reference.

## 4.5 Edit History entry — target future functionality

Manually selectable intentional statuses:

```text
Taken
Skipped
```

`Timed out / No response` is system-originated and must not appear as a user-selectable intentional status.

A timed-out occurrence may nevertheless be corrected to Taken or Skipped.

When correcting to Taken, actual intake date/time may be edited.

Rules:

- explicit `Zapisz`;
- no additional `Are you sure?` confirmation;
- a snackbar such as `Wpis historii zaktualizowany` may confirm completion;
- clearly state that only this historical occurrence is changed;
- never silently alter the permanent medication schedule;
- do not add delete-history-event in this flow.

Cross-midnight rule:

> Actual intake may belong to the next calendar day without changing the identity of the historical occurrence. Identity follows the scheduled occurrence.

Editable History requires future domain support.

## 4.6 Loading/error

Initial loading keeps the History shell/filters and skeletonizes the list.

A list/read failure keeps the shell and replaces only the affected content region with an inline retry state such as `Nie udalo sie wczytac historii` + `Sprobuj ponownie`.

---

# 5. ALARMACTIVITY

Canonical reference: `reference/M26_REF_ALARM_FINAL.png`

## 5.1 Visual environment

AlarmActivity is:

- full-screen;
- fixed dark teal/navy;
- high-contrast;
- urgent but calm;
- lockscreen-appropriate;
- vertically scrollable for oversized/large-text content.

Medication identity is more important than clock time.

Do not introduce a giant clock as the dominant hierarchy.

Primary hierarchy:

```text
medication icon
-> medication name
-> dose for CURRENT actionable schedule/occurrence
-> instructions/context
-> resolution actions
```

The speaker icon visible in old mockups is illustrative and does not define a mute action.

## 5.2 Initial loading

Before authoritative current occurrence identity is resolved:

- remain in the fixed Alarm theme;
- show subtle indeterminate progress plus `Wczytywanie alarmu...` or equivalent;
- do not render guessed medication name/dose;
- do not show Taken/Snooze/Skip.

Resolution actions appear only after the actionable occurrence is authoritatively known.

## 5.3 Dose correctness

Display the dose for the currently actionable schedule/occurrence only. Do not show a medication-wide dose or aggregate across unrelated schedules.

## 5.4 Single-dose actions

Primary:

`Oznacz jako wziety` — large, filled, high emphasis.

Snooze:

split control approximately 4:1:

- large segment immediately applies configured default Snooze, e.g. `Drzemka 5 min`;
- compact `...` segment opens alternate duration selection.

Skip:

`Pomin te dawke` — full-width secondary/tertiary outlined action; it must remain clearly discoverable.

Do not permanently expose 5/10/15/30 chips on the main ringing screen.

## 5.5 Alternate Snooze sheet

Use a flat dark Alarm-theme sheet — no white background and no gradient.

Allowed values exactly:

```text
5 min
10 min
15 min
30 min
```

Identify the current default.

Tapping a value **immediately performs Snooze**; there is no `Gotowe` confirmation.

## 5.6 Completion behavior

For a single occurrence:

```text
Taken or Skip
-> AlarmActivity ends naturally
-> return to previous app / secure lockscreen according to established behavior
```

No praise screen, success screen, or extra `Dalej` click.

After Snooze, there is no special post-Snooze screen; the snoozed occurrence later uses normal AlarmActivity presentation again.

## 5.7 Same-time multi-dose wizard — target future functionality

Target visual model:

- one medication/dose at a time;
- progress at top, e.g. `2 z 3`;
- same central presentation as single-dose alarm;
- Taken/Snooze/Skip for current item;
- advance after current resolution;
- finish naturally when current applicable members are resolved;
- no final celebration/success screen.

Frozen Snooze semantics for this future model:

```text
current dose Snoozed
-> current occurrence is deferred
-> wizard immediately advances to next still-current group member
-> remaining current group members are resolved now
-> AlarmActivity ends when current applicable members are done
-> snoozed occurrence rings later as its own actionable occurrence
```

Snooze creates a new occurrence UUID.

If the later snoozed occurrence coincides with other doses, it may join a later applicable grouped presentation according to the eventual implementation model.

Critical rule: snoozing one member does not block handling the other current members.

The explicit grouped wizard/total model is a **future functional change** relative to the current sequential active-occurrence core.

---

# 6. SETTINGS

Canonical reference: `reference/M26_REF_SETTINGS_READINESS_FINAL.png`

## 6.1 Screen structure

Settings is a secondary full-screen destination:

- top app bar with Back;
- no bottom navigation;
- Alarm Readiness summary card near the top;
- `Alarm` section below.

Core existing alarm controls:

```text
Dzwiek alarmu / Alarm sound -> current label / system default
Wibracje / Vibration       -> switch
Domyslna drzemka           -> selected duration
```

Do not add unsupported controls merely to fill the screen.

## 6.2 Alarm sound

Open the native Android alarm-ringtone picker.

Display resolved sound name where available, otherwise stable fallback such as `System default` / `Selected alarm sound`.

## 6.3 Vibration

Simple on/off switch. No custom vibration-pattern editor.

## 6.4 Default Snooze

Selection sheet values exactly:

```text
5 min
10 min
15 min
30 min
```

Single selection plus `Gotowe`.

This is a settings selection interaction, not an immediate alarm-resolution action.

---

# 7. ALARM READINESS

Canonical reference: `reference/M26_REF_SETTINGS_READINESS_FINAL.png`

## 7.1 Summary states

### Ready

Required reliable-delivery capabilities are available and full-screen presentation is available.

### Limited

Reliable-delivery requirements are available, but full-screen presentation is unavailable. Actionable alarm notification behavior remains available.

### Needs attention

At least one required reliable-delivery capability is missing.

Required for reliable delivery:

- Notifications;
- alarm notification channel at required importance;
- Exact alarms.

Full-screen alarm capability is a degraded/limited capability, not a required-delivery blocker.

## 7.2 Detail rows

Show all four:

```text
Notifications
Alarm notifications / alarm channel
Exact alarms
Full-screen alarm
```

Each row has:

- state icon plus textual state;
- concise explanation;
- remediation CTA when useful.

Unresolved items receive higher visual priority than already-ready items.

Possible remediation labels include:

- Allow notifications;
- Open notification settings;
- Open alarm/channel settings;
- Enable exact-alarm access;
- Enable full-screen access.

## 7.3 Samsung guidance

On Samsung devices, preserve an informational card explaining that `Brief` notification presentation may hide quick alarm actions and `Detailed` may be preferable.

This vendor-specific setting does **not** participate in the computed Ready/Limited/Needs attention state because the app cannot reliably model it as a standard readable capability.

## 7.4 Visibility outside Settings

- settings gear may show a compact warning indicator when readiness requires attention;
- a critical required-capability issue may appear as a compact contextual prompt on Today;
- full-screen-only limitation must not generate an aggressive critical warning.

---

# 8. CROSS-SCREEN STATE AND OPERATION RULES

## 8.1 Operation in progress

Keep the current context visible. Replace the initiating action label with progress (`Zapisywanie...`, `Usuwanie...`) and block conflicting mutations.

Do not navigate or show success before the real operation completes.

## 8.2 Success

No dedicated success screens. Use a snackbar only when the completed result is not otherwise obvious.

## 8.3 Error scope

Prefer action snackbar -> section inline error -> full-content error while preserving shell, in that order of locality.

Do not default to modal error dialogs.

## 8.4 Bottom sheets/dialogs

Normal-app sheets share one visual language across Today, History, Settings, and medication editing. AlarmActivity sheets use the fixed Alarm theme.

---

# 9. CROSS-SCREEN CONSISTENCY FREEZE

Implementation should preserve:

- one shared surface hierarchy;
- one primary-action hierarchy;
- one semantic Back model;
- one disabled-state meaning;
- one spacing rhythm;
- one coherent icon family;
- one secondary-text hierarchy;
- distinct treatments for History outcome, medication configuration state, and Alarm Readiness;
- destructive red reserved for destructive/error semantics;
- simple system-like empty-state illustration/iconography rather than a separate cartoon style;
- restrained motion and no new global haptic language.

---

# 10. IMPLEMENTATION CLASSIFICATION SUMMARY

The following are primarily redesign/presentation of already-supported concepts:

- medication list/loading/empty presentation;
- History list/loading presentation;
- AlarmActivity visual redesign and initial loading presentation;
- Settings information architecture;
- native alarm sound selection presentation;
- vibration switch presentation;
- existing default Snooze setting presentation (5/10/15/30);
- Alarm Readiness presentation;
- normal-app Light/Dark theme and fixed Alarm theme;
- dialogs/state/picker visual language where backed by existing operations.

The following are target-product UX and require later functional/domain review before implementation if the repository does not already support them:

- day-plan/calendar occurrence generation and full month calendar;
- Today Taken/Skip actions outside AlarmActivity;
- one-off occurrence rescheduling;
- editable History outcome and actual-intake date/time;
- richer schedule model (start date, every-N-days, dose per schedule, multiple times, end conditions);
- multiple schedules per medication where necessary;
- grouped same-time AlarmActivity wizard with explicit group total/progress.
