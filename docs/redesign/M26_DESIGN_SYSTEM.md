# M26 DESIGN SYSTEM — FINAL DESIGN FREEZE

Status: **FROZEN**  
Milestone: **M26 — Product-wide Visual / UX Redesign Definition**  
Implementation milestone: **M27+**

This document is the canonical visual and interaction-system contract for the Meds Reminder redesign. It is intentionally tool-independent. Figma is **not** a required medium, source of truth, or dependency for implementation. The written freeze plus the selected reference images are sufficient for planning and implementation.

When this document conflicts with an incidental detail in a mockup, the written rule wins.

---

## 1. Product character

The product should feel:

- calm, modern, precise;
- friendly but not childish;
- assistive rather than punitive;
- factual rather than gamified;
- visually restrained rather than decorative;
- optimized for clarity under time pressure and large-text accessibility.

The main application uses light neutral surfaces with a restrained teal/green accent family. Avoid corporate-blue styling, highly saturated electric accents, wellness-gamification motifs, streaks, adherence scores, celebration screens, and moralized “good/bad” framing.

AlarmActivity is deliberately a separate urgent visual environment: dark teal/navy, high contrast, calm but unmistakably actionable.

---

## 2. Information hierarchy principles

Across the product:

1. Primary content and identity come before metadata.
2. A screen should expose one clearly dominant primary action when one exists.
3. Secondary text explains, contextualizes, or timestamps; it must not compete with the primary label/value.
4. Status meaning must never rely on color alone.
5. Medication identity uses medication name plus user-selected icon/color where useful, but the color is never the only carrier of meaning.
6. Historical information is factual, not scored.
7. User-owned data should be correctable with clear scope rather than artificially locked.

---

## 3. Semantic color system

Implementation should use semantic tokens rather than component-specific raw colors.

Minimum token families:

```text
background
surface
surfaceElevated
surfaceContainer
surfaceContainerHigh
textPrimary
textSecondary
textDisabled
borderSubtle
accent
accentContainer
onAccent
success
warning
error
onError
```

Each normal-app token has a Light and Dark value. Components consume semantic tokens, not hard-coded per-screen colors.

### 3.1 Normal application themes

The normal app follows the Android system appearance:

```text
System Light -> App Light
System Dark  -> App Dark
```

M26 explicitly does **not** add:

- an app-level Light / Dark / System theme selector;
- Material You dynamic color as the product palette authority;
- a separate pure-black / AMOLED mode.

The palette should remain deterministic across devices.

### 3.2 Dark mode

Dark mode is not a literal inversion of light mode.

Use several near-black / very dark neutral-teal surfaces to preserve hierarchy:

```text
background
-> surface
-> card/container
-> elevated sheet/dialog
```

Do not use pure black as the default application background. Avoid bright floating cards and heavy shadows. Prefer subtle surface differences, borders, and spacing.

The dark-mode accent remains teal but may be adjusted lighter for contrast. It must not become neon.

### 3.3 AlarmActivity theme

AlarmActivity has a **fixed Alarm theme** and does not inherit normal-app Light/Dark appearance.

```text
App Light
App Dark
Alarm Fixed Dark
```

The alarm theme remains the accepted dark teal/navy environment regardless of system appearance.

Alarm-related sheets that belong to AlarmActivity, including alternate Snooze selection, inherit the Alarm Fixed Dark palette.

### 3.4 Status colors

`success`, `warning`, and `error` require theme-aware variants. Error/destructive red is reserved for:

- destructive actions;
- real failures;
- critical readiness problems when they actually block reliable behavior.

Do not use red as general decoration or for ordinary selected states.

### 3.5 Medication colors

Medication colors come from a curated, bounded palette rather than a free-form color wheel. Aim for roughly 8–10 clearly distinguishable accent choices.

A medication color should have theme-aware usage variants, for example:

```text
medicationBlueAccent
medicationBlueContainerLight
medicationBlueContainerDark
```

Selection must be indicated by more than color, e.g. check mark plus outline and accessibility state.

---

## 4. Surface and elevation language

Normal application hierarchy:

```text
background
-> surface
-> container/card
-> elevated sheet/dialog
```

Use rounded cards/components, subtle borders, and restrained elevation. Avoid heavy drop shadows.

