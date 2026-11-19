# SPDX-License-Identifier: MIT
#
# R8 keep rules for the release build of both channels.
#
# Most of what a minified build needs arrives with the artifacts, so it is not restated here:
#   * Android components: AGP derives keep rules from AndroidManifest.xml for MainActivity,
#     PlaybackService and UpdateInstallReceiver, which the framework instantiates by name.
#   * kotlinx.serialization: kotlinx-serialization-core 1.8.1 ships
#     META-INF/com.android.tools/r8/kotlinx-serialization-r8.pro, which keeps the @Serializable
#     classes and their Companion.serializer(). That method is the reflective lookup
#     PresetJson.decode/encode and Json.decodeFromString<Mix> perform.
#   * Compose 1.8.0 and Media3 1.6.1 ship their own proguard.txt (Compose keeps its throw helpers
#     and ViewLayerContainer.dispatchGetDisplayList, Media3 adds -dontwarn lines for Guava and the
#     checkerframework annotations), and this app reaches neither by reflection.
#
# Nothing here is a keep-all of the app or of a library.

# A stack trace pasted into a GitHub issue is this app's only diagnostic channel (there is no crash
# reporter), so the line number must survive obfuscation.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Names only, no members, over the updater package. This is not a reflection rule: tools/check_release.py
# proves on every build that the fdroid APK contains none of the updater's fourteen class descriptors
# and that the github APK contains them. Obfuscated names would make the negative half of that check
# find nothing and pass vacuously, which would quietly weaken the F-Droid "no network code" gate.
-keepnames class dev.nightfall.update.**

# The same rule for the same reason, over the hidden diagnostics package. These class descriptors
# are what tools/check_release.py searches the F-Droid APK for, and what it requires the GitHub APK to
# contain so that search cannot pass vacuously.
-keepnames class dev.nightfall.diagnostics.**
