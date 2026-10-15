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
package com.vitorpamplona.amethyst.commons.appupdate

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * `fixture-signed.apk` is a 25 KB APK built with aapt2 and signed with apksigner (v2 + v3) by a
 * throwaway EC key: package com.vitorpamplona.amethyst, version 1.19.0 (464), SDK 26 to 37, one
 * `lib/arm64-v8a/` entry. `fixture-unsigned.apk` is the same before signing.
 */
class ApkInspectorTest {
    private fun fixture(name: String) = File(requireNotNull(javaClass.getResource("/appupdate/$name")) { name }.toURI())

    @Test
    fun readsTheManifestAndTheSigner() {
        val info = ApkInspector.inspect(fixture("fixture-signed.apk"))
        assertEquals("com.vitorpamplona.amethyst", info.packageName)
        assertEquals("1.19.0", info.versionName)
        assertEquals(464L, info.versionCode)
        assertEquals(26, info.minSdk)
        assertEquals(37, info.targetSdk)
        assertEquals(listOf("arm64-v8a"), info.nativeAbis)
        assertEquals(listOf("android-arm64-v8a"), info.platforms)
        // apksigner verify --print-certs: "certificate SHA-256 digest"
        assertEquals("8d1cefa22c2246ae3ef1fe600546626d166a7a32ef625d21aea6f96cb35a629f", info.certificateSha256)
    }

    @Test
    fun refusesAnUnsignedApk() {
        assertFailsWith<ApkInspector.InvalidApkException> { ApkInspector.inspect(fixture("fixture-unsigned.apk")) }
    }

    /**
     * Against a real release APK, when one is given (`AMETHYST_REAL_APK=/path/to.apk`): the values must be
     * the ones zsp published for it. For amethyst-googleplay-arm64-v8a-v1.19.0.apk, Zapstore's
     * asset event says version_code 464, SDK 26/37, android-arm64-v8a and this certificate.
     */
    @Test
    fun matchesWhatZspPublishedForARealApk() {
        val path = System.getenv("AMETHYST_REAL_APK") ?: return
        println("Checking $path against zsp's published asset event")
        val info = ApkInspector.inspect(File(path))
        assertEquals("com.vitorpamplona.amethyst", info.packageName)
        assertEquals("1.19.0", info.versionName)
        assertEquals(464L, info.versionCode)
        assertEquals(26, info.minSdk)
        assertEquals(37, info.targetSdk)
        assertEquals(listOf("android-arm64-v8a"), info.platforms)
        assertEquals("c2d0aa86bcb6b62090561a41bbe336e98b78c2d0210a498dc885f28e1348cf17", info.certificateSha256)
        assertEquals("158457da5adaad9f915bc2f5f933d12d17a26d325d70e8086ea4bf1d80bb4f2e", ApkInspector.sha256(File(path)))
    }
}
