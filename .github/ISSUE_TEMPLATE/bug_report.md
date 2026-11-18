---
name: Bug report
about: Something Nightfall does wrong — a crash, a gap in the audio, a control that ignores you
title: ''
labels: bug
assignees: ''
---

**What happened**
A plain description of the wrong behavior, and what you expected instead.

**Steps to reproduce**
1.
2.
3.

**Device and build**

| | |
| --- | --- |
| Device model | e.g. Pixel 7, Galaxy A54 |
| Android version | e.g. Android 15, API 35 |
| Nightfall version | from the release page or the APK file name |
| Installed from | GitHub release APK / F-Droid / a debug build you compiled |
| APK variant | arm64-v8a / universal / armeabi-v7a / x86_64 / x86 |

**The mix**
How many layers were playing, and which ones if you can name them. Any solo,
mute, per-layer filter or fade settings that were active. A timer, a layer stop
schedule, or a weather-to-rain transition running?

**Output route**
Speaker, wired, or Bluetooth — and the device name if Bluetooth. Did anything
change about the route while it was playing?

**Playback context**
Screen locked? Another app playing audio or taking a phone call? Battery saver
or a manufacturer "app killer" active? Did the app come back from the background,
or from being swiped away?

**Logcat excerpt**
If you can, reproduce the problem and capture the log. The interesting lines are
Nightfall's own and the audio subsystem's; you do not need the whole log.

```sh
adb logcat -d -v time > nightfall-logcat.txt
```

Paste the lines around the failure, or attach the trimmed file. Check it for
anything private before posting — logcat can contain other apps' output.

**Does it happen with the default mix?**
Launching and pressing Play on **Nine-Layer Dream** without changing anything is a
useful control: it separates "this mix is too heavy for my phone" from "this
build is broken".

---

Please do not post credentials, signing material, private recordings or unrelated
personal information. If this is a security problem, do not file it here — see
[SECURITY.md](../blob/main/SECURITY.md).
