// SPDX-License-Identifier: MIT
package dev.nightfall.update

/** Builds release-manifest text in the exact shape `tools/package_release.py` publishes. */
object TestManifests {
    const val PIN = "0ef5904042e684e4158711036c04c6fb2527cb48c023d5173fd8ded18db032de"
    const val PACKAGE = "io.github.camjampng.nightfall"
    val SHAS =
        mapOf(
            "universal" to "e17b315213b61e8cf977569c8743450dcbb2f455ff1384462be3acb4b6d2e180",
            "arm64-v8a" to "8896aa7937a8b33609330fe162fafd808689ea855ab4b890d5e93cfa3fb1b45f",
            "armeabi-v7a" to "853e7476923d3d778d8cdaf524013affcdfabd6b2d69389a8ea728162299937f",
            "x86" to "25aa8c717871a461bf5bfb89998214572b38af1b2f1fa5b769ecab941364307a",
            "x86_64" to "7f6c92414abf7ab077b60012dc65e67a856ff827608ca2d73dea6ea9bf3f239f",
        )
    val SIZES =
        mapOf(
            "universal" to 91195916L,
            "arm64-v8a" to 91097092L,
            "armeabi-v7a" to 91097096L,
            "x86" to 91097080L,
            "x86_64" to 91097086L,
        )

    fun apk(
        abi: String,
        versionName: String = "1.0.2",
        file: String = "Nightfall-$versionName-$abi.apk",
        bytes: Long = SIZES[abi] ?: 1000L,
        sha256: String = SHAS[abi] ?: "a".repeat(64),
        url: String = "https://github.com/CamjamPNG/Nightfall/releases/download/v$versionName/$file",
    ): String =
        """    {"file": "$file", "abi": "$abi", "bytes": $bytes, "sha256": "$sha256", "url": "$url"}"""

    /** The release log two releases look like in the published manifest. */
    const val RELEASES =
        """    {"version": "1.0.2", "date": "2026-11-18", "notes": ["feat(update): install a release on its own", "fix: say which window an update waits for"]},
    {"version": "1.0.1", "date": "2026-11-12", "notes": ["hotfix(release): verify the signing identity"]}"""

    const val SCHEMA = 2

    fun manifest(
        versionName: String = "1.0.2",
        versionCode: Long = 5,
        applicationId: String = PACKAGE,
        minSdk: Long = 26,
        targetSdk: Long = 35,
        certificate: String = PIN,
        apks: List<String> = listOf("universal", "arm64-v8a", "armeabi-v7a", "x86", "x86_64").map { apk(it, versionName) },
        /** Raw `log` value, so a test can hand the parser a hostile one. Null omits the key. */
        log: String? = "[\n$RELEASES\n  ]",
        /** Null omits the key, which is what a pre-log manifest looks like. */
        schemaVersion: Long? = SCHEMA.toLong(),
    ): String =
        """
        {
          "versionName": "$versionName",
          "versionCode": $versionCode,
          "applicationId": "$applicationId",
          "minSdk": $minSdk,
          "targetSdk": $targetSdk,
          "certificateSha256": "$certificate",
          "apks": [
        ${apks.joinToString(",\n")}
          ]${if (schemaVersion != null) """,
          "schemaVersion": $schemaVersion""" else ""}${if (log != null) """,
          "log": $log""" else ""}
        }
        """
            .trimIndent()

    val installed =
        InstalledApp(
            packageName = PACKAGE,
            versionName = "1.0.1.2",
            versionCode = 4,
            certificateSha256 = PIN,
        )

    val phone = DeviceProfile(listOf("arm64-v8a", "armeabi-v7a", "armeabi"), 35)
}