Bottom sheets and dialogs should feel like part of the same component family across Today, History, Settings, and medication flows.

AlarmActivity is the intentional exception and uses its own fixed dark surface language.

---

## 5. Spacing and density

The product should feel calm and readable, not maximally dense.

Use shared spacing tokens in implementation rather than screen-specific magic numbers. The intended rhythm is:

- small spacing within a tight logical group;
- medium spacing between related elements;
- larger spacing between sections.

Do not allow Today to become spacious while History or Settings become visibly compressed. Cross-screen density should feel related.

---

## 6. Typography and text behavior

Important text may wrap. Do not preserve a screenshot by shrinking typography or truncating critical copy.

Do not arbitrarily ellipsize:

- medication names needed for identification;
- AlarmActivity critical instructions/actions;
- readiness problem descriptions;
- validation/error text;
- dialog copy needed for a decision.

Ellipsis is acceptable only when the full value is non-critical and remains discoverable elsewhere.

Target contrast requirements:

- normal text: at least 4.5:1;
- large text: at least 3:1;
- important non-text controls/icons and their boundaries: 3:1 where applicable.

Actual token contrast must be validated during implementation.

---

## 7. Responsive and accessibility contract

The design must remain functional with larger system font scales, narrower screens, long Polish strings, system bars, display cutouts, and the IME.

### 7.1 Reflow

Prefer, in order:

1. wrap/reflow;
2. vertical stacking;
3. scrolling;

Do **not** solve crowding by reducing font size, shrinking touch targets, or hiding actions.

Horizontal controls such as weekday chips, snooze controls, header actions, and dialog buttons may reflow to multiple rows or vertical layouts.

### 7.2 Touch targets

Interactive hit areas should be at least **48 x 48 dp**, even when the visible icon is smaller.

### 7.3 Accessibility semantics

Visual abbreviations use complete accessibility labels:

```text
Pn -> Poniedzialek / Monday equivalent in active locale semantics
trash icon -> Remove medication
```

Use state semantics (`selected`, `disabled`, etc.) where appropriate. Avoid generic labels such as “Button” when the action can be named.

Decorative icons/images should be removed from the accessibility tree when adjacent text already carries the full meaning.

### 7.4 Focus/read order

Screen-reader order must match logical reading order, not incidental Compose implementation order.

AlarmActivity order should prioritize:

```text
alarm/current occurrence context
-> medication
-> dose/time/instructions
-> primary resolution actions
-> secondary actions
```

### 7.5 Selected states

Selected/unselected states may not differ by color alone. Use a combination of shape, check, outline, text, iconography, or semantic state.

### 7.6 Orientation and insets

Portrait is the primary design target. Landscape does not require a separate fully optimized design, but it must remain usable through graceful reflow/scrolling.

System bars, cutouts, and IME insets are part of layout correctness. Forms must not hide fields or Save actions behind the keyboard.

### 7.7 AlarmActivity large text

AlarmActivity content remains vertically scrollable when oversized. Existing occurrence-UUID ownership and scroll reset per occurrence UUID must be preserved.

---

## 8. Navigation and action hierarchy

Primary bottom navigation:

```text
Dzisiaj | Leki | Historia
```

Settings is a secondary full-screen destination and is not a fourth bottom-navigation tab.

### 8.1 Back behavior

Back behavior is semantically unified across toolbar back, system Back, and gesture Back:

```text
open sheet -> dismiss sheet
open dialog -> dismiss/cancel dialog
changed editor -> unsaved-changes handling
normal secondary screen -> navigate back
```

### 8.2 Primary actions

The most obvious next action should have the strongest emphasis.

Examples:

- editor -> `Zapisz`;
- no-medications empty state -> `Dodaj pierwszy lek`;
- destructive confirmation -> `Usuń` with destructive styling;
- settings picker -> `Gotowe`.

Do not present multiple equally dominant primary buttons.

A FAB may remain structurally present on an empty medication list, but it should not visually compete with the empty-state CTA.

### 8.3 Disabled controls

Disabled state means an action is currently unavailable. It is not an error explanation.

If Save is disabled because validation fails, the user must be able to understand why from local validation state.

---

## 9. State system

### 9.1 Initial loading

