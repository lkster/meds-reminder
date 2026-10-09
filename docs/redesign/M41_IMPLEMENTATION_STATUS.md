# M41 implementation status

M41 adds bounded medication read-failure and retry presentation owned by `MainActivity`. The
Activity-local medication collector now tracks failure separately from the nullable first Room
value: a first failure preserves `null` and exposes a local retry state, while a later failure keeps
the last authoritative medication list (including a confirmed empty list) and adds an inline retry
notice. Retry replaces only an inactive collector; cancellation is rethrown.

The Medication Library keeps its shell, disabled search, and Add availability for an initial failure.
Medication Details keeps its Back shell and hides all medication facts and actions until authority
returns. Refresh failures keep existing Library/Details facts and stable-ID actions visible. M11
loading-versus-empty semantics, M38 Details stable-ID selection, M40 History behavior, Room schema
2, and medication/alarm domain behavior remain unchanged.

Changed production files are `MainActivity.kt` and `MedicationScreens.kt`. M41 updates
`MedicationScreensTest.kt`, adds `MainActivityMedicationReadFailureTest.kt`, and adds
`MedicationReadFailureRenderTest.kt`, which writes the three prescribed M41 PNG fixtures.
Versioning is `0.41-m41` / code `2`; the README records the milestone.

Codex local validation: `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and
`assembleDebugAndroidTest` completed successfully. The first unit-test attempt encountered the
known Robolectric/SDK-36 JVM issue; rerunning it with Android Studio JBR completed successfully.
The Android-test assembly initially found one unresolved test import; after that narrow correction,
it completed successfully. Existing Compose test API deprecation warnings remain.

Connected/device validation and render capture remain pending user execution. No connected tests,
emulator/device commands, ADB commands, or captures were run by Codex.
