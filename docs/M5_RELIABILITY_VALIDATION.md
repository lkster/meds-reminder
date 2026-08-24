# M5 reliability validation

Status: **M5 is technically complete.** JVM/Robolectric and connected Android tests, lint/build
gates, and `git diff --check` passed; implementation/diff review was accepted. The external
non-force-stop emulator process-death check also passed on 2026-08-24.

Samsung Galaxy S23 physical validation has **NOT YET PHYSICALLY EXECUTED**. It is explicitly
deferred to final physical-device / user-acceptance validation and is not an M5 blocker.

## Recovery contract

- Room is authoritative logical state. AlarmManager contains only a rebuildable projection.
- A fresh or null sticky service start validates and selects the current queue from Room. Start
  Intent extras are notification hints, not recovery truth.
- An already presented occurrence retains its persisted presentation timestamp and original
  ten-minute deadline across service recreation.
- An overdue presented owner becomes `TIMED_OUT` immediately on recovery. Queued, unpresented
  followers retain their occurrence UUIDs and ordering; each gets its first presentation timestamp
  and ten-minute window only when it becomes current.
- Exact-alarm access controls future projection and new delivery eligibility. Its absence does not
  expire an existing `RINGING` queue. If a `systemExempted` service cannot be recovered, the current
  actionable notification fallback is posted and service-owned resources are cleaned.
- If actionable notification presentation is unavailable, recovery fails closed by expiring the
  ringing queue and cleaning the notification/resources. FSI denial alone is not this condition.
- Reboot after unlock expires all pre-reboot ringing. Package replacement and exact-access
  restoration preserve valid presented ringing and queued followers, apply routine grace to
  unpresented orphan claims, rebuild future projection, and synchronize the preserved queue.

The fallback notification is actionable but cannot reproduce continuous service-owned sound,
vibration, wake lock, or timeout while the service cannot be validly promoted. This limitation is
accepted for M5; no retry supervisor or second scheduling truth is introduced.

## External emulator process-death check

Status: **PASS** — 2026-08-24, headless `sdk_gphone64_x86_64` Android 14 / API 34 `user` AVD.

The installed debug APK allowed `run-as`; no root-capable image was needed. Exact command/output:

```text
adb shell run-as com.example.medsreminder id
uid=10193(u0_a193) gid=10193(u0_a193) groups=10193(u0_a193),1004(input),1007(log),1011(adb),1015(sdcard_rw),1028(sdcard_r),1078(ext_data_rw),1079(ext_obb_rw),3001(net_bt_admin),3002(net_bt),3003(inet),3006(net_bw_stats),3009(readproc),3011(uhid),3012(readtracefs),50193(all_a193) context=u:r:runas_app:s0:c193,c256,c512,c768
```

With occurrence `62252dbc-bd58-40f7-ae5f-33ed99326522` authoritatively `RINGING`, its persisted
`presented_at_epoch_millis` was `1787602740032`; its original deadline was therefore
`1787603340032`. The process was killed without stopping the package:

```powershell
$alarmProcessId = (adb shell pidof -s com.example.medsreminder).Trim() # 4950
adb shell run-as com.example.medsreminder kill -9 $alarmProcessId
```

Android automatically recreated the process as PID `8003` (Activity Manager recorded
`restartCount=1`, `startCommandResult=1`, and foreground service id `7001`). The replacement
notification was observed within about six seconds of the pre-kill capture. MainActivity was not
manually launched; the launcher was resumed after recovery and no Meds activity task or duplicate
AlarmActivity task was observed.

Room before and after restart contained the same `RINGING` UUID and timestamp, with no replacement
occurrence; the future BASE UUID was also unchanged. One high-importance alarm notification (id
`7001`) with Taken/Snooze/Skip actions was present after recovery. The headless AVD cannot provide
physical audibility or haptic evidence, but the restarted foreground service reacquired the app
wake lock, vibrator and AudioMix; after using its visible Taken action, Room changed that same UUID
to `TAKEN`, Activity Manager reported no service, no app notification remained, and active wake
locks were zero. The original timestamp was retained, so restart did not create a new timeout
window. The overdue branch remains covered by the focused Robolectric lifecycle test.

If a future debug image does not permit `run-as`, use a root-capable AVD and kill only the Linux app
process (`adb shell kill -9 <PID>`). `am force-stop` is explicitly not valid: it puts the package
into a stopped state and suppresses the restart semantics under test.

