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
package com.vitorpamplona.emulator

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmulatorDiscoveryTest {
    private val sample =
        """
        port.serial=5554
        port.adb=5555
        avd.name=Pixel_8_API_35
        avd.dir=/home/me/.android/avd/Pixel_8_API_35.avd
        avd.id=Pixel_8_API_35
        cmdline="/sdk/emulator/qemu/linux-x86_64/qemu-system-x86_64" "-avd" "Pixel_8_API_35" "-grpc" "8554" "-x=y"
        grpc.port=8554
        grpc.token=s3cr3t+/=
        some.future.key=kept
        """.trimIndent()

    private fun tempDir(): Path = Files.createTempDirectory("avd-running")

    @Test
    fun parsesTheDiscoveryFile() {
        val entries = EmulatorDiscovery.parseIni(sample.replace("\n", "\r\n"))
        val emu = EmulatorDiscovery.parse(1234, entries, Paths.get("pid_1234.ini"))
        assertEquals(1234, emu.pid)
        assertEquals("Pixel_8_API_35", emu.avdName)
        assertEquals("Pixel_8_API_35", emu.avdId)
        assertEquals(5554, emu.serialPort)
        assertEquals(5555, emu.adbPort)
        assertEquals("emulator-5554", emu.adbSerial)
        assertEquals(8554, emu.grpcPort)
        assertEquals("s3cr3t+/=", emu.grpcToken)
        assertTrue(emu.commandLine!!.endsWith("\"-x=y\""))
        assertEquals("kept", emu.entries["some.future.key"])
        assertFalse(emu.usesJwt)
    }

    @Test
    fun missingKeysAreNull() {
        val emu = EmulatorDiscovery.parse(1, EmulatorDiscovery.parseIni("# comment\nport.serial=abc\n\nnoequals\n"), Paths.get("pid_1.ini"))
        assertNull(emu.serialPort)
        assertNull(emu.grpcPort)
        assertNull(emu.grpcToken)
        assertNull(emu.avdName)
        assertEquals("emulator (1)", emu.name)
    }

    @Test
    fun listsLiveEmulatorsAndSkipsDeadOnes() {
        val dir = tempDir()
        dir.resolve("pid_100.ini").writeText(sample)
        dir.resolve("pid_200.ini").writeText(sample.replace("8554", "8556").replace("5554", "5556"))
        dir.resolve("pid_abc.ini").writeText(sample)
        dir.resolve("other.ini").writeText(sample)
        val discovery = EmulatorDiscovery(listOf(dir, dir.resolve("missing"))) { it == 200L }
        val running = discovery.running()
        assertEquals(listOf(200L), running.map { it.pid })
        assertEquals(8556, running.single().grpcPort)
        assertEquals(dir.resolve("pid_200.ini"), running.single().discoveryFile)
    }

    @Test
    fun theDefaultAliveCheckUsesRealProcesses() {
        val dir = tempDir()
        val me = ProcessHandle.current().pid()
        dir.resolve("pid_$me.ini").writeText(sample)
        // pid_1 is init (alive) on Linux; a huge pid is never alive.
        dir.resolve("pid_${Int.MAX_VALUE}.ini").writeText(sample)
        assertEquals(listOf(me), EmulatorDiscovery(listOf(dir)).running().map { it.pid })
    }

    @Test
    fun defaultDirectoriesPerOs() {
        assertEquals(
            listOf(Paths.get("/run/user/1000/avd/running"), Paths.get("/tmp/android-me/avd/running"), Paths.get("/home/me/.android/avd/running")),
            EmulatorDiscovery.defaultDirectories("Linux", mapOf("XDG_RUNTIME_DIR" to "/run/user/1000", "USER" to "me"), "/home/me", "x"),
        )
        assertEquals(
            listOf(Paths.get("/tmp/android-x/avd/running"), Paths.get("/home/me/.android/avd/running")),
            EmulatorDiscovery.defaultDirectories("Linux", emptyMap(), "/home/me", "x"),
        )
        assertEquals(
            listOf(Paths.get("/Users/me/Library/Caches/TemporaryItems/avd/running"), Paths.get("/Users/me/.android/avd/running")),
            EmulatorDiscovery.defaultDirectories("Mac OS X", emptyMap(), "/Users/me", "me"),
        )
        assertEquals(
            listOf(Paths.get("C:\\Users\\me\\AppData\\Local", "Temp", "avd", "running"), Paths.get("C:\\Users\\me", ".android", "avd", "running")),
            EmulatorDiscovery.defaultDirectories("Windows 11", mapOf("LOCALAPPDATA" to "C:\\Users\\me\\AppData\\Local"), "C:\\Users\\me", "me"),
        )
    }

    @Test
    fun androidUserHomeMovesTheFallback() {
        assertEquals(
            listOf(Paths.get("/tmp/android-x/avd/running"), Paths.get("/data/android/avd/running")),
            EmulatorDiscovery.defaultDirectories("Linux", mapOf("ANDROID_USER_HOME" to "/data/android"), "/home/me", "x"),
        )
    }

    @Test
    fun theTokenNeverPrints() {
        val emu = EmulatorDiscovery.parse(1, EmulatorDiscovery.parseIni("grpc.port=8554\ngrpc.token=s3cr3t\n"), Paths.get("pid_1.ini"))
        assertFalse(emu.toString().contains("s3cr3t"))
    }

    @Test
    fun jwtEmulatorsAreFlagged() {
        val emu = EmulatorDiscovery.parse(1, EmulatorDiscovery.parseIni("grpc.port=8554\ngrpc.jwks=/tmp/jwks\n"), Paths.get("pid_1.ini"))
        assertTrue(emu.usesJwt)
    }

    @Test
    fun pidComesFromTheFileName() {
        assertEquals(42L, EmulatorDiscovery.pidOf(Paths.get("/x/pid_42.ini")))
        assertNull(EmulatorDiscovery.pidOf(Paths.get("/x/pid_.ini")))
        assertNull(EmulatorDiscovery.pidOf(Paths.get("/x/42.ini")))
    }
}
