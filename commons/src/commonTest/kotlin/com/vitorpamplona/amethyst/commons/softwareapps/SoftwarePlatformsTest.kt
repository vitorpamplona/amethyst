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
package com.vitorpamplona.amethyst.commons.softwareapps

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SoftwarePlatformsTest {
    @Test
    fun readsAppendixAIdentifiers() {
        assertEquals(SoftwareOs.MACOS, SoftwarePlatforms.os("darwin-arm64"))
        assertEquals("Apple silicon", SoftwarePlatforms.arch("darwin-arm64"))
        assertEquals("Intel", SoftwarePlatforms.arch("darwin-x86_64"))
        assertEquals(SoftwareOs.ANDROID, SoftwarePlatforms.os("android-arm64-v8a"))
        assertEquals("ARM64", SoftwarePlatforms.arch("android-arm64-v8a"))
        assertEquals("ARMv7", SoftwarePlatforms.arch("android-armeabi-v7a"))
        assertEquals("x86-64", SoftwarePlatforms.arch("windows-x86_64"))
        assertEquals("ARM64", SoftwarePlatforms.arch("linux-aarch64"))
        assertEquals("RISC-V 64", SoftwarePlatforms.arch("linux-riscv64"))
        assertEquals(SoftwareOs.WASM, SoftwarePlatforms.os("wasi-wasm32"))
        assertEquals(SoftwareOs.OTHER, SoftwarePlatforms.os("haiku"))
        assertNull(SoftwarePlatforms.arch("haiku"))
    }

    @Test
    fun aMimeTypeAloneDecidesTheOsWhenThereAreNoFTags() {
        assertEquals(listOf(SoftwareOs.ANDROID), SoftwarePlatforms.osesOf(emptyList(), "application/vnd.android.package-archive"))
        assertEquals(listOf(SoftwareOs.LINUX), SoftwarePlatforms.osesOf(emptyList(), "application/vnd.debian.binary-package"))
        assertEquals(listOf(SoftwareOs.OTHER), SoftwarePlatforms.osesOf(emptyList(), "application/zip"))
        // `f` tags win over the MIME type: a zip is macOS when tagged so.
        assertEquals(listOf(SoftwareOs.MACOS), SoftwarePlatforms.osesOf(listOf("darwin-arm64"), "application/zip"))
    }

    @Test
    fun releasePlatformsCollapseToOrderedOses() {
        val armada = listOf("android-arm64-v8a", "android-armeabi-v7a", "darwin-arm64", "darwin-x86_64", "linux-x86_64", "windows-x86_64")
        assertEquals(listOf(SoftwareOs.ANDROID, SoftwareOs.MACOS, SoftwareOs.WINDOWS, SoftwareOs.LINUX), SoftwarePlatforms.osesOfPlatforms(armada))
    }

    @Test
    fun deviceFitPrefersTheNativePlatform() {
        val armMac = listOf("darwin-arm64", "darwin-x86_64")
        assertEquals(0, SoftwarePlatforms.deviceFit(listOf("darwin-arm64"), "application/zip", armMac))
        assertEquals(1, SoftwarePlatforms.deviceFit(listOf("darwin-x86_64"), "application/zip", armMac))
        assertNull(SoftwarePlatforms.deviceFit(listOf("linux-x86_64"), "application/vnd.appimage", armMac))

        val phone = listOf("android-arm64-v8a", "android-armeabi-v7a")
        assertEquals(0, SoftwarePlatforms.deviceFit(listOf("android-arm64-v8a", "android-armeabi-v7a"), "application/vnd.android.package-archive", phone))
        // A universal APK (no `f`) runs on any Android device, after exact matches.
        assertEquals(2, SoftwarePlatforms.deviceFit(emptyList(), "application/vnd.android.package-archive", phone))
        assertNull(SoftwarePlatforms.deviceFit(emptyList(), "application/x-msi", phone))
        assertNull(SoftwarePlatforms.deviceFit(listOf("android-arm64-v8a"), null, emptyList()))
    }
}
