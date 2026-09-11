# Manager stability investigation (2026-09-11)

## Evidence

The original Downloads/Telegram Desktop/crash.log no longer exists at the supplied
path. Read-only device diagnostics were used instead; log buffers were not cleared.

`adb shell dumpsys activity exit-info moe.shizuku.privileged.api` recorded:

- 2026-09-11 17:21:32.685, PID 10543, reason 6 (ANR).
- Description: a foreground service of FOREGROUND_SERVICE_TYPE_SHORT_SERVICE did
  not stop within a timeout, naming `moe.shizuku.manager.adb.AdbPairingService`.
- Other recent records were package-update restarts, force-stops, and a system kill.
  These are not evidence of additional Java crashes. The crash log buffer did not
  contain a recent Shizuku manager Java exception.

The pairing helper runs inside the manager app, separately from the root/ADB Shizuku
server. An ANR in the manager does not itself imply that the server has exited.
This identifies one confirmed failure mode, not every possible intermittent exit.

## Fixes

- Delay composing the activation list until its initial service-status query finishes.
  Previously the stable first method key became the scroll anchor; inserting the stop
  row above that key after loading left the stop row above the viewport.
- Pairing helper implements both Android 14 and 15 timeout callbacks and explicitly
  stops foreground work and itself. An independent 150-second watchdog also covers
  older releases. No sticky intent replay after termination.
- Pairing work uses a service-owned coroutine, returns UI/notification results on the
  main dispatcher, closes the pairing client, and ignores results after destruction.
  Connect timeout is 10 seconds; TLS/network read timeout is 15 seconds.
- Discovery that completes registration after stop is explicitly unregistered.
- Repeated status/package loads cancel obsolete requests and serialize Binder work;
  obsolete results cannot overwrite current UI state.
- Cache parsed HTML and system-derived base palettes. Unrelated preference updates
  no longer recompute the theme or rewrite system-bar appearance on every composition.
- Pause the uptime ticker outside the home tab and while the activity is stopped.

The 150-second watchdog ends only the pairing helper. If pairing search times out,
reopen the pairing page to start a new search. It does not stop the Shizuku server.

## Validation

Regression tests cover delayed activation status, both timeout callbacks, the watchdog
on API 30/34, explicit stop and destruction cleanup, plus the existing Material tests.
All 21 cases and assembleDebug passed with JDK 21. The updated APK was installed
without clearing data. On OPPO PMA120 / Android 16, a search started around 17:44:27;
discovery stopped automatically at 17:46:57.522 and its foreground ServiceRecord
disappeared. Manager PID 28017 and Shizuku server PID 23522 remained alive and unchanged.
No claim is made that all device/vendor exits or all sources of jank have been eliminated.

Read-only graphics diagnostics before installation reported 460 missed-deadline frames
out of 3872 (11.88%) in the accumulated Debug-app session, with a 22 ms 90th percentile.
That session is not a controlled benchmark; it must not be used to claim a percentage
performance improvement over a different interaction sequence.

Android contract: [Service.onTimeout documentation](https://developer.android.com/reference/android/app/Service#onTimeout(int)).
