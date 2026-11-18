// SPDX-License-Identifier: MIT
package dev.nightfall.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A refused update. The message is shown to the user verbatim, so it states the reason. */
class UpdateProblem(message: String) : Exception(message)

/** The repository whose releases this channel trusts. */
const val RELEASE_REPOSITORY = "CamjamPNG/Nightfall"
const val RELEASE_PACKAGE = "io.github.camjampng.nightfall"

val RELEASED_ABIS = setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64", "universal")

data class ManifestApk(
    val file: String,
    val abi: String,
    val bytes: Long,
    val sha256: String,
    val url: String,
)

data class ReleaseManifest(
    val versionName: String,
    val versionCode: Int,
    val applicationId: String,
    val minSdk: Int,
    val targetSdk: Int,
    val certificateSha256: String,
    val apks: List<ManifestApk>,
    /**
     * What the document declares it carries. `1` is the release-only shape, `2` adds [log]. A value
     * this build does not know is not a refusal: every field that decides an install is still
     * checked exactly as before, and a newer schema can only add keys this parser ignores.
     */
    val schemaVersion: Int = 1,
    /** Recent releases and their notes, newest first. Informational only. */
    val log: List<UpdateLogEntry> = emptyList(),
)

data class InstalledApp(
    val packageName: String,
    val versionName: String,
    val versionCode: Int,
    val certificateSha256: String,
)

data class DeviceProfile(val supportedAbis: List<String>, val sdk: Int)

data class AvailableUpdate(val versionName: String, val versionCode: Int, val apk: ManifestApk)

private val VERSION_NAME = Regex("""\d+\.\d+(?:\.\d+){0,2}""")
private val DIGEST = Regex("""[0-9a-f]{64}""")
private const val MAX_MANIFEST_CHARS = 64 * 1024
private const val MAX_VERSION_CODE = 2_100_000_000L
private const val MAX_APK_BYTES = 1_073_741_824L

/**
 * Parses and validates the release manifest asset. Pure Kotlin with no Android types, so the whole
 * trust decision can be unit-tested against hostile input. Unknown keys are ignored; every value
 * that decides an install is checked.
 */
object ReleaseManifestParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): ReleaseManifest {
        if (text.length > MAX_MANIFEST_CHARS)
            throw UpdateProblem("The release manifest is larger than expected")
        val root =
            try {
                json.parseToJsonElement(text)
            } catch (e: Exception) {
                throw UpdateProblem("The release manifest is not valid JSON")
            }
        val obj = root as? JsonObject ?: throw UpdateProblem("The release manifest is not a JSON object")
        val versionName = string(obj, "versionName")
        if (!VERSION_NAME.matches(versionName))
            throw UpdateProblem("The release manifest has an unrecognised version name: $versionName")
        val versionCode = integer(obj, "versionCode")
        if (versionCode !in 1..MAX_VERSION_CODE)
            throw UpdateProblem("The release manifest has an out-of-range versionCode: $versionCode")
        val applicationId = string(obj, "applicationId")
        if (applicationId.isEmpty() || applicationId.length > 200)
            throw UpdateProblem("The release manifest has no usable applicationId")
        val minSdk = integer(obj, "minSdk")
        val targetSdk = integer(obj, "targetSdk")
        if (minSdk !in 1..100)
            throw UpdateProblem("The release manifest has an out-of-range minSdk: $minSdk")
        if (targetSdk < minSdk || targetSdk > 100)
            throw UpdateProblem("The release manifest has an unusable targetSdk: $targetSdk")
        val certificate = string(obj, "certificateSha256").lowercase()
        if (!DIGEST.matches(certificate))
            throw UpdateProblem("The release manifest has no usable signing certificate digest")
        val entries =
            (obj["apks"] as? JsonArray)?.takeIf { it.isNotEmpty() }
                ?: throw UpdateProblem("The release manifest lists no APKs")
        if (entries.size > 16) throw UpdateProblem("The release manifest lists too many APKs")
        val apks =
            entries.map { entry ->
                val apk = entry as? JsonObject ?: throw UpdateProblem("The release manifest has a malformed APK entry")
                apk(apk, versionName)
            }
        val duplicates = apks.groupBy { it.abi }.filterValues { it.size > 1 }.keys
        if (duplicates.isNotEmpty())
            throw UpdateProblem("The release manifest lists ${duplicates.first()} twice")
        return ReleaseManifest(
            versionName = versionName,
            versionCode = versionCode,
            applicationId = applicationId,
            minSdk = minSdk,
            targetSdk = targetSdk,
            certificateSha256 = certificate,
            apks = apks,
            schemaVersion = schemaVersion(obj),
            log = UpdateLogParser.parse(obj["log"]),
        )
    }

    /** A declared schema is informative: anything unreadable is reported as the original shape. */
    private fun schemaVersion(obj: JsonObject): Int =
        (obj["schemaVersion"] as? JsonPrimitive)?.takeUnless { it.isString }?.content?.toIntOrNull()?.coerceAtLeast(1)
            ?: 1

    private fun apk(obj: JsonObject, versionName: String): ManifestApk {
        val file = string(obj, "file")
        if (!file.endsWith(".apk") || file.length > 200 || file.any { it == '/' || it == '\\' })
            throw UpdateProblem("The release manifest has an unusable APK name: $file")
        val abi = string(obj, "abi")
        if (abi !in RELEASED_ABIS) throw UpdateProblem("The release manifest has an unknown architecture: $abi")
        val bytes =
            (obj["bytes"] as? JsonPrimitive)?.takeUnless { it.isString }?.content?.toLongOrNull()
                ?: throw UpdateProblem("The release manifest has no usable size for $file")
        if (bytes <= 0 || bytes > MAX_APK_BYTES)
            throw UpdateProblem("The release manifest has an unusable size for $file")
        val sha256 = string(obj, "sha256").lowercase()
        if (!DIGEST.matches(sha256)) throw UpdateProblem("The release manifest has an unusable checksum for $file")
        val url = string(obj, "url")
        val expected = "https://github.com/$RELEASE_REPOSITORY/releases/download/v$versionName/$file"
        if (url != expected)
            throw UpdateProblem("The release manifest points $file at an unexpected address: $url")
        return ManifestApk(file, abi, bytes, sha256, url)
    }

    private fun string(obj: JsonObject, key: String): String {
        val value = obj[key] as? JsonPrimitive ?: throw UpdateProblem("The release manifest has no $key")
        if (!value.isString) throw UpdateProblem("The release manifest's $key is not a string")
        return value.content
    }

    private fun integer(obj: JsonObject, key: String): Int {
        val value = obj[key] as? JsonPrimitive ?: throw UpdateProblem("The release manifest has no $key")
        if (value.isString) throw UpdateProblem("The release manifest's $key is not a number")
        return value.content.toIntOrNull() ?: throw UpdateProblem("The release manifest's $key is not a number")
    }
}

/** What the last download turned out to be once Android has read the file. */
data class DownloadedApk(
    val sizeBytes: Long,
    val sha256: String,
    val packageName: String?,
    val versionCode: Int?,
    val certificateSha256s: List<String>,
    val nativeAbis: Set<String>,
)

/** Compares Nightfall version names numerically: `1.0.1` < `1.0.1.1` < `1.0.2` < `1.1`. */
fun compareVersions(a: String, b: String): Int {
    val left = numbers(a)
    val right = numbers(b)
    for (index in 0 until maxOf(left.size, right.size)) {
        val difference = left.getOrElse(index) { 0L } - right.getOrElse(index) { 0L }
        if (difference != 0L) return if (difference > 0L) 1 else -1
    }
    return 0
}

private fun numbers(version: String): List<Long> {
    if (!VERSION_NAME.matches(version)) throw UpdateProblem("Unrecognised version name: $version")
    return version.split('.').map { it.toLong() }
}

/**
 * Every refusal the update channel makes, with the reason the user sees: downgrades, foreign
 * signing identities, wrong packages, bad checksums, foreign URLs and missing architectures.
 */
