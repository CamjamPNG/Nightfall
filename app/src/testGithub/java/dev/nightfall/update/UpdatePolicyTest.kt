// SPDX-License-Identifier: MIT
package dev.nightfall.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {
    private val manifest = ReleaseManifestParser.parse(TestManifests.manifest())

    private fun refused(block: () -> Unit): String {
        val problem = assertThrows(UpdateProblem::class.java) { block() }
        return problem.message.orEmpty()
    }

    private fun downloaded(
        abi: String = "arm64-v8a",
        size: Long = TestManifests.SIZES.getValue("arm64-v8a"),
        sha256: String = TestManifests.SHAS.getValue("arm64-v8a"),
        packageName: String? = TestManifests.PACKAGE,
        versionCode: Int? = 5,
        certificates: List<String> = listOf(TestManifests.PIN),
        abis: Set<String> = setOf(abi),
    ) = DownloadedApk(size, sha256, packageName, versionCode, certificates, abis)

    @Test
    fun picksTheApkForThisDevice() {
        val update = UpdatePolicy.nextUpdate(manifest, TestManifests.installed, TestManifests.phone)
        assertEquals("1.0.2", update.versionName)
        assertEquals(5, update.versionCode)
        assertEquals("arm64-v8a", update.apk.abi)
    }

    @Test
    fun prefersAnExactArchitectureOverUniversal() {
        val installed = TestManifests.installed
        val x86 = DeviceProfile(listOf("x86_64", "x86"), 35)
        assertEquals("x86_64", UpdatePolicy.nextUpdate(manifest, installed, x86).apk.abi)
        val universalOnly =
            ReleaseManifestParser.parse(
                TestManifests.manifest(apks = listOf(TestManifests.apk("universal")))
            )
        assertEquals("universal", UpdatePolicy.nextUpdate(universalOnly, installed, x86).apk.abi)
    }

    @Test
    fun refusesAnUpdateForAnotherPackage() {
        val other = TestManifests.installed.copy(packageName = "dev.nightfall")
        assertTrue(refused { UpdatePolicy.nextUpdate(manifest, other, TestManifests.phone) }.contains("io.github.camjampng.nightfall"))
    }

    @Test
    fun refusesDowngradesAndReplays() {
        val same = TestManifests.installed.copy(versionCode = 5, versionName = "1.0.2")
        assertTrue(refused { UpdatePolicy.nextUpdate(manifest, same, TestManifests.phone) }.contains("downgrades are refused"))
        val newer = TestManifests.installed.copy(versionCode = 9, versionName = "1.0.3")
        assertTrue(refused { UpdatePolicy.nextUpdate(manifest, newer, TestManifests.phone) }.contains("not newer"))
    }

    @Test
    fun refusesAVersionNameThatIsOlderThanTheInstalledOne() {
        val ahead = TestManifests.installed.copy(versionCode = 3, versionName = "1.1")
        val message = refused { UpdatePolicy.nextUpdate(manifest, ahead, TestManifests.phone) }
        assertTrue(message, message.contains("not newer"))
    }

    @Test
    fun refusesADifferentSigningChannel() {
        val foreign = TestManifests.installed.copy(certificateSha256 = "b".repeat(64))
        assertTrue(refused { UpdatePolicy.nextUpdate(manifest, foreign, TestManifests.phone) }.contains("different channel"))
    }

    @Test
    fun refusesAReleaseThisDeviceCannotRun() {
        val old = TestManifests.phone.copy(sdk = 26)
        val newerManifest = ReleaseManifestParser.parse(TestManifests.manifest(minSdk = 30))
        val message = refused { UpdatePolicy.nextUpdate(newerManifest, TestManifests.installed, old) }
        assertTrue(message, message.contains("API 30"))
    }

    @Test
    fun refusesAReleaseWithoutAnApkForThisArchitecture() {
        val intelOnly =
            ReleaseManifestParser.parse(
                TestManifests.manifest(apks = listOf(TestManifests.apk("x86_64"), TestManifests.apk("x86")))
            )
        val message = refused { UpdatePolicy.nextUpdate(intelOnly, TestManifests.installed, TestManifests.phone) }
        assertTrue(message, message.contains("arm64-v8a"))
    }

    @Test
    fun acceptsAVerifiedDownload() {
        val update = UpdatePolicy.nextUpdate(manifest, TestManifests.installed, TestManifests.phone)
        UpdatePolicy.verifyDownloaded(update, downloaded(), TestManifests.installed)
    }

    @Test
    fun refusesADownloadThatIsNotTheReleasedOne() {
        val update = UpdatePolicy.nextUpdate(manifest, TestManifests.installed, TestManifests.phone)
        assertTrue(
            refused { UpdatePolicy.verifyDownloaded(update, downloaded(size = 1), TestManifests.installed) }
                .contains("discarded")
        )
        assertTrue(
            refused {
                    UpdatePolicy.verifyDownloaded(
                        update,
                        downloaded(sha256 = "c".repeat(64)),
                        TestManifests.installed,
                    )
                }
                .contains("checksum")
        )
        assertTrue(
            refused {
                    UpdatePolicy.verifyDownloaded(
                        update,
                        downloaded(packageName = "com.evil.app"),
                        TestManifests.installed,
                    )
                }
                .contains("com.evil.app")
        )
        assertTrue(
            refused { UpdatePolicy.verifyDownloaded(update, downloaded(packageName = null), TestManifests.installed) }
                .contains("unknown package")
        )
        assertTrue(
            refused {
                    UpdatePolicy.verifyDownloaded(update, downloaded(versionCode = 6), TestManifests.installed)
                }
                .contains("version code")
        )
        assertTrue(
            refused { UpdatePolicy.verifyDownloaded(update, downloaded(versionCode = null), TestManifests.installed) }
                .contains("version code")
        )
    }

    @Test
    fun refusesADownloadSignedBySomethingElse() {
        val update = UpdatePolicy.nextUpdate(manifest, TestManifests.installed, TestManifests.phone)
        assertTrue(
            refused { UpdatePolicy.verifyDownloaded(update, downloaded(certificates = emptyList()), TestManifests.installed) }
                .contains("signing certificates")
        )
        assertTrue(
            refused {
                    UpdatePolicy.verifyDownloaded(
                        update,
                        downloaded(certificates = listOf(TestManifests.PIN, "d".repeat(64))),
                        TestManifests.installed,
                    )
                }
                .contains("signing certificates")
        )
        assertTrue(
            refused {
                    UpdatePolicy.verifyDownloaded(
                        update,
                        downloaded(certificates = listOf("e".repeat(64))),
                        TestManifests.installed,
                    )
                }
                .contains("different certificate")
        )
    }

    @Test
    fun refusesAnApkCarryingTheWrongArchitecture() {
        val update = UpdatePolicy.nextUpdate(manifest, TestManifests.installed, TestManifests.phone)
        assertTrue(
            refused { UpdatePolicy.verifyDownloaded(update, downloaded(abis = setOf("x86_64")), TestManifests.installed) }
                .contains("x86_64")
        )
        assertTrue(
            refused { UpdatePolicy.verifyDownloaded(update, downloaded(abis = emptySet()), TestManifests.installed) }
                .contains("no native libraries")
        )
        val universal =
            UpdatePolicy.nextUpdate(
                ReleaseManifestParser.parse(TestManifests.manifest(apks = listOf(TestManifests.apk("universal")))),
                TestManifests.installed,
                TestManifests.phone,
            )
        UpdatePolicy.verifyDownloaded(
            universal,
            DownloadedApk(
                universal.apk.bytes,
                universal.apk.sha256,
                TestManifests.PACKAGE,
                5,
                listOf(TestManifests.PIN),
                setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64"),
            ),
            TestManifests.installed,
        )
        assertTrue(
            refused {
                    UpdatePolicy.verifyDownloaded(
                        universal,
                        DownloadedApk(
                            universal.apk.bytes,
                            universal.apk.sha256,
                            TestManifests.PACKAGE,
                            5,
                            listOf(TestManifests.PIN),
                            emptySet(),
                        ),
                        TestManifests.installed,
                    )
                }
                .contains("no native libraries")
        )
    }

    @Test
    fun comparesVersionNamesNumerically() {
        assertTrue(compareVersions("1.0.1", "1.0.0.9") > 0)
        assertTrue(compareVersions("1.10", "1.9.9.9") > 0)
        assertTrue(compareVersions("1.0", "1.0.0.0") == 0)
        assertTrue(compareVersions("1.0.0.1", "1.0") > 0)
        assertTrue(compareVersions("2.0", "1.99.99.99") > 0)
        assertThrows(UpdateProblem::class.java) { compareVersions("1.0-beta", "1.0") }
    }
}
