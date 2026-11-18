/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.mavenReleases

import com.vitorpamplona.quartz.nipFCMavenRepositories.MavenModule
import com.vitorpamplona.quartz.nipFCMavenRepositories.release.MavenReleaseEvent
import com.vitorpamplona.quartz.nipFCMavenRepositories.snapshot.MavenSnapshotEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

internal const val SIGNER = "e9867c970848e300d6fca32bbd600c2fd4bd92c760ae5707355058720991955c"
internal const val MANIFEST = "1012859c6c4548599c203c41f2126fcc0ca2138feae1f2acf84f42222f96825e"

internal fun release(
    version: String,
    modules: List<String> = listOf("com.example:lib", "com.example:lib-jvm"),
    createdAt: Long = 1_700_000_000L,
    id: String = "01".repeat(32),
    extra: List<Array<String>> = emptyList(),
) = MavenReleaseEvent(
    id,
    SIGNER,
    createdAt,
    (
        modules.map { arrayOf("j", it) } +
            listOf(arrayOf("v", version), arrayOf("manifest", MANIFEST, "5784"), arrayOf("x", MANIFEST), arrayOf("x", "ab".repeat(32)), arrayOf("server", "https://nostr.download")) +
            extra
    ).toTypedArray(),
    "",
    "22".repeat(64),
)

class MavenReleaseSummaryTest {
    // crypto-kmp 2.1.1's modules, as its release lists them.
    private val crypto =
        release(
            "2.1.1",
            listOf(
                "com.vitorpamplona.crypto:crypto",
                "com.vitorpamplona.crypto:crypto-android",
                "com.vitorpamplona.crypto:crypto-iosarm64",
                "com.vitorpamplona.crypto:crypto-iossimulatorarm64",
                "com.vitorpamplona.crypto:crypto-js",
                "com.vitorpamplona.crypto:crypto-jvm",
                "com.vitorpamplona.crypto:crypto-linuxx64",
                "com.vitorpamplona.crypto:crypto-macosarm64",
                "com.vitorpamplona.crypto:crypto-wasm-js",
            ),
        )

    @Test
    fun readsTheTargetsAsPlatforms() {
        val summary = MavenReleaseSummary(crypto)
        assertEquals(
            listOf(KmpPlatform.ANDROID, KmpPlatform.JVM, KmpPlatform.IOS, KmpPlatform.MACOS, KmpPlatform.LINUX, KmpPlatform.JS, KmpPlatform.WASM),
            summary.platforms,
        )
        assertEquals(emptyList(), summary.otherModules)
        assertEquals("com.vitorpamplona.crypto:crypto:2.1.1", summary.coordinate.toString())
        assertEquals(1, summary.artifactCount)
        assertEquals(5784L, summary.manifestSize)
        assertTrue(summary.isWellFormed)
        assertFalse(summary.isSnapshot)
    }

    @Test
    fun readsComposeTargetNames() {
        val summary = MavenReleaseSummary(release("1.0", listOf("org.jetbrains.compose.ui:ui", "org.jetbrains.compose.ui:ui-desktop", "org.jetbrains.compose.ui:ui-uikitarm64")))
        assertEquals(listOf(KmpPlatform.JVM, KmpPlatform.IOS), summary.platforms)
        assertEquals(emptyList(), summary.otherModules)
    }

    @Test
    fun countsArtifactsWhereverTheManifestHashSits() {
        // The manifest's x tag after an artifact's, and in upper case.
        val event = release("1.0", extra = listOf(arrayOf("x", "cd".repeat(32))))
        val reordered =
            MavenReleaseEvent(event.id, event.pubKey, event.createdAt, event.tags.filter { it[0] != "x" }.toTypedArray() + arrayOf(arrayOf("x", "ab".repeat(32)), arrayOf("x", MANIFEST.uppercase())), "", event.sig)
        assertEquals(2, MavenReleaseSummary(event).artifactCount)
        assertEquals(1, MavenReleaseSummary(reordered).artifactCount)
    }

    @Test
    fun keepsModulesThatAreNoTarget() {
        val summary = MavenReleaseSummary(release("1.0", listOf("com.example:lib", "com.example:lib-bom", "com.example:lib-jvm", "org.other:thing")))
        assertEquals(listOf(KmpPlatform.JVM), summary.platforms)
        assertEquals(listOf("lib-bom", "org.other:thing"), summary.otherModules)
    }

    @Test
    fun theSnippetsUseMagpieForTheGroup() {
        val summary = MavenReleaseSummary(crypto)
        assertEquals("implementation(\"com.vitorpamplona.crypto:crypto:2.1.1\")", summary.dependency)
        assertTrue(summary.settings!!.contains("id(\"com.vitorpamplona.magpie\") version \"${MavenSetup.MAGPIE_VERSION}\""))
        assertTrue(summary.settings!!.contains("group(\"com.vitorpamplona.crypto\")"))
        assertTrue(summary.settings!!.startsWith("// settings.gradle.kts\n"))
    }

    @Test
    fun aSnapshotIsMarked() {
        val snapshot =
            MavenSnapshotEvent(
                "03".repeat(32),
                SIGNER,
                1_700_000_000L,
                arrayOf(
                    arrayOf("d", "com.example:lib:1.1.0-SNAPSHOT"),
                    arrayOf("j", "com.example:lib"),
                    arrayOf("v", "1.1.0-SNAPSHOT"),
                    arrayOf("manifest", MANIFEST, "10"),
                ),
                "",
                "22".repeat(64),
            )
        val summary = MavenReleaseSummary(snapshot)
        assertTrue(summary.isSnapshot)
        assertTrue(summary.isWellFormed)
    }

    @Test
    fun picksTheNewestCanonicalRelease() {
        val module = MavenModule("com.example", "lib")
        val old = release("1.0.0", id = "01".repeat(32))
        val newLate = release("1.2.0", createdAt = 1_700_000_500L, id = "02".repeat(32))
        val newEarly = release("1.2.0", createdAt = 1_700_000_100L, id = "03".repeat(32))
        val rc = release("1.3.0-rc1", id = "04".repeat(32))
        val other = release("9.0.0", listOf("com.example:other"), id = "05".repeat(32))

        assertSame(newEarly, MavenLibraryVersions.newestRelease(listOf(old, newLate, newEarly, other), module))
        assertSame(rc, MavenLibraryVersions.newestRelease(listOf(old, newLate, rc), module))
        assertNull(MavenLibraryVersions.newestRelease(listOf(other), module))
    }
}
