# Meds Reminder M1

Meds Reminder is an Android-first, local medication reminder. M1 supports multiple medications,
optional instructions, enable/disable, and one or more fixed local times per medication. Each time
recurs daily and can be resolved as Taken, Snoozed for five minutes, or Skipped.

## Alarm behavior

Room is the authoritative logical state. Each medication, reminder time, and concrete BASE or
SNOOZE occurrence has its own stable identity. AlarmManager registrations are a rebuildable
projection of scheduled occurrences; receivers reject stale or unknown occurrence UUIDs.

`AlarmRingingService` remains the sole owner of continuous sound, vibration, the wake lock,
foreground-service state, the alarm notification, and cleanup. The high-importance notification
channel intentionally has no channel-owned sound or vibration. A single persisted ringing session
presents due occurrences sequentially, and its temporary ten-minute safety timeout resets for each
presented occurrence.

Normal delivery accepts an occurrence up to the explicit two-minute `DELIVERY_GRACE_MILLIS` policy.
This protects an AlarmManager broadcast already in flight during routine reconciliation while still
rejecting materially late delivery. Reboot/package-update/exact-access recovery does not catch up
past medication alarms; it expires them and restores the next future daily occurrences.

## Direct Boot limitation

Medication data is stored in credential-protected Room storage. M1 deliberately does not duplicate
medication names or schedules into device-protected storage. `LOCKED_BOOT_COMPLETED` therefore does
not access Room or restore medication alarms. `BOOT_COMPLETED` reconciles after the first unlock.

This is a known temporary M1 reliability regression relative to the M0 technical spike, not the
intended final production behavior: a medication alarm due after reboot but before first unlock is
not delivered. A later reliability milestone must address this explicitly.

## Samsung One UI finding

- Samsung **Brief** pop-up mode may hide the unlocked alarm actions until the notification shade is
  expanded.
- The per-app **Detailed** override immediately exposes Taken, Snooze, and Skip: **Apps -> Meds
  Reminder -> Notifications -> Pop-up notification style -> Detailed**.
- The app opens only public Android notification settings and does not try to force this OEM option.

## Build and validation

Run the automated checks from the repository root:

```powershell
.\.tools\gradle-9.1.0\bin\gradle.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

The instrumented Room test still needs an emulator or connected device:

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
.\.tools\gradle-9.1.0\bin\gradle.bat connectedDebugAndroidTest
```

Emulator validation covers CRUD, edit/disable/delete cancellation, reboot reconciliation, snooze,
queue advancement, and resource cleanup. Samsung S23 validation remains required for lock-screen
full-screen presentation, unlocked heads-up behavior, task restoration, and OEM notification UI.
