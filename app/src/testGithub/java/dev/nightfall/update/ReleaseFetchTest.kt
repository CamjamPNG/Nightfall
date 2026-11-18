// SPDX-License-Identifier: MIT
package dev.nightfall.update

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Exercises the channel's HTTP and hashing behaviour against a local socket server, never the
 * internet, so the redirects, truncation and size limits are actually run rather than assumed.
 */
class ReleaseFetchTest {
    @get:Rule val temporary = TemporaryFolder()

    /** What the local server answers for a path. */
    private data class Reply(
        val status: Int,
        val body: ByteArray = ByteArray(0),
        val headers: List<Pair<String, String>> = emptyList(),
        val announcedLength: Int = body.size,
        val truncateAfter: Int = -1,
    )

    private class LocalServer(private val routes: (String) -> Reply) : Closeable {
        private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        private val worker: Thread

        init {
            worker =
                Thread {
                    while (!socket.isClosed) {
                        try {
                            socket.accept().use { connection -> answer(connection) }
                        } catch (e: IOException) {
                            return@Thread
                        }
                    }
                }
            worker.isDaemon = true
            worker.start()
        }

        fun url(path: String) = "http://127.0.0.1:${socket.localPort}$path"

        private fun answer(connection: Socket) {
            val input = connection.getInputStream().bufferedReader()
            val first = input.readLine() ?: return
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) break
            }
            val path = first.split(' ').getOrElse(1) { "/" }
            val reply = routes(path)
            val output = connection.getOutputStream()
            val head = StringBuilder("HTTP/1.1 ${reply.status} STATUS\r\n")
            head.append("Connection: close\r\n")
            head.append("Content-Length: ${reply.announcedLength}\r\n")
            for ((name, value) in reply.headers) head.append("$name: $value\r\n")
            head.append("\r\n")
            output.write(head.toString().toByteArray())
            if (reply.body.isNotEmpty()) {
                val length = if (reply.truncateAfter >= 0) reply.truncateAfter else reply.body.size
                output.write(reply.body, 0, minOf(length, reply.body.size))
            }
            output.flush()
        }

        override fun close() {
            socket.close()
        }
    }

    private var server: LocalServer? = null

    private fun serve(routes: Map<String, Reply>) {
        server = LocalServer { path -> routes[path] ?: Reply(404) }
    }

    private fun url(path: String) = server!!.url(path)

    @Before
    fun start() {
        serve(emptyMap())
    }

    @After
    fun stop() {
        server?.close()
        server = null
    }

    @Test
    fun readsTheManifestAndFollowsTheRedirectToIt() {
        val manifest = TestManifests.manifest()
        serve(
            mapOf(
                "/direct.json" to Reply(200, manifest.toByteArray()),
                "/latest/manifest.json" to Reply(302, headers = listOf("Location" to "/direct.json")),
            )
        )
        assertEquals(manifest, ReleaseFetch.manifestText(url("/direct.json")))
        assertEquals(manifest, ReleaseFetch.manifestText(url("/latest/manifest.json")))
        assertEquals(5, ReleaseManifestParser.parse(ReleaseFetch.manifestText(url("/latest/manifest.json"))).versionCode)
    }

    @Test
    fun reportsAnUnreachableManifestAsAnError() {
        val failure = assertThrows(IOException::class.java) { ReleaseFetch.manifestText(url("/missing.json")) }
        assertTrue(failure.message.orEmpty(), failure.message.orEmpty().contains("404"))
    }

    @Test
    fun downloadsTheWholeFileAndReportsMonotonicProgress() {
        val payload = ByteArray(300_000) { (it % 251).toByte() }
        serve(mapOf("/apk" to Reply(200, payload)))
        val target = temporary.newFile("downloaded.apk")
        val seen = mutableListOf<Float>()
        ReleaseFetch.download(url("/apk"), target, payload.size.toLong()) { seen.add(it) }
        assertEquals(payload.size.toLong(), target.length())
        assertEquals(ReleaseFetch.sha256(payload), ReleaseFetch.sha256(target))
        assertTrue("progress must start below half: $seen", seen.first() <= 0.5f)
        assertEquals(1f, seen.last(), 0f)
        assertEquals("progress must not go backwards: $seen", seen.sorted(), seen)
        assertTrue("progress must be reported more than once: $seen", seen.size > 1)
    }

    @Test
    fun refusesAResponseLargerThanTheReleaseLists() {
        val payload = ByteArray(200_000) { 1 }
        serve(mapOf("/big" to Reply(200, payload)))
        val target = temporary.newFile("too-big.apk")
        val refused = assertThrows(UpdateProblem::class.java) {
            ReleaseFetch.download(url("/big"), target, 100_000) { }
        }
        assertTrue(refused.message.orEmpty(), refused.message.orEmpty().contains("discarded"))
    }

    @Test
    fun aDownloadThatDiesHalfWayNeverReportsSuccess() {
        serve(mapOf("/truncated" to Reply(200, ByteArray(500_000) { 7 }, announcedLength = 500_000, truncateAfter = 10_000)))
        val target = temporary.newFile("truncated.apk")
        val seen = mutableListOf<Float>()
        val refused = assertThrows(Exception::class.java) {
            ReleaseFetch.download(url("/truncated"), target, 500_000) { seen.add(it) }
        }
        assertTrue("a half download must never report completion: $seen", seen.none { it >= 1f })
        assertTrue(refused.message.orEmpty(), refused.message.orEmpty().contains("stopped after 10000 of 500000"))
    }

    @Test
    fun readsTheNativeArchitecturesAnApkCarries() {
        val apk = temporary.newFile("variant.apk")
        java.util.zip.ZipOutputStream(apk.outputStream()).use { zip ->
            for (entry in listOf("classes.dex", "lib/arm64-v8a/libnightfall.so", "lib/arm64-v8a/libother.so")) {
                zip.putNextEntry(java.util.zip.ZipEntry(entry))
                zip.write(ByteArray(8))
                zip.closeEntry()
            }
        }
        assertEquals(setOf("arm64-v8a"), ReleaseFetch.nativeAbis(apk))
        assertThrows(IOException::class.java) { ReleaseFetch.nativeAbis(temporary.newFile("not-an-apk.zip")) }
    }

    @Test
    fun hashesMatchTheStandardDigest() {
        val file: File = temporary.newFile("bytes.bin")
        file.writeBytes("nightfall".toByteArray())
        val expected =
            java.security.MessageDigest.getInstance("SHA-256")
                .digest("nightfall".toByteArray()).joinToString("") { "%02x".format(it) }
        assertEquals(expected, ReleaseFetch.sha256(file))
        assertEquals(ReleaseFetch.sha256(file), ReleaseFetch.sha256("nightfall".toByteArray()))
        assertEquals(64, ReleaseFetch.sha256(file).length)
    }
}
