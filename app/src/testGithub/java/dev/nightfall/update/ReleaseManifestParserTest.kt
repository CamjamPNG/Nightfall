// SPDX-License-Identifier: MIT
package dev.nightfall.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseManifestParserTest {
    private fun refused(text: String): String {
        val problem = assertThrows(UpdateProblem::class.java) { ReleaseManifestParser.parse(text) }
        return problem.message.orEmpty()
    }

    private fun refusedWith(fragment: String, text: String) {
        val message = refused(text)
        assertTrue("expected a reason mentioning '$fragment', got '$message'", message.contains(fragment, ignoreCase = true))
    }

    @Test
    fun readsThePublishedShape() {
        val manifest = ReleaseManifestParser.parse(TestManifests.manifest())
        assertEquals("1.0.2", manifest.versionName)
        assertEquals(5, manifest.versionCode)
        assertEquals(TestManifests.PACKAGE, manifest.applicationId)
        assertEquals(26, manifest.minSdk)
        assertEquals(35, manifest.targetSdk)
        assertEquals(TestManifests.PIN, manifest.certificateSha256)
        assertEquals(5, manifest.apks.size)
        assertEquals(TestManifests.SCHEMA, manifest.schemaVersion)
        assertEquals(listOf("1.0.2", "1.0.1"), manifest.log.map { it.version })
        val arm64 = manifest.apks.first { it.abi == "arm64-v8a" }
        assertEquals("Nightfall-1.0.2-arm64-v8a.apk", arm64.file)
        assertEquals(TestManifests.SIZES.getValue("arm64-v8a"), arm64.bytes)
        assertEquals(
            "https://github.com/CamjamPNG/Nightfall/releases/download/v1.0.2/Nightfall-1.0.2-arm64-v8a.apk",
            arm64.url,
        )
    }

    @Test
    fun readsTheReleaseNotesTheManifestCarries() {
        val newest = ReleaseManifestParser.parse(TestManifests.manifest()).log.first()
        assertEquals("1.0.2", newest.version)
        assertEquals("2026-11-18", newest.date)
        assertEquals(
            listOf("feat(update): install a release on its own", "fix: say which window an update waits for"),
            newest.notes,
        )
    }

    @Test
    fun aManifestWithNoNotesStillUpdates() {
        val manifest = ReleaseManifestParser.parse(TestManifests.manifest(log = null, schemaVersion = null))
        assertEquals("the original document shape", 1, manifest.schemaVersion)
        assertTrue(manifest.log.isEmpty())
        assertEquals(5, manifest.versionCode)
        assertEquals(TestManifests.PIN, manifest.certificateSha256)
        assertEquals(5, manifest.apks.size)
    }

    @Test
    fun aNewerSchemaIsNotARefusal() {
        val manifest = ReleaseManifestParser.parse(TestManifests.manifest(schemaVersion = 9))
        assertEquals(9, manifest.schemaVersion)
        assertEquals(5, manifest.versionCode)
        assertEquals("a nonsense schema reads as the original shape", 1, ReleaseManifestParser.parse(TestManifests.manifest(schemaVersion = -4)).schemaVersion)
        assertEquals(1, ReleaseManifestParser.parse(TestManifests.manifest().replace(""""schemaVersion": 2""", """"schemaVersion": "two"""")).schemaVersion)
    }

    @Test
    fun hostileNotesNeverChangeTheTrustDecision() {
        val clean = ReleaseManifestParser.parse(TestManifests.manifest())
        val hostile =
            mapOf(
                "not a list" to "\"what changed: everything\"",
                "numbers" to "[1, 2, 3]",
                "entries that are not objects" to "[\"1.0.2\", 7, null]",
                "version that is not a version" to """[{"version": "1.0-beta", "notes": ["x"]}, {"version": "<script>", "notes": ["y"]}]""",
                "notes that are not strings" to """[{"version": "1.0.2", "date": "2026-11-18", "notes": [1, {"a": 1}, null]}]""",
                "a hostile date" to """[{"version": "1.0.2", "date": "yesterday\\nexpires: never", "notes": ["fine"]}]""",
                "the same version twice" to
                    """[{"version": "1.0.2", "notes": ["first"]}, {"version": "1.0.2", "notes": ["second"]}]""",
                "two hundred releases" to
                    (1..200).joinToString(",", "[", "]") { """{"version": "1.0.$it", "notes": ["$it"]}""" },
                "a note of two thousand characters" to
                    """[{"version": "1.0.2", "notes": ["${"long ".repeat(400)}"]}]""",
                "a note full of control characters" to
                    """[{"version": "1.0.2", "notes": ["a\u0000b\u0007c\u001bd\nd  e"]}]""",
            )
        for ((label, log) in hostile) {
            val parsed = ReleaseManifestParser.parse(TestManifests.manifest(log = log))
            withClue(label) {
                assertEquals(clean.versionName, parsed.versionName)
                assertEquals(clean.versionCode, parsed.versionCode)
                assertEquals(clean.certificateSha256, parsed.certificateSha256)
                assertEquals(clean.apks, parsed.apks)
                assertTrue("${parsed.log.size} entries", parsed.log.size <= UpdateLogParser.MAX_ENTRIES)
                for (entry in parsed.log) {
                    assertTrue(entry.notes.size <= UpdateLogParser.MAX_NOTES)
                    for (note in entry.notes) {
                        assertTrue(note.length <= UpdateLogParser.MAX_NOTE_CHARS)
                        assertTrue(note, note.none { it < ' ' || it == '\u007f' })
                    }
                    assertTrue("$label: ${entry.version}", entry.version.matches(Regex("""\d+\.\d+(?:\.\d+){0,2}""")))
                }
            }
        }
        assertEquals("duplicate versions are listed once", listOf("first"), ReleaseManifestParser.parse(TestManifests.manifest(log = """[{"version": "1.0.2", "notes": ["first"]}, {"version": "1.0.2", "notes": ["second"]}]""")).log.first().notes)
        assertTrue(ReleaseManifestParser.parse(TestManifests.manifest(log = "[1, 2, 3]")).log.isEmpty())
    }

    private fun withClue(label: String, block: () -> Unit) {
        try {
            block()
        } catch (e: AssertionError) {
            throw AssertionError("$label: ${e.message}", e)
        }
    }

    @Test
    fun ignoresKeysItDoesNotUse() {
        val text = TestManifests.manifest().replaceFirst("{", """{"notes": "hello", "channel": {"nested": [1]},""")
        assertEquals(5, ReleaseManifestParser.parse(text).versionCode)
    }

    @Test
    fun acceptsAnUppercaseCertificateDigestAndNormalisesIt() {
        val text = TestManifests.manifest(certificate = TestManifests.PIN.uppercase())
        assertEquals(TestManifests.PIN, ReleaseManifestParser.parse(text).certificateSha256)
    }

    @Test
    fun rejectsTextThatIsNotAManifest() {
        refusedWith("not valid JSON", "not json at all")
        refusedWith("not valid JSON", """{"versionName": }""")
        refusedWith("not a JSON object", "[1, 2, 3]")
        refusedWith("not a JSON object", "\"1.0.2\"")
        refusedWith("larger than expected", "x".repeat(70 * 1024))
    }

    @Test
    fun rejectsUnusableVersions() {
        for (version in listOf("1", "v1.0", "1.0-beta", "1.0.0.0.0", "", "1..0")) {
            refusedWith("version name", TestManifests.manifest(versionName = version))
        }
        refusedWith("versionCode", TestManifests.manifest(versionCode = 0))
        refusedWith("versionCode", TestManifests.manifest(versionCode = 2_100_000_001))
        refusedWith("versionCode", TestManifests.manifest().replace(""""versionCode": 5""", """"versionCode": "5""""))
        refusedWith("versionCode", TestManifests.manifest().replace(""""versionCode": 5""", """"versionCode": 5.5"""))
    }

    @Test
    fun rejectsUnusableSdkBoundsAndIdentities() {
        refusedWith("minSdk", TestManifests.manifest(minSdk = 0))
        refusedWith("targetSdk", TestManifests.manifest(minSdk = 33, targetSdk = 26))
        refusedWith("applicationId", TestManifests.manifest(applicationId = ""))
        refusedWith("certificate", TestManifests.manifest(certificate = "abc"))
        refusedWith("certificate", TestManifests.manifest(certificate = "z".repeat(64)))
    }

    @Test
    fun rejectsUnusableApkLists() {
        refusedWith("lists no APKs", TestManifests.manifest(apks = emptyList()))
        refusedWith("lists no APKs", TestManifests.manifest().replace(Regex("\"apks\": \\[[\\s\\S]*]"), "\"apks\": []"))
        val duplicate = listOf(TestManifests.apk("arm64-v8a"), TestManifests.apk("arm64-v8a"))
        refusedWith("arm64-v8a twice", TestManifests.manifest(apks = duplicate))
        val tooMany = (1..17).map { TestManifests.apk("arm64-v8a", file = "Nightfall-1.0.2-$it.apk") }
        refusedWith("too many", TestManifests.manifest(apks = tooMany))
    }

    @Test
    fun rejectsUnusableApkEntries() {
        refusedWith("unusable APK name", TestManifests.manifest(apks = listOf(TestManifests.apk("arm64-v8a", file = "../x.apk"))))
        refusedWith("unusable APK name", TestManifests.manifest(apks = listOf(TestManifests.apk("arm64-v8a", file = "sub/x.apk"))))
        refusedWith("unusable APK name", TestManifests.manifest(apks = listOf(TestManifests.apk("arm64-v8a", file = "x.zip"))))
        refusedWith("unknown architecture", TestManifests.manifest(apks = listOf(TestManifests.apk("mips"))))
        refusedWith("size", TestManifests.manifest(apks = listOf(TestManifests.apk("arm64-v8a", bytes = 0))))
        refusedWith("size", TestManifests.manifest(apks = listOf(TestManifests.apk("arm64-v8a", bytes = -5))))
        refusedWith("checksum", TestManifests.manifest(apks = listOf(TestManifests.apk("arm64-v8a", sha256 = "nothex"))))
        refusedWith("malformed APK entry", TestManifests.manifest(apks = listOf("\"just a string\"")))
        refusedWith("malformed APK entry", TestManifests.manifest(apks = listOf("42")))
        // Manifests published before the download URL existed carry no address for the APK.
        refusedWith(
            "no url",
            TestManifests.manifest(
                apks =
                    listOf(
                        """{"file": "Nightfall-1.0.2-arm64-v8a.apk", "abi": "arm64-v8a", "bytes": 1000, """ +
                            """"sha256": "${"a".repeat(64)}"}"""
                    )
            ),
        )
    }

    @Test
    fun rejectsAnyAddressOtherThanThisRepositorysReleaseAsset() {
        val hostile =
            listOf(
                "https://evil.example/Nightfall-1.0.2-arm64-v8a.apk",
                "http://github.com/CamjamPNG/Nightfall/releases/download/v1.0.2/Nightfall-1.0.2-arm64-v8a.apk",
                "https://github.com/other/Nightfall/releases/download/v1.0.2/Nightfall-1.0.2-arm64-v8a.apk",
                "https://github.com/CamjamPNG/Nightfall/releases/download/v9.9.9/Nightfall-1.0.2-arm64-v8a.apk",
                "https://github.com/CamjamPNG/Nightfall/releases/download/v1.0.2/other.apk",
                "https://github.com/CamjamPNG/Nightfall/releases/download/v1.0.2/Nightfall-1.0.2-arm64-v8a.apk?x=1",
                "https://github.com.evil.example/CamjamPNG/Nightfall/releases/download/v1.0.2/Nightfall-1.0.2-arm64-v8a.apk",
            )
        for (url in hostile) {
            refusedWith(
                "unexpected address",
                TestManifests.manifest(apks = listOf(TestManifests.apk("arm64-v8a", url = url))),
            )
        }
    }
}
