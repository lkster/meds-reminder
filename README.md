# Meds Reminder M4

Meds Reminder is an Android-first, local medication reminder. M3 supports multiple medications,
optional instructions, enable/disable, and one or more fixed local-time schedules per medication.
Each time selects its own weekdays; all seven selected days is the existing daily behavior. An
occurrence can be resolved as Taken, Snoozed, or Skipped.

M3 adds global alarm behavior preferences. The app can use the current system-default alarm tone or
an explicit alarm tone returned by Android's system ringtone picker, enable or disable vibration,
and use a 5, 10, 15, or 30 minute Snooze duration. Sound cannot be disabled. Preferences are stored
in credential-protected SharedPreferences and do not change the Room v2 schema.

## Factual medication history

M4 adds a newest-first history of retained, unambiguous factual outcomes: Taken, Skipped, and
No response after a presented alarm times out. It uses each occurrence's persisted scheduled instant
and the medication's current name. A qualifying Snooze occurrence is marked After snooze; M4 does
not reconstruct Snooze chains, original BASE times, or Snooze counts.

History is not a complete audit trail of scheduled doses, reminder journeys, or alarm attempts.
Snoozed and Expired occurrences are intentionally excluded. Existing disabling, schedule-edit, and
delete behavior can remove nonterminal or cascaded occurrences, leaving no visible history item.
Deleting a medication or reminder also deletes its associated history.

## Alarm behavior

Room is the authoritative logical state. Each medication, reminder time, and concrete BASE or
SNOOZE occurrence has its own stable identity. AlarmManager registrations are a rebuildable
projection of scheduled occurrences; receivers reject stale or unknown occurrence UUIDs.

`AlarmRingingService` remains the sole owner of continuous sound, vibration, the wake lock,
foreground-service state, the alarm notification, and cleanup. The high-importance notification
channel intentionally has no channel-owned sound or vibration. A single persisted ringing session
presents due occurrences sequentially, and its temporary ten-minute safety timeout resets for each
presented occurrence.

Sound and vibration are captured when each occurrence begins presentation. An ordinary refresh of
the same occurrence does not change its active output, while the next queued occurrence reads fresh
preferences after its actionable notification is updated. A recreated service may read current
preferences again because presentation settings are intentionally not persisted per occurrence.
An unavailable explicit ringtone falls back once to the current system-default alarm tone; failure
of both tones leaves the notification/full-screen presentation and any enabled vibration active.

Normal delivery accepts an occurrence up to the explicit two-minute `DELIVERY_GRACE_MILLIS` policy.
This protects an AlarmManager broadcast already in flight during routine reconciliation while still
rejecting materially late delivery. Reboot/package-update/exact-access recovery does not catch up
past medication alarms; it expires them and restores the next eligible future occurrences.

Weekday-only edits preserve a recently-due BASE occurrence while it remains inside delivery grace
and its concrete local weekday remains selected. They also preserve already-ringing occurrences and
pending SNOOZE occurrences. Each enabled schedule otherwise has one canonical future BASE; the
grace-valid due BASE may temporarily coexist with it.

## Persistence

Room remains at schema version 2, which adds `reminder_times.weekday_mask` as
`INTEGER NOT NULL DEFAULT 127`.
The non-destructive v1-to-v2 migration maps every existing daily schedule to all seven weekdays
without changing medication IDs, reminder-time IDs, occurrence UUIDs, or occurrence history.

## Direct Boot limitation

Medication data is stored in credential-protected Room storage. M3 deliberately does not duplicate
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

Targeted emulator validation should cover CRUD, weekday edits, preference persistence and fallback,
grace-valid delivery, edit/disable/delete cancellation, reboot reconciliation, configurable Snooze,
queue advancement, resource cleanup, and factual history rendering.

Deferred Samsung Galaxy S23 validation remains:

- custom ringtone playback;
- vibration OFF;
- FSI/lockscreen regression;
- selected ringtone persistence after reboot.