object UpdatePolicy {
    fun nextUpdate(
        manifest: ReleaseManifest,
        installed: InstalledApp,
        device: DeviceProfile,
    ): AvailableUpdate {
        if (manifest.applicationId != installed.packageName)
            throw UpdateProblem(
                "That update is for ${manifest.applicationId}, but the installed app is ${installed.packageName}"
            )
        if (manifest.versionCode <= installed.versionCode)
            throw UpdateProblem(
                "Version ${manifest.versionName} (${manifest.versionCode}) is not newer than the installed " +
                    "${installed.versionName} (${installed.versionCode}); downgrades are refused"
            )
        if (compareVersions(manifest.versionName, installed.versionName) <= 0)
            throw UpdateProblem(
                "The release names itself ${manifest.versionName}, which is not newer than the installed " +
                    "${installed.versionName}"
            )
        if (manifest.certificateSha256 != installed.certificateSha256)
            throw UpdateProblem(
                "That release is signed for a different channel, so Android would refuse to replace Nightfall"
            )
        if (device.sdk < manifest.minSdk)
            throw UpdateProblem(
                "That release needs Android API ${manifest.minSdk}; this device runs API ${device.sdk}"
            )
        return AvailableUpdate(manifest.versionName, manifest.versionCode, apkFor(manifest, device))
    }

    private fun apkFor(manifest: ReleaseManifest, device: DeviceProfile): ManifestApk {
        val exact = device.supportedAbis.firstNotNullOfOrNull { abi -> manifest.apks.firstOrNull { it.abi == abi } }
        if (exact != null) return exact
        val universal = manifest.apks.firstOrNull { it.abi == "universal" }
        if (universal != null) return universal
        throw UpdateProblem(
            "That release has no APK for this device's architecture (${device.supportedAbis.joinToString(", ")})"
        )
    }

    /** Verifies what actually arrived on disk before anything is handed to the installer. */
    fun verifyDownloaded(update: AvailableUpdate, downloaded: DownloadedApk, installed: InstalledApp) {
        if (downloaded.sizeBytes != update.apk.bytes)
            throw UpdateProblem(
                "The download is ${downloaded.sizeBytes} bytes but the release lists ${update.apk.bytes}; " +
                    "it was discarded"
            )
        if (downloaded.sha256.lowercase() != update.apk.sha256)
            throw UpdateProblem("The downloaded APK's checksum does not match the release manifest; it was discarded")
        if (downloaded.packageName != installed.packageName)
            throw UpdateProblem(
                "The downloaded APK installs ${downloaded.packageName ?: "an unknown package"}, not " +
                    installed.packageName
            )
        if (downloaded.versionCode != update.versionCode)
            throw UpdateProblem(
                "The downloaded APK is version code ${downloaded.versionCode ?: "unknown"}, not the released " +
                    update.versionCode
            )
        if (downloaded.certificateSha256s.size != 1)
            throw UpdateProblem(
                "The downloaded APK carries ${downloaded.certificateSha256s.size} signing certificates; " +
                    "this channel is signed by exactly one"
            )
        if (downloaded.certificateSha256s.single().lowercase() != installed.certificateSha256)
            throw UpdateProblem("The downloaded APK is signed by a different certificate than the installed app")
        if (update.apk.abi == "universal") {
            if (downloaded.nativeAbis.isEmpty())
                throw UpdateProblem("The downloaded APK carries no native libraries")
            if (!RELEASED_ABIS.containsAll(downloaded.nativeAbis))
                throw UpdateProblem(
                    "The downloaded APK carries unknown native libraries " +
                        downloaded.nativeAbis.joinToString(", ")
                )
        } else if (downloaded.nativeAbis != setOf(update.apk.abi)) {
            val found = downloaded.nativeAbis.joinToString(", ").ifEmpty { "no" }
            throw UpdateProblem(
                "The downloaded APK contains $found native libraries, not only ${update.apk.abi}"
            )
        }
    }
}