Keep the stable screen shell when known and load only the data-dependent region.

Prefer local skeletons/progress over replacing a full screen with a centered spinner.

Examples:

- Today keeps header/date/navigation and skeletonizes summary/dose content;
- History keeps header/filters and skeletonizes the list;
- medication/settings secondary screens retain their known shell.

AlarmActivity is the exception: before authoritative occurrence resolution, show a dedicated Alarm-theme loading state such as `Wczytywanie alarmu...` plus subtle indeterminate progress. Do not render guessed medication data or resolution actions.

**Resolution actions must never appear until the currently actionable occurrence is authoritatively known.**

### 9.2 Reload/refresh

Do not clear already-visible authoritative content merely because it is being re-read. Preserve content and show only local progress if needed.

### 9.3 Empty

Empty is a normal product state, not an error.

Distinguish:

- no medications configured -> strong first-run state plus `Dodaj pierwszy lek`;
- medications exist but selected day has no doses -> calm `Brak dawek w tym dniu` style state;
- History truly has no events -> global History empty state;
- History filter has zero matches -> compact local `Brak wpisow dla tego filtra` state;
- medication search has zero matches -> local search-empty state, not a prompt to create a new medication.

### 9.4 Errors

Show a failure at the smallest useful scope:

```text
action failure -> snackbar when context remains clear
section/data failure -> inline error state
whole content failure -> content-level error while preserving the stable shell
```

Do not use modal error dialogs as the default `try/catch` presentation.

### 9.5 Operation in progress

Keep context visible. Block conflicting mutations and show progress at the action that initiated the operation.

Example:

```text
Zapisz -> Zapisywanie...
Usuń  -> Usuwanie...
```

Do not navigate away or visually announce completion before the real operation contract completes.

### 9.6 Success

Do not create dedicated success screens.

If the result is already obvious from UI state, no snackbar is required. Use a transient snackbar when it resolves genuine ambiguity, e.g. `Wpis historii zaktualizowany` after a history correction.

Alarm resolution never adds a praise/celebration step.

---

## 10. Dialog and destructive-operation contract

Use a modal dialog only when the user must make a consequential decision, especially destructive or unsaved-change decisions.

Do not use dialogs for ordinary loading, success, empty states, or generic errors.

### 10.1 Delete medication

Target copy structure:

```text
Usun lek?
<Medication name> zostanie usuniety z listy lekow i przyszle przypomnienia dla tego leku zostana anulowane.

Anuluj | Usun
```

Rules:

- destructive action uses destructive styling;
- no typed-name confirmation;
- no second confirmation layer;
- while deleting, action becomes `Usuwanie...` with local progress;
- disable both actions and outside-tap dismissal during the committed operation;
- on failure, keep the dialog open and show an inline message such as `Nie udalo sie usunac leku. Sprobuj ponownie.`;
- after failure, restore `Anuluj | Usun`.

### 10.2 Unsaved changes

Only show when the editor is actually dirty.

Target structure:

```text
Odrzucic zmiany?
Wprowadzone zmiany nie zostana zapisane.

Kontynuuj edycje | Odrzuc
```

Toolbar back, system Back, and gesture Back use the same semantics.

### 10.3 Large text

Dialog content may scroll. Buttons may reflow vertically. Do not shrink or truncate decision labels merely to preserve a horizontal reference layout.

---

## 11. Selection and picker contract

Different option sets do **not** imply identical commit semantics.

### 11.1 Weekday selection

Visual row/chips:

```text
Pn Wt Sr Cz Pt Sb Nd
```

Rules:

- Monday-first in Polish locale;
- independent multi-select chips;
- minimum one selected weekday when weekday recurrence mode is active;
- local validation, not a dialog/snackbar;
- large text may wrap chips into multiple rows;
- visual abbreviations have full accessibility labels;
- choices commit with the parent schedule `Save`.

Recurrence mode offers:

```text
Dni tygodnia | Co N dni
```

Do not invent separate semantic domain modes for `Codziennie`, `Weekend`, or `Dni robocze`; they may be conveniences later, not new data models.

### 11.2 Default Snooze in Settings

Use a normal-app selection sheet with exactly:

```text
5 min
10 min
15 min
30 min
```