An explicit user/system force-stop is a separate behavior: no sticky restart is expected while the
package remains stopped. Record it separately and do not report it as a sticky-recovery failure.

## Deferred final physical-device / user-acceptance backlog: Samsung Galaxy S23

Run on the physical S23 with the app's notification permission granted, the medication alarm
channel at high importance, exact-alarm access granted, and Samsung pop-up style set to Detailed
unless the step intentionally varies a capability. Emulator evidence does not replace these checks,
but they are not required for individual milestones during active incremental development.

Prefer automated validation during active incremental development. Concentrate physical-device
regression testing near final application acceptance; perform an isolated manual check earlier only
when it is necessary to resolve a concrete implementation decision.

### 1. Custom ringtone real playback

Status/result: **DEFERRED — final physical-device / user-acceptance validation; NOT YET PHYSICALLY EXECUTED**.

1. In the app, choose a non-default system alarm sound with a clearly distinguishable tone.
2. Create a near-future medication alarm and leave the app.
3. When it rings, verify the selected tone—not the current default—is audibly played.
4. Resolve it and verify sound stops immediately.

App bug: the selected provider URI remains readable but the app plays a different/default tone,
stays silent, or fails to stop it. Samsung/Android/provider condition: the selected URI is no longer
readable or the provider removed/changed the sound; the documented one-time default fallback is
then expected.

### 2. Vibration OFF

Status/result: **DEFERRED — final physical-device / user-acceptance validation; NOT YET PHYSICALLY EXECUTED**.

1. Turn vibration OFF in the app and leave sound enabled with an audible alarm tone.
2. Create a near-future alarm, place the phone on a surface where vibration is observable, and let
   it ring.
3. Verify sound plays and there is no repeating vibration.
4. Resolve it and verify sound/resources stop.

App bug: the app-owned vibrator still runs while its persisted preference is OFF. Samsung/platform
condition: a separately configured system accessibility or notification effect produces haptics;
confirm its source before assigning the failure to the app. The app's alarm channel itself has
sound and vibration disabled.

### 3. FSI, secure lock screen, and return task

Status/result: **DEFERRED — final physical-device / user-acceptance validation; NOT YET PHYSICALLY EXECUTED**.

1. Allow the app's full-screen alarm access where the installed Android/One UI version exposes it.
2. Open another foreground app, lock the device securely, and turn the screen off.
3. Let the medication alarm fire. Verify it wakes/presents, and AlarmActivity appears over the
   secure lock screen where platform policy permits.
4. Tap Taken. Verify sound, vibration, wake lock, notification, service, and AlarmActivity resolve.
5. Verify the device returns naturally to the secure lock screen; after unlocking, the prior app is
   retained. MainActivity must not be launched by the resolution.

App bug: granted capabilities still lead to wrong PendingIntent/task routing, duplicate
AlarmActivity tasks, persistent resources after Taken, or an explicit MainActivity launch.
Samsung/SystemUI condition: presentation is suppressed because FSI, notification, channel,
lock-screen notification, battery, or Brief/Detailed policy is not actually enabled. Record those
settings with the result. Repeating with FSI denied is useful optional confirmation of actionable
notification degradation, not a fifth mandatory gate.

### 4. Selected ringtone persistence after reboot/unlock

Status/result: **DEFERRED — final physical-device / user-acceptance validation; NOT YET PHYSICALLY EXECUTED**.

1. Select a non-default system alarm sound and confirm its label remains selected.
2. Ensure a medication has a future alarm after the planned reboot/unlock time.
3. Reboot the S23, unlock it, and allow normal `BOOT_COMPLETED` reconciliation.
4. Reopen the app and verify the selected sound remains shown.
5. Let the future alarm ring and verify that sound is audibly played while its provider URI remains
   valid.

App bug: the readable selected URI is lost or ignored after post-unlock recovery. Samsung/provider
condition: the ringtone provider changes or revokes the URI across reboot; the documented default
fallback may then be correct.

## Direct Boot decision

M5 deliberately defers Direct Boot. Medication Room data and alarm preferences remain
credential-protected, and `LOCKED_BOOT_COMPLETED` performs no medication recovery. An alarm due
after reboot but before first unlock can therefore be missed. M5 does not mirror medication names,
schedules, Snooze/action state, or preferences into device-protected storage and does not introduce
a second source of truth.
