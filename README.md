# Meds Reminder M0

This repository is an Android alarm-behavior spike. It intentionally supports one test alarm and
does not contain medication-management features.

## Alarm notification architecture

`AlarmRingingService` is the only owner of alarm sound and vibration. The notification channel is
high importance and the notification uses the alarm category, but the channel does not independently
play sound or vibrate.

Android notification channel behavior is immutable after a channel is created. Final M0 validation
uses the fresh channel ID `medication_alarm_m0_2` so devices do not retain settings from earlier
experiments.

When comparing channel behavior, uninstall/reinstall the app or clear its notification settings if
the device retains old test channels. Samsung S23 remains the source of truth for heads-up and
lockscreen behavior.

## Samsung One UI finding

- Samsung **Brief** pop-up mode shows the unlocked alarm as a compact, temporary heads-up; actions
  remain available after manually expanding the notification shade.
- The app-level **Detailed** override immediately shows the full heads-up with Taken, Snooze, and
  Skip: **Apps → Meds Reminder → Notifications → Pop-up notification style → Detailed**.
- This behavior appears to be controlled by One UI. The app provides a standard Android notification
  settings shortcut, but does not assume the Detailed override can be enforced through public APIs.

## Emulator regression check

Build and install the debug APK, then grant the special alarm access in the app's capability panel:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.example.medsreminder/.MainActivity
```

Use **Schedule in 10 seconds** to verify notification delivery, action handling, full-screen behavior,
and cleanup. Emulator results are useful for regression testing but do not replace Samsung testing.

## Future work after M0

- Custom alarm sound.
- Vibration enable/disable and patterns.
- Configurable snooze duration.
- Volume-button snooze.
- Investigation of power/lock-button snooze behavior.
