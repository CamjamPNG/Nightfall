// SPDX-License-Identifier: MIT
package dev.nightfall.update

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Where the app looks for the current release. GitHub redirects this to the latest published release. */
const val MANIFEST_URL = "https://github.com/$RELEASE_REPOSITORY/releases/latest/download/release-manifest.json"

const val USER_AGENT = "Nightfall-Updater"

/**
 * The update channel's only network access: one manifest read and, when the user installs, one APK
 * download. Plain JDK classes, no Android types, so it can be exercised against a local server.
 */
object ReleaseFetch {
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val CHUNK = 64 * 1024

    fun manifestText(url: String = MANIFEST_URL): String {
        val connection = open(url)
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK)
                throw IOException("the manifest answered HTTP ${connection.responseCode}")
            val text =
                (connection.inputStream.use { it.readLimited(64 * 1024 + 1) })
                    ?: throw IOException("the manifest was empty")
            return text
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Streams [url] to [target], reporting 0..1 progress. Refuses to write more than [expectedBytes],
     * so a hostile or broken response cannot fill the device's storage.
     */
    fun download(url: String, target: File, expectedBytes: Long, onProgress: (Float) -> Unit) {
        val connection = open(url)
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK)
                throw IOException("the download answered HTTP ${connection.responseCode}")
            val announced = connection.contentLengthLong
            val total = if (announced > 0) announced else expectedBytes
            if (total > expectedBytes)
                throw UpdateProblem("The release served ${total} bytes but lists $expectedBytes; it was discarded")
            target.parentFile?.mkdirs()
            var written = 0L
            connection.inputStream.use { input ->
                target.outputStream().buffered(CHUNK).use { output ->
                    val buffer = ByteArray(CHUNK)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        written += read
                        if (written > expectedBytes)
                            throw UpdateProblem("The download is larger than the release lists; it was discarded")
                        output.write(buffer, 0, read)
                        if (total > 0) onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            if (written != expectedBytes)
                throw UpdateProblem(
                    "The download stopped after $written of $expectedBytes bytes; it was discarded"
                )
            onProgress(1f)
        } finally {
            connection.disconnect()
        }
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(CHUNK)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    /** The architectures an APK actually carries, read from its native library entries. */
    fun nativeAbis(file: File): Set<String> =
        ZipFile(file).use { zip ->
            zip.entries()
                .asSequence()
                .filter { it.name.startsWith("lib/") && it.name.endsWith(".so") }
                .mapNotNull { it.name.split('/').getOrNull(1) }
                .toSet()
        }

    private fun open(url: String): HttpURLConnection {
        val connection =
            (URL(url).openConnection() as? HttpURLConnection)
                ?: throw IOException("the release address is not HTTP")
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Accept", "application/json, application/vnd.android.package-archive")
        return connection
    }

    private fun InputStream.readLimited(limit: Int): String? {
        val buffer = ByteArray(limit)
        var filled = 0
        while (filled < limit) {
            val read = read(buffer, filled, limit - filled)
            if (read < 0) break
            filled += read
        }
        return if (filled == 0) null else String(buffer, 0, filled, Charsets.UTF_8)
    }

    private fun ByteArray.toHex(): String {
        val out = StringBuilder(size * 2)
        for (byte in this) {
            val value = byte.toInt() and 0xff
            out.append("0123456789abcdef"[value shr 4]).append("0123456789abcdef"[value and 0x0f])
        }
        return out.toString()
    }
}
