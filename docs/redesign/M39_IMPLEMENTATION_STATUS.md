# M39 implementation status

## Delivered

- History events are discoverably actionable and open a read-only normal-app details sheet.
- Sheet selection is owned by occurrence UUID and re-resolved from the current History Room Flow;
  Medication Details navigation passes the persisted medication ID and preserves existing M38
  resolution ownership.
- The sheet shows only supported persisted facts: medication name, scheduled date/time, terminal
  outcome, result time, and applicable After snooze context.
- History editing, status correction, dose, form, medication artwork, schedule details, and `Edit
  entry` are intentionally absent. There are no Room schema, DAO mutation, or alarm changes.
- `versionName` is `0.39-m39`; `versionCode` and Room schema remain 2.

## Validation

Codex ran the following local Gradle gates from the repository root:

- `rtk gradlew testDebugUnitTest --console=plain --no-daemon` — passed after rerunning with
  Android Studio's bundled JBR. The first JVM run encountered the known Robolectric SDK-36
  `DefaultSdkProvider.java:170` `UnsupportedOperationException`.
- `rtk gradlew lintDebug` — passed.
- `rtk gradlew assembleDebug` — passed.
- `rtk gradlew assembleDebugAndroidTest` — passed.

Connected/device and render validation remain pending user execution; Codex did not run them.
