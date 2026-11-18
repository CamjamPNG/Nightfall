# Privacy

Nightfall works offline. It does not collect or transmit analytics, advertising
identifiers, usage history, audio, recordings, location, contacts, or account
information. It has no account system, advertising SDK, purchases or microphone
permission. There are no tokens, no logins and no third-party services.

Nightfall ships in two builds, and they differ in exactly one respect.

**The GitHub build** can update itself from this repository's releases. That is
its only network access, and it is a download, never an upload:

- It reads one public file, `release-manifest.json`, from the latest Nightfall
  release on github.com — at most once every six hours while the app is open and
  the device is online, and whenever you press *Check for updates*. Like any
  download, GitHub sees the request's IP address and the app's user agent
  (`Nightfall-Updater`). Nightfall sends no identifier, account, device name,
  installed-app list, listening history or mix data with it.
- When you tap **Update**, it downloads the one APK for your device's
  architecture, checks its size and SHA-256 against the manifest, checks that it
  is signed by the same certificate as the installed app, and hands it to
  Android's installer. The downloaded file is deleted from the app's cache after
  a failed or refused attempt.
- It stores two facts in private preferences: when it last checked, and what the
  installer reported about the last attempt.

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
local presets and settings require none. If you never tap *Update* in the GitHub
build, no APK is ever downloaded.
