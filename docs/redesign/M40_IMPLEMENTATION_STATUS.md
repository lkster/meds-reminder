# M40 implementation status

M40 adds a bounded, Activity-local History read-failure and retry presentation path. A first-read
failure keeps the History shell and disabled filters while offering inline Retry. A refresh failure
retains the last authoritative Room History value, including empty data, and adds a compact inline
retry notice. Room remains authoritative; no History write path, schema/DAO behavior, medication
loading behavior, or shared loading/retry infrastructure changed.

Changed production files are `MainActivity.kt` and `HistoryScreen.kt`. M40 also updates
`HistoryScreenTest.kt`, adds `MainActivityHistoryReadFailureTest.kt`, and adds the deterministic
`HistoryReadFailureRenderTest.kt` fixture, which writes the device-side
`m40-history-read-error.png`. Versioning is `0.40-m40`; the README records the milestone.

Codex local gates: `testDebugUnitTest`, `lintDebug`, and `assembleDebug` completed successfully.
`assembleDebugAndroidTest` initially found an unresolved test import; after that narrow source fix, it
completed successfully. Existing unrelated Compose test API deprecation warnings remain.

Connected/device validation is pending user execution. Visual fixture capture and visual acceptance
inspection are also pending user execution; no connected tests, emulator, ADB commands, or captures
were run by Codex.