One value is selected. `Gotowe` finishes the sheet interaction.

No arbitrary custom duration field.

### 11.3 Alternate Snooze in AlarmActivity

Use the same allowed durations but Alarm-theme styling.

Current default is visibly identified, e.g. `5 min · domyslna`.

Tapping an alternate value **immediately performs Snooze**. There is no `Gotowe` or additional confirmation.

### 11.4 Alarm sound

Use the native Android alarm-ringtone picker.

Display the resolved label where possible, otherwise a stable fallback such as `System default` or `Selected alarm sound`.

Do not add a custom ringtone catalog, custom file upload, custom volume slider, or `Silent` mode in M26.

### 11.5 Medication appearance

Use one `Wyglad leku` sheet rather than separate icon and color screens.

Structure:

```text
preview
Ikona -> curated grid
Kolor -> curated swatches
Gotowe
```

`Gotowe` commits appearance changes into the medication editor; the medication itself is not durably saved until the editor's main `Zapisz` completes.

Icon set: a small curated medication-form vocabulary (tablet/pill, capsule, bottle/liquid, drops, inhaler, syringe/injection, patch, cream/tube, neutral fallback). Do not expose hundreds of unrelated Material icons.

The exact asset library may be selected during implementation as long as the above conceptual scope is preserved.

### 11.6 Sheet scaling

A picker/sheet does not become full-screen merely because font scale is large. Prefer taller/scrollable sheets and reflow first. Native Android/system pickers may use their own presentation.

---

## 12. Iconography

Use a coherent, simple icon family. Avoid arbitrary mixing of filled and outlined variants.

Filled/outlined differences may support selected state where useful, but should not become a rigid rule that creates inconsistency.

Medication-list icons are shown **without circular colored background containers**. The icon itself may use the user-selected color.

---

## 13. Readiness visual semantics

Alarm Readiness is informative, not gamified. Avoid a score metaphor such as `3/4 ready` as the main product framing.

Each unresolved capability should identify what is wrong and, when useful, what action can remediate it.

Do not visually conflate:

- history outcome (`Taken`, `Skipped`, `Timed out`);
- medication configuration state (`enabled/disabled`);
- readiness state.

These are different semantic systems and should not use indistinguishable badges/treatments.

---

## 14. Motion and haptics

M26 defines only restrained motion:

- short, calm transitions;
- fade/expand/collapse for state changes where useful;
- natural platform sheet/dialog motion;
- no bounce, celebration, or decorative urgency animation;
- skeleton/loading animation must be subtle.

Do not add a new global haptic layer to chips/buttons/toggles. Existing alarm haptics remain governed by the ringing behavior/service contract.

---

## 15. Copy tone

UI copy should be short, neutral, concrete, and non-technical unless a system term is needed for remediation.

Prefer:

`Nie udalo sie zapisac leku. Sprobuj ponownie.`

Avoid database/platform implementation jargon in ordinary errors.

Readiness may use Android/system terminology when the user must recognize the same setting in system UI.

---

## 16. Rejected directions — do not reintroduce

Do not reintroduce:

- corporate-blue or highly saturated electric-blue primary styling;
- childish wellness/gamification treatment;
- circular/oval selected-day treatment replacing the accepted rounded rectangle;
- medication library as the Home screen;
- Taken/Skipped/Timed-out icons on medication-library cards;
- circular colored background containers behind medication icons on the medication list;
- global medication dose in Medication Details header;
- dose field in `Edytuj lek`;
- giant clock as AlarmActivity's main hierarchy;
- permanently visible Snooze duration chips on AlarmActivity;
- post-Snooze screen;
- success/congratulations screen;
- grouped medications displayed as a simultaneous list on the ringing screen;
- white/light alternate-Snooze sheet inside AlarmActivity;
- app-level alarm volume slider;
- inferred mute control from the incidental speaker icon;
- custom vibration patterns.

---

## 17. M26 governing rule

> **Visual redesign must not silently redefine functional ownership or persistence semantics.**

M26 may change hierarchy, layout, spacing, color, presentation, and discoverability. It may specify target future UX when explicitly marked as such. It does not silently replace Room authority, scheduling ownership, occurrence identity, ringing-service ownership, or other frozen functional contracts.
