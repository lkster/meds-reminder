# M29 implementation status

## Implemented in M29

- Medication-library browse-surface redesign with the `Medications` heading and pane semantics.
- Presentation-only, saveable medication-name search over the existing Room emission.
- Truthful reminder time and weekday-mask card presentation; persisted instructions are not shown on cards.
- Add FAB for loading, populated, and search-empty states; a dedicated first-run CTA for an authoritative empty library.
- Distinct loading, global-empty, and search-empty presentation, plus responsive card/header layouts, accessibility labels, and safe-drawing handling.

## Partial / transitional

- History and Settings remain reachable from the Medication library.
- Final `Dzisiaj | Leki | Historia` IA is incomplete.
- Medication-specific icon/color identity is omitted because it is not persisted.
- Explicit Edit/Delete actions remain on cards because Medication Details is deferred.
- The existing delete dialog remains transitional.

## Deferred Category A/B

- Medication editor and Medication Details redesign.
- Delete-dialog committed-operation and unsaved-changes dialog redesign.
- History details/read-error presentation, AlarmActivity fixed-dark redesign, split Snooze presentation, remaining shared visual convergence, and broader localization/copy.

## Deferred Category C

- Today/day plan/calendar, Today Taken/Skip, one-off rescheduling, medication appearance/form persistence, richer schedules and schedule-specific dose, editable History, and grouped same-time alarms.

## Validation

- `./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` — PASS (using Android Studio's bundled JDK because the inherited JDK 8 cannot run Gradle 9.5).
- `git diff --check` — PASS.
- Focused `MedicationScreensTest` connected run — PASS: 28 tests.
- Focused `SettingsScreensTest` connected run — PASS: 15 tests, including the configured Vibration Switch state and callback regression.
- Focused real-Activity connected run for list loading, pane semantics, toggle lifecycle, and delete lifecycle — PASS: 10 tests.
