# M30 implementation status

## Implemented

M30 redesigns the medication editor with the established normal-app Material presentation: a
safe-drawing, IME-aware scrolling secondary-screen shell, toolbar Back, medication and reminder
cards, responsive reminder time/remove actions, and accessible heading and pane semantics.

The editor presents only existing facts. `instructions` is labelled **Instructions / notes** and
continues to persist the existing field; it is not a dose. Existing reminder times and weekday masks
remain individually editable, including existing validation and history-cascade explanation. Save
accurately presents Room saving, alarm completion, pre-commit retry, and completion-only retry after
a post-commit failure. Failure messages remain polite live regions while progress is not one.

## Partial / transitional

- Medication and reminder editing remain combined because the richer M26 schedule model is not implemented.
- The existing medication-wide enabled state remains the only enablement control.
- `instructions` remains the existing persisted field.
- Idle editor exit still discards without an unsaved-changes dialog.

## Deferred Category A/B

- Unsaved-changes dialog.
- Medication Details.
- Delete-dialog committed-operation presentation.
- History details and read-error presentation.
- AlarmActivity and fixed Alarm-theme redesign.
- Remaining shared visual and copy convergence.

## Deferred Category C

- Today/day-plan functionality and one-off occurrence rescheduling.
- Medication appearance/form persistence.
- Schedule-specific dose.
- Richer recurrence, start/end, and multiple-schedule rules.
- Editable History.
- Grouped same-time alarms.

## Validation

- `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`: PASS.
- `git diff --check`: PASS.
- Connected `MedicationScreensTest` on `Medium_Phone(AVD) - 14`: PASS (31 tests).
- Connected `MainActivityPaneSemanticsTest` on `Medium_Phone(AVD) - 14`: PASS (1 test).
- Connected `MedicationEditorLifecycleTest` on `Medium_Phone(AVD) - 14`: PASS (8 tests).
