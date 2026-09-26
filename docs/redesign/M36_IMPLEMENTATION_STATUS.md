# M36 implementation status

Selected surface: Medication Editor

Primary reference: `reference/M26_REF_MEDICATIONS_FINAL.png` (screens C and D)

## Implemented visual changes

- Rebuilt the editor into a fixed compact header, a separately scrolling form body, and a fixed Save/status region. Safe-drawing remains on the outer editor viewport; IME padding now constrains that viewport rather than becoming scroll-content padding.
- Set MainActivity's manifest soft-input mode to `adjustResize` so the system exposes IME space to that viewport; MainActivity navigation and ownership code are unchanged.
- Flattened the medication form into external field labels, locally rounded outlined fields, a taller multiline notes field, and a standalone enabled-reminders row.
- Replaced card-like reminder blocks with compact shallow time rows, contextual remove icon actions, a per-reminder weekday selector, and semantic group spacing.
- Kept Every day as its own convenience chip and moved Monday through Sunday into one naturally wrapping FlowRow.
- Restyled Add reminder time as a low-emphasis full-width container action and kept Save as the dominant, strongly rounded, full-width fixed action.
- Moved progress, Room failure, and post-commit failure/retry presentation into the fixed action region without changing their established semantics.
- Applied the bounded post-review correction: selected Every day and weekday `FilterChip`s now use the editor-local `primaryContainer` / `onPrimaryContainer` mint-teal roles. Unselected chip styling and all chip behavior remain native Material3 defaults.

## Intentional reference deviations

- No medication appearance, form, dose, note character counter, start/end date, recurrence expansion, or separate schedule route was added because the current domain does not support those concepts.
- Each reminder retains its own weekday mask and selector; the UI does not fabricate a medication-wide recurrence.
- The native `TimePickerDialog` remains the time editor.

## Preserved behavior and ownership

- EditorDraft validation, stable reminder identity, enabled switch behavior, Every day behavior, and removal-history warning remain unchanged.
- Room remains the commit authority. The existing Idle, SavingRoom, RoomFailure, CompletingAlarms, and PostCommitFailure lifecycle remains intact; post-commit retry remains completion-only.
- No Room schema, ViewModel, navigation, MainActivity wiring, or shared theme token change was made. Medication Library, History, Settings, Alarm Readiness, and AlarmActivity were left untouched.

## Validation and visual evidence

Passed with Android Studio's bundled Java 17:

- `./gradlew testDebugUnitTest`.
- `./gradlew lintDebug`.
- `./gradlew assembleDebug`.
- `./gradlew assembleDebugAndroidTest`.
- `git diff --check`.

Focused connected validation on `emulator-5554` (`Medium_Phone(AVD) - 14`) passed:

- `MedicationScreensTest` — 31 tests, 0 failures.
- `MedicationEditorLifecycleTest` — 8 tests, 0 failures (run with the focused editor suite before the final UI-test correction; no lifecycle source or behavior changed afterward).

Representative truthful emulator captures are available in the ignored build reports directory:

- `app/build/reports/m36-editor-upper.png` — labeled form, multiline notes, enabled state, first reminder, and fixed Save.
- `app/build/reports/m36-editor-reminders.png` — two independent reminder groups, weekday chips, Add-time action, and fixed Save.
- `app/build/reports/m36-editor-ime.png` — focused notes field with the action region visibly allocated above the IME.
- The corrected `m36-editor-reminders.png` capture verifies the selected schedule controls use the same mint-teal family as Add-time, while Save remains the stronger teal action.

Known fidelity limitation: the supported domain has no medication appearance, form, dosage, start/end date, or recurrence-expansion data to render.
