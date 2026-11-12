# Privacy

Nightfall works offline. It does not collect or transmit analytics, advertising
identifiers, usage history, audio, recordings, location, contacts, or account
information. It has no account system, advertising SDK, purchases, microphone
permission, or Android INTERNET permission.

Mixes, favorites, music configurations, and settings are saved in the app’s
private local DataStore. Android media services receive the current mix name,
playback state and timer text to show notification and lock-screen controls.
The app reads Android’s selected audio route so it can show an output label.
It does not scan for Bluetooth devices or record microphone audio.

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
local presets and settings require none.
