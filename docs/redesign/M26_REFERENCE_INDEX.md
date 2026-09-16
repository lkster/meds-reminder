# M26 REFERENCE INDEX — FINAL DESIGN FREEZE

Status: **FROZEN**

This index defines the authority and intended use of the selected M26 visual references. The package is tool-independent; **Figma is not required** to interpret or implement these designs.

---

## 1. Authority order

When sources disagree, use this order:

```text
1. M26_FUNCTIONAL_CONSTRAINTS.md
2. Explicit written decisions in M26_SCREEN_SPEC.md
3. Explicit written decisions in M26_DESIGN_SYSTEM.md
4. FINAL reference images
5. PROVISIONAL / SUPPORTING reference images
6. Incidental visual details in any generated mockup
```

Functional constraints outrank visual convenience. Written decisions outrank accidental pixels.

---

## 2. Final visual references

### `M26_REF_TODAY_FINAL.png`

Status: **FINAL**

Covers:

- Today refined/default view;
- expanded full-month calendar;
- dose quick-actions bottom sheet.

Authoritative visual intentions include:

- rounded-rectangle selected-day treatment;
- day-oriented Home hierarchy;
- dose cards grouped by time;
- light normal-app visual language;
- contextual quick-action sheet direction.

Do not infer that every quick action is already supported by the M0–M25 core; see functional constraints.

### `M26_REF_MEDICATIONS_FINAL.png`

Status: **FINAL**

Covers:

- medication list;
- medication details;
- edit medication;
- edit schedule target direction.

Authoritative visual intentions include:

- medication identity icons without circular colored backgrounds on the medication list;
- medication library role distinct from day/status views;
- no global medication dose in the details header;
- no dose field in Edit Medication.

Richer schedule controls may be future functionality.

### `M26_REF_HISTORY_FINAL.png`

Status: **FINAL**

Covers:

- populated chronological History;
- outcome filters;
- true History empty state.

Use written state-system rules for filter-empty/loading/error variants that were frozen later in Part 3.

### `M26_REF_ALARM_FINAL.png`

Status: **FINAL**

Covers:

- single-dose AlarmActivity;
- target multi-dose wizard visual direction.

Authoritative visual intentions include:

- fixed dark teal/navy alarm world;
- medication-first hierarchy;
- large primary Taken action;
- split Snooze control;
- clearly discoverable Skip;
- no giant top-level clock;
- no success/post-Snooze screen.

The visible speaker icon is illustrative and must not be treated as a mute-control requirement.

### `M26_REF_SETTINGS_READINESS_FINAL.png`

Status: **FINAL**

Covers:

- Settings main screen;
- Alarm Readiness all-ready state;
- Alarm Readiness needs-attention/limited direction;
- default Snooze selection sheet.

Use written Part 3 selection semantics for exact commit behavior and written readiness semantics for state computation.

---

## 3. Supporting visual reference

### `M26_REF_INTERACTIONS_PROVISIONAL.png`

Status: **PROVISIONAL / SUPPORTING ONLY**

Contains:

- History event details;
- Edit History entry;
- Change time for this dose;
- Alarm alternate-Snooze sheet.

This image is not pixel-perfect authority.

Mandatory overrides:

1. **History details sheet** must converge to the normal-app bottom-sheet language used elsewhere; do not preserve accidental spacing/container/handle differences from the provisional image.
2. **Alarm alternate-Snooze sheet** must be a flat dark teal/navy AlarmActivity surface. No white background and no gradient.
3. Written interaction semantics in `M26_SCREEN_SPEC.md` and `M26_FUNCTIONAL_CONSTRAINTS.md` override any differing detail in this image.

---

## 4. Part 3 additions without new canonical mockups

The following were explicitly frozen in writing and do not require additional image files to be canonical:

- loading/reload/empty/error/operation-in-progress/success state system;
- delete and unsaved-changes dialogs;
- weekday, Snooze, native sound, and medication-appearance picker contracts;
- normal-app Light/Dark strategy;
- fixed Alarm theme separation;
- accessibility, large-text, responsive, IME/inset behavior;
- cross-screen surface/action/navigation/iconography/copy/motion convergence rules.

Implementation must use the written freeze rather than assuming an unpictured state is undefined.

---

## 5. Tool independence

Figma was used experimentally during M26 but is **not** a required handoff medium, implementation dependency, or source of truth.

M27 and later work should be possible using only:

```text
M26_DESIGN_SYSTEM.md
M26_SCREEN_SPEC.md
M26_FUNCTIONAL_CONSTRAINTS.md
M26_REFERENCE_INDEX.md
reference/*.png
actual repository state
```

No paid Figma plan or Figma MCP access is required.

---

## 6. Reference directory

Expected package contents:

```text
docs/redesign/
    M26_DESIGN_SYSTEM.md
    M26_SCREEN_SPEC.md
    M26_FUNCTIONAL_CONSTRAINTS.md
    M26_REFERENCE_INDEX.md
    reference/
        M26_REF_TODAY_FINAL.png
        M26_REF_MEDICATIONS_FINAL.png
        M26_REF_HISTORY_FINAL.png
        M26_REF_ALARM_FINAL.png
        M26_REF_SETTINGS_READINESS_FINAL.png
        M26_REF_INTERACTIONS_PROVISIONAL.png
```
