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
package com.vitorpamplona.androiddevice

import com.vitorpamplona.androiddevice.avd.Avds
import com.vitorpamplona.androiddevice.device.EmulatorScreen
import com.vitorpamplona.androiddevice.emulator.EmulatorDiscovery
import com.vitorpamplona.androiddevice.emulator.EmulatorLauncher
import com.vitorpamplona.androiddevice.emulator.LaunchOptions
import com.vitorpamplona.androiddevice.emulator.displayAvailable
import com.vitorpamplona.devicecore.screen.ScreenSize
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmulatorDiscoveryTest {
    private fun tempDir(): File = Files.createTempDirectory("avd").toFile().also { it.deleteOnExit() }

    @Test
    fun readsTheDiscoveryFileEveryEmulatorWrites() {
        val dir = tempDir()
        File(dir, "pid_4242.ini").writeText(
            """
            port.serial=5556
            port.adb=5557
            avd.name=Pixel_9_API_36
            avd.dir=/home/me/.android/avd/Pixel_9_API_36.avd
            grpc.port=8556
            grpc.token=abc123
            cmdline="emulator" "-avd" "Pixel_9_API_36"
            """.trimIndent(),
        )
        // What Android Studio starts (emulator 37): a display name apart from the id, and JWT keys.
        File(dir, "pid_99_info.ini").writeText(
            "port.serial=5554\navd.name=Pixel 9 API 36\navd.id=Pixel_9_API_36\ngrpc.port=8554\n" +
                "grpc.jwks=/tmp/jwks\ngrpc.jwk_active=/tmp/jwks/active.jwk\n",
        )
        File(dir, "notes.txt").writeText("ignored")

        val running = EmulatorDiscovery.running(listOf(dir), alive = { it == 4242L || it == 99L }).sortedBy { it.pid }

        assertEquals(listOf(99L, 4242L), running.map { it.pid })
        val ours = running[1]
        assertEquals("emulator-5556", ours.serial)
        assertEquals("Pixel_9_API_36", ours.avdName)
        assertEquals(8556, ours.grpcPort)
        assertEquals("abc123", ours.grpcToken)
        assertTrue(ours.grpcReachable)
        // Android Studio's: JWT only, reached with a key dropped into its folder.
        val studio = running[0]
        assertTrue(studio.usesJwt)
        assertTrue(studio.grpcReachable)
        assertEquals("/tmp/jwks", studio.jwksDir)
        assertEquals("/tmp/jwks/active.jwk", studio.activeJwks)
        // Matched to AVDs by id; the display name is only for showing.
        assertEquals("Pixel_9_API_36", studio.avdName)
        assertEquals("Pixel 9 API 36", studio.avdDisplayName)
        // A file whose process is gone is stale.
        assertTrue(EmulatorDiscovery.running(listOf(dir), alive = { false }).isEmpty())
        assertEquals(5556, EmulatorDiscovery.find(4242, listOf(dir))?.consolePort)
        assertNull(EmulatorDiscovery.find(1, listOf(dir)))
    }

    @Test
    fun discoveryFoldersPerSystem() {
        assertEquals(
            File("/Users/me/Library/Caches/TemporaryItems/avd/running"),
            EmulatorDiscovery.directories("Mac OS X", emptyMap(), "/Users/me", "/tmp", "me").first(),
        )
        assertEquals(
            File("/run/user/1000/avd/running"),
            EmulatorDiscovery.directories("Linux", mapOf("XDG_RUNTIME_DIR" to "/run/user/1000"), "/home/me", "/tmp", "me").first(),
        )
        // Without XDG_RUNTIME_DIR, emulator 37 writes under ~/.android.
        assertEquals(
            File("/home/me/.android/avd/running"),
            EmulatorDiscovery.directories("Linux", emptyMap(), "/home/me", "/tmp", "me").first(),
        )
    }

    @Test
    fun listsAvdsFromTheirIniFiles() {
        val home = tempDir()
        val avd = File(home, "Pixel_9_API_36.avd").apply { mkdirs() }
        File(home, "Pixel_9_API_36.ini").writeText("avd.ini.encoding=UTF-8\npath=${avd.absolutePath}\ntarget=android-36\n")
        File(avd, "config.ini").writeText("avd.ini.displayname=Pixel 9 API 36\nabi.type=x86_64\nimage.sysdir.1=system-images/android-36/google_apis_playstore/x86_64/\nPlayStore.enabled=true\n")
        // A pointer to a folder that is gone is skipped.
        File(home, "Gone.ini").writeText("path=/nowhere/Gone.avd\n")

        val avds = Avds.list(home)

        assertEquals(1, avds.size)
        assertEquals("Pixel_9_API_36", avds[0].name)
        assertEquals("Pixel 9 API 36", avds[0].displayName)
        assertEquals(36, avds[0].apiLevel)
        assertEquals("x86_64", avds[0].abi)
        assertTrue(avds[0].playStore)
        assertEquals(File("/x/avd"), Avds.home(mapOf("ANDROID_AVD_HOME" to "/x/avd"), "/home/me"))
        assertEquals(File("/home/me/.android/avd"), Avds.home(emptyMap(), "/home/me"))
    }

    @Test
    fun startsHiddenWithItsOwnTokenGuardedGrpcPort() {
        val args = EmulatorLauncher(File("/sdk/emulator/emulator"), hasDisplay = { true }).arguments("Pixel", 8560, LaunchOptions(coldBoot = true))
        assertEquals(listOf("/sdk/emulator/emulator", "-avd", "Pixel", "-grpc", "8560", "-grpc-use-token", "-qt-hide-window", "-no-boot-anim", "-no-snapshot-load"), args)
        // A workstream's copy: several of one AVD run at once only when all are read-only.
        val copy = EmulatorLauncher(File("/sdk/emulator/emulator"), hasDisplay = { true }).arguments("Pixel", 8562, LaunchOptions(readOnly = true))
        assertEquals("-read-only", copy.last())
        // No display (a Linux server): a hidden window would still start Qt and abort the emulator.
        val headless = EmulatorLauncher(File("/sdk/emulator/emulator"), hasDisplay = { false }).arguments("Pixel", 8564, LaunchOptions())
        assertTrue("-no-window" in headless && "-qt-hide-window" !in headless, headless.toString())
        assertFalse(displayAvailable("Linux", emptyMap()))
        assertTrue(displayAvailable("Linux", mapOf("WAYLAND_DISPLAY" to "wayland-0")))
        assertTrue(displayAvailable("Mac OS X", emptyMap()))
    }

    @Test
    fun touchesLandInTheNaturalFrameWhateverTheRotation() {
        val natural = ScreenSize(1000, 2000)
        // Portrait: as shown.
        assertEquals(100 to 500, EmulatorScreen.toNatural(0.1f, 0.25f, 0, natural))
        // Landscape (top of the phone on the left): the frame's left edge is the panel's top.
        assertEquals(999 to 0, EmulatorScreen.toNatural(0f, 0f, 1, natural))
        assertEquals(0 to 0, EmulatorScreen.toNatural(0f, 1f, 1, natural))
        assertEquals(500 to 1000, EmulatorScreen.toNatural(0.5f, 0.5f, 1, natural))
        assertEquals(900 to 1500, EmulatorScreen.toNatural(0.1f, 0.25f, 2, natural))
        assertEquals(250 to 1800, EmulatorScreen.toNatural(0.1f, 0.25f, 3, natural))
    }
}
