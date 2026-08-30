# Meds Reminder M8

Meds Reminder is an Android-first, local medication reminder. M3 supports multiple medications,
optional instructions, enable/disable, and one or more fixed local-time schedules per medication.
Each time selects its own weekdays; all seven selected days is the existing daily behavior. An
occurrence can be resolved as Taken, Snoozed, or Skipped.

M3 adds global alarm behavior preferences. The app can use the current system-default alarm tone or
an explicit alarm tone returned by Android's system ringtone picker, enable or disable vibration,
and use a 5, 10, 15, or 30 minute Snooze duration. Sound cannot be disabled. Preferences are stored
in credential-protected SharedPreferences and do not change the Room v2 schema.

## M8 list-toggle commit boundary, M7 readiness, and M6 editor lifecycle

Medication-list enable/disable is an enabled-only Room transaction. It reads the medication and
current reminder rows inside Room, so an accepted list snapshot can never overwrite a newer name,
instructions, reminder identity, reminder time, or weekday mask saved elsewhere. Disabling removes
the medication's nonterminal occurrences and returns their UUIDs for AlarmManager cancellation;
enabling maintains one canonical future BASE for every current Room reminder.

The Enabled-switch callback is the list-toggle acceptance boundary. Its finite Room-to-alarm
completion operation is structurally owned by MainActivity's lifecycle coroutine, enters
undispatched, and uses `withContext(NonCancellable)` only for that protected region. The real Room
and AlarmManager work switches to IO inside that region; `NonCancellable` is not combined with a
dispatcher. Ordinary Activity recreation or finish therefore cannot abandon an accepted operation
between a committed Room update and alarm completion. Room remains authoritative after commit; an
alarm-completion failure never repeats the Room mutation, and an old or destroyed Activity never
delivers late Toast/UI feedback. Durable continuation through process death remains deliberately
absent: the authoritative Room state is recovered by the established reconciliation paths.

List toggles remain MainActivity-owned and intentionally introduce no list ViewModel, global lock,
application coroutine scope, WorkManager flow, or generic mutation framework. The M6 editor owner
and its separate two-phase retry behavior remain unchanged.

The medication list now shows alarm readiness immediately below the header, before Add medication,
alarm behavior, or medication cards. Notifications, the Medication alarms channel, exact-alarm
access, and full-screen alarm access remain distinct. Android 13+ notification permission and
app-level notification enablement are separately checked behind the single Notifications item.

Notifications, high channel importance, and exact-alarm access are required for the application's
reliable-delivery path. Missing full-screen access only limits lock-screen presentation: actionable
alarm notifications remain available. Medication CRUD and History remain usable while setup is
incomplete; there is no mandatory onboarding or setup wizard. Readiness refreshes when the Activity
resumes after Android settings.

The medication editor draft and its active Save are retained across normal, same-process Activity
configuration recreation. This includes reminder identities, times, weekday selections and the
active save phase. Unsaved drafts and in-memory editor Saves are deliberately not restored after
process death; Room remains authoritative, so a transaction that committed before process death is
visible from Room and an uncommitted edit is discarded.

Editor Save explicitly separates the Room transaction from alarm completion. Once Room returns a
`MedicationScheduleEditResult`, the medication is saved even if cancellation/reconciliation/ringing
synchronization still needs retrying. That retry uses the retained result and never repeats a new
medication insert. Room remains schema version 2. The existing Samsung physical-validation backlog
remains deferred.

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
past `SCHEDULED` medication alarms; it expires them and restores the next eligible future
occurrences. A successfully claimed or expired BASE ensures its next future BASE in the same
delivery transaction; Taken, Skip, Snooze, and timeout do not advance recurrence.

Weekday-only edits preserve a recently-due BASE occurrence while it remains inside delivery grace
and its concrete local weekday remains selected. They also preserve already-ringing occurrences and
pending SNOOZE occurrences. Each enabled schedule otherwise has one canonical future BASE; the
grace-valid due BASE may temporarily coexist with it.

## M5 ringing recovery

The ringing service uses Room-authoritative `START_STICKY` recovery. A null restart Intent reloads
the persisted `RINGING` queue; Intent extras never replace Room identity or ordering. The original
`presented_at_epoch_millis` remains the timeout anchor, so process or service recreation cannot add
a fresh ten-minute window.

Exact-alarm access controls projection of future `SCHEDULED` occurrences. It is not required for an
occurrence already authoritatively `RINGING`. If exact access is unavailable during service
recovery, the service preserves Room state and posts the existing actionable notification fallback
when notification presentation is available. It cleans partial service resources and stops without
requesting another sticky restart. If actionable notification presentation itself is unavailable,
the current ringing queue is expired and cleaned up. Full-screen-intent access is separate from both
decisions; without FSI, the actionable notification remains the supported presentation fallback.

Reboot, package replacement, and exact-access restoration intentionally differ. Post-unlock reboot
reconciliation expires all pre-reboot `RINGING` rows. Package replacement and exact-access
restoration preserve a valid presented owner and its queued followers, apply the normal two-minute
grace only to unpresented orphan claims, rebuild future AlarmManager projection independently, and
synchronize a surviving ringing queue.

After a long interruption, an overdue presented owner immediately becomes `TIMED_OUT` against its
original deadline. An unpresented queued follower is not treated as stale: it keeps the same UUID,
becomes current in deterministic order, and receives its first presentation timestamp and normal
window only then. This deliberately favors the existing sequential-session contract; M5 adds no
timestamp heuristic or stale-queue policy for followers.

## Persistence

Room remains at schema version 2, which adds `reminder_times.weekday_mask` as
`INTEGER NOT NULL DEFAULT 127`.
The non-destructive v1-to-v2 migration maps every existing daily schedule to all seven weekdays
without changing medication IDs, reminder-time IDs, occurrence UUIDs, or occurrence history.

## Direct Boot limitation

Medication data is stored in credential-protected Room storage. M5 consciously continues not to duplicate
medication names or schedules into device-protected storage. `LOCKED_BOOT_COMPLETED` therefore does
not access Room or restore medication alarms. `BOOT_COMPLETED` reconciles after the first unlock.

This is an explicitly accepted M5 limitation: a medication alarm due after reboot but before first
unlock is not delivered. Direct Boot is deferred beyond M5 because mirroring medication, preference,
Snooze, and action state would introduce a second consistency domain beside authoritative Room.

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

The following Samsung Galaxy S23 checks are deferred to final physical-device / user-acceptance
validation and have not yet been physically executed; they are not individual M5 completion gates:

- custom ringtone playback;
- vibration OFF;
- FSI/lockscreen regression;
- selected ringtone persistence after reboot.

See [`docs/M5_RELIABILITY_VALIDATION.md`](docs/M5_RELIABILITY_VALIDATION.md) for the exact Samsung
procedures, deferred-result placeholders, and the passing non-force-stop emulator process-death
validation. During active incremental development, automated validation is preferred; perform an
isolated physical-device check earlier only when required to resolve a concrete implementation
decision.
