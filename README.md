# Meds Reminder M2

Meds Reminder is an Android-first, local medication reminder. M2 supports multiple medications,
optional instructions, enable/disable, and one or more fixed local-time schedules per medication.
Each time selects its own weekdays; all seven selected days is the existing daily behavior. An
occurrence can be resolved as Taken, Snoozed for five minutes, or Skipped.

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
past medication alarms; it expires them and restores the next eligible future occurrences.

Weekday-only edits preserve a recently-due BASE occurrence while it remains inside delivery grace
and its concrete local weekday remains selected. They also preserve already-ringing occurrences and
pending SNOOZE occurrences. Each enabled schedule otherwise has one canonical future BASE; the
grace-valid due BASE may temporarily coexist with it.

## Persistence

Room schema version 2 adds `reminder_times.weekday_mask` as `INTEGER NOT NULL DEFAULT 127`.
The non-destructive v1-to-v2 migration maps every existing daily schedule to all seven weekdays
without changing medication IDs, reminder-time IDs, occurrence UUIDs, or occurrence history.

## Direct Boot limitation

Medication data is stored in credential-protected Room storage. M2 deliberately does not duplicate
medication names or schedules into device-protected storage. `LOCKED_BOOT_COMPLETED` therefore does
not access Room or restore medication alarms. `BOOT_COMPLETED` reconciles after the first unlock.

This remains a known reliability limitation relative to the M0 technical spike, not the
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
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

The instrumented Room test still needs an emulator or connected device:

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
.\gradlew.bat connectedDebugAndroidTest
```

Emulator validation covers CRUD, weekday edits, grace-valid delivery, edit/disable/delete
cancellation, reboot reconciliation, Snooze, queue advancement, and resource cleanup. The accepted
Samsung S23 alarm-presentation result does not need to be repeated for M2 because the alarm activity,
ringing service, notification/channel, full-screen session identity, foreground startup, and alarm
actions are unchanged.
