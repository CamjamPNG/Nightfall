# Privacy

Nightfall works offline. It does not collect or transmit analytics, advertising
identifiers, usage history, audio, recordings, location, contacts, or account
information. It has no account system, advertising SDK, purchases or microphone
permission. There are no tokens, no logins and no third-party services.

There is one deliberate exception, and it is off until you turn it on yourself, in
a surface the app does not advertise: a local diagnostics journal, described at the
end of this file. The F-Droid build does not contain it at all, and neither build
has any code that could upload it.

Nightfall ships in two builds. They are the same app except for two things, both
of which exist only in the GitHub build: the updater described here, and the
opt-in diagnostics journal described at the end. What follows about the updater is
the GitHub build's.

**The GitHub build** can update itself from this repository's releases. That is
its only network access, and it is a download, never an upload:

- It reads one public file, `release-manifest.json`, from the latest Nightfall
  release on github.com. On its own that happens at most once every six hours while
  the app is open and the device is online, counted from the last successful check,
  and never more than once every fifteen minutes; pressing *Check for updates* obeys
  that same fifteen-minute limit. Like any download, GitHub sees the request's IP
  address and the app's user agent (`Nightfall-Updater`). Nightfall sends no
  identifier, account, device name, installed-app list, listening history or mix data
  with it.
- **Update automatically** is on by default in this build, so a newer release is
  downloaded and installed without a tap. The install is refused while audio is
  playing, or while the app has no playback answer yet, because a replacement
  restarts the process and would silence a night; it happens when sound stops or on
  the next launch. With the switch off, nothing is downloaded until you press
  **Update**: that press fetches and verifies the release, and the app then offers
  **Install**, which is the tap that hands it to Android's installer.
- Either way it downloads the one APK for your device's architecture, checks its
  size and SHA-256 against the manifest, checks that it is signed by the same
  certificate as the installed app, and hands it to Android's installer. The
  downloaded file is deleted from the app's cache if the download or its verification
  fails, and kept when the install itself does not finish, so a retry does not fetch
  it again.
- It keeps nine things in one private preferences file, and nothing else: when it
  last attempted a check, when it last got a successful answer, whether the
  automatic switch is on, what the installer reported about the last attempt (read
  once and cleared), which release's update row you dismissed, the release notes the
  last manifest read carried, which version's notes you have already seen, the
  verified download waiting for a window (its version, version code and file name),
  and the version it last handed to Android, so the next launch can tell an update
  from a fresh install.

There is no background worker, alarm, notification channel or periodic wake-up:
an app that is not in the foreground does not check anything.

**The F-Droid build** contains no updater, no network code and no network
permission. It cannot open a socket. Settings states that updates arrive through
F-Droid or your app store.

Mixes, favorites, music configurations, and settings are saved in the app’s
private local DataStore. Android media services receive the current mix name,
playback state and timer text to show notification and lock-screen controls.
The app reads Android’s selected audio route so it can show an output label.
It does not scan for Bluetooth devices or record microphone audio. The installed
version, package name and signing certificate are read locally on the device to
compare against an update; none of it is transmitted.

JSON import/export uses Android’s system file picker. Exported files go where
you choose. A storage provider chosen in that picker may synchronize files
according to that provider’s own settings; Nightfall does not operate that
provider or require cloud storage.

There is no application-level encryption of exported JSON. Keep exports in a
location appropriate for your needs. Uninstalling the app removes its private
local data. The app disables Android backup; keep your own preset exports if
you want to preserve them across uninstall or signing-key changes.

Downloading an APK, obtaining source/dependencies, or reproducing audio assets
may require internet access. Installed playback, music generation, timers,
local presets and settings require none. In the GitHub build, turning
**Update automatically** off means no APK is downloaded unless you tap *Update*;
the F-Droid build has no updater and downloads nothing.

## The diagnostics journal (GitHub build only, off by default)

The GitHub build has a hidden surface: five taps on the version row at the bottom
of Settings, inside three seconds. It holds one switch, off until you turn it on,
and it names what is recorded before you decide. Once revealed, the section stays
in Settings until you hide it again; uninstalling the app removes it with
everything else.

The switch records the shape of things that break, on the device, and nothing
else:

- the app's version name and version code, and the Android API level;
- how long the app took to build its first screen, in buckets: under one second,
  one to two, two to five, five to ten, ten or more;
- one entry per finished playback session, with its length in buckets: under five
  minutes, five to thirty, thirty minutes to two hours, two to eight hours, eight
  hours or more;
- that playback failed, and when — never the failure's message, which can carry a
  file path;
- that the Opus decoder refused, and which step refused it: this device has no Opus
  decoder, a decoder refused the format, it refused to start, it refused the
  recording's identification header (it was then built without one), it started and
  produced no output in time, or it refused in a way that is none of those. Never the
  platform's own words, which can name a codec, a path and a device;
- every update check and how it ended: up to date, a newer release available,
  downloading, ready to install, installing, failed, or inside the fifteen-minute
  floor;
- each crash, ANR or other process exit Android recorded for this app while the switch
  was on, as a reason and a date. Android keeps those records from before you turned the
  switch on too; they are not imported, and neither is anything from a stretch when the
  switch was off.

It records no audio, no recordings, no mix or preset names or contents, no file
paths, no output-device name, no location, no contacts, no advertising id, no
account and no device serial. No free text is stored anywhere in it: every value
is one of the tokens listed above. Each entry holds the local date and never a
clock time, because when in the night somebody listens is theirs.

A playback session is measured from the moment collection was on: a night that started
before the switch did, or that spans a stretch with it off, is recorded only for the part
that was watched.

The journal is capped at 200 entries, 64 kilobytes and 30 days. Past any of those
the oldest entries are dropped first, so leaving the switch on for years cannot
accumulate more. Turning the switch off stops the recording; **Erase journal**
deletes the file, and the section says so when it has.

**Nothing is uploaded, by any path.** The app has no server, no endpoint and no
upload code. The report view shows the exact JSON held on the device, formatted to
be read, and the only way it leaves your phone is if you press **Share report**
and pick a destination in Android's own share sheet — the same deliberate action
as sharing any other file, and the receiving app's privacy terms are then its own.
