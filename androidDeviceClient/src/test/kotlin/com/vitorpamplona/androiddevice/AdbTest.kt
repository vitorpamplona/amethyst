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

import com.vitorpamplona.androiddevice.adb.Adb
import com.vitorpamplona.devicecore.CommandResult
import com.vitorpamplona.devicecore.CommandRunner
import com.vitorpamplona.devicecore.input.DeviceKey
import com.vitorpamplona.devicecore.screen.ScreenSize
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdbTest {
    /** Records each command and answers from [answers], by the command's tail. */
    private class FakeRunner(
        val answers: (List<String>) -> CommandResult = { CommandResult(0, ByteArray(0), "") },
    ) : CommandRunner {
        val commands = mutableListOf<List<String>>()

        override suspend fun run(
            command: List<String>,
            timeoutMs: Long,
            environment: Map<String, String>,
            directory: File?,
            log: File?,
        ): CommandResult {
            commands += command
            return answers(command)
        }

        override fun start(
            command: List<String>,
            environment: Map<String, String>,
            log: File?,
        ): Process = throw UnsupportedOperationException()
    }

    private fun ok(text: String) = CommandResult(0, text.toByteArray(), "")

    @Test
    fun parsesTheDeviceList() {
        val out =
            """
            * daemon not running; starting now at tcp:5037
            * daemon started successfully
            List of devices attached
            emulator-5554          device product:sdk_gphone64_x86_64 model:sdk_gphone64_x86_64 device:emu64xa transport_id:1
            R58M123ABC             unauthorized usb:1-1 transport_id:2
            0A1B2C                 no permissions (missing udev rules?); see [http://developer.android.com/tools/device.html] usb:1-2 transport_id:3

            """.trimIndent()
        val devices = Adb.parseDevices(out)
        assertEquals(listOf("emulator-5554", "R58M123ABC", "0A1B2C"), devices.map { it.serial })
        assertTrue(devices[0].isOnline && devices[0].isEmulator)
        assertEquals(5554, devices[0].consolePort)
        assertEquals("sdk gphone64 x86 64", devices[0].model)
        assertEquals("unauthorized", devices[1].state)
        assertTrue(devices[2].state.startsWith("no permissions"))
    }

    @Test
    fun readsWmSize() {
        assertEquals(ScreenSize(1080, 2400), Adb.parseWmSize("Physical size: 1080x2400\n")!!.logical)
        val overridden = Adb.parseWmSize("Physical size: 1440x3120\nOverride size: 1080x2340\n")!!
        assertEquals(ScreenSize(1440, 3120), overridden.physical)
        assertEquals(ScreenSize(1080, 2340), overridden.logical)
        assertNull(Adb.parseWmSize("error"))
    }

    @Test
    fun quotesForTheDeviceShell() {
        assertEquals("input", Adb.quote("input"))
        assertEquals("'hello world'", Adb.quote("hello world"))
        assertEquals("'it'\\''s'", Adb.quote("it's"))
        assertEquals("'a;b&c'", Adb.quote("a;b&c"))
        assertEquals("''", Adb.quote(""))
    }

    @Test
    fun typesSpacesAsPercentSAndNewlinesAsEnter() =
        runBlocking {
            val runner = FakeRunner()
            val adb = Adb(File("/sdk/adb"), runner)
            adb.type("emulator-5554", "hi there\nok")
            assertEquals(
                listOf(
                    listOf("/sdk/adb", "-s", "emulator-5554", "shell", "input text hi%sthere"),
                    listOf("/sdk/adb", "-s", "emulator-5554", "shell", "input keyevent 66"),
                    listOf("/sdk/adb", "-s", "emulator-5554", "shell", "input text ok"),
                ),
                runner.commands,
            )
            assertFailsWith<IOException> { adb.type("emulator-5554", "olá") }
            Unit
        }

    @Test
    fun findsTheHierarchyInWhatUiautomatorPrinted() =
        runBlocking {
            val xml = "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?><hierarchy rotation=\"0\"><node text=\"Hi\"/></hierarchy>"
            val runner = FakeRunner { ok(xml + "UI hierchary dumped to: /dev/tty\n") }
            assertEquals(xml, Adb(File("adb"), runner).uiDump("s"))
        }

    @Test
    fun aKeyIsFoundByItsSpokenName() {
        assertEquals(DeviceKey.VOLUME_UP, DeviceKey.named("Volume up"))
        assertEquals(DeviceKey.BACK, DeviceKey.named("back"))
        assertEquals(DeviceKey.RECENTS, DeviceKey.named("app_switch"))
        assertNull(DeviceKey.named("teleport"))
    }
}

class RawScreencapTest {
    private fun raw(
        width: Int,
        height: Int,
        headerInts: Int,
    ): ByteArray {
        val out = ByteArrayOutputStream()

        fun int(v: Int) = repeat(4) { out.write((v ushr (8 * it)) and 0xff) }
        int(width)
        int(height)
        int(1)
        if (headerInts == 4) int(0)
        repeat(width * height) { i -> out.write(byteArrayOf(i.toByte(), 2, 3, -1)) }
        return out.toByteArray()
    }

    @Test
    fun readsBothHeaderLayouts() {
        listOf(3, 4).forEach { ints ->
            val screen = Adb.parseRawScreencap(raw(3, 2, ints))
            assertEquals(3, screen.width)
            assertEquals(2, screen.height)
            assertEquals(24, screen.rgba.size)
            assertEquals(1.toByte(), screen.rgba[4])
        }
    }

    @Test
    fun refusesWhatIsntARawScreen() {
        assertFailsWith<IOException> { Adb.parseRawScreencap(ByteArray(5)) }
        assertFailsWith<IOException> { Adb.parseRawScreencap(raw(3, 2, 3).copyOf(20)) }
    }

    @Test
    fun findsAPidWithOrWithoutPidof() {
        assertEquals(4242, Adb.parsePid("4242\n", "com.app"))
        assertEquals(4242, Adb.parsePid("4242 4300\n", "com.app"))
        // Android 5's ps, through grep: the main process, not its :remote one.
        val ps =
            "u0_a52    3001  1153  990000 40000 ffffffff b7751f15 S com.app:remote\n" +
                "u0_a52    2407  1153  1002952 62724 ffffffff b7751f15 S com.app\n"
        assertEquals(2407, Adb.parsePid(ps, "com.app"))
        assertNull(Adb.parsePid("", "com.app"))
        assertNull(Adb.parsePid("u0_a52 3001 1153 990000 40000 ffffffff b7751f15 S com.app:remote\n", "com.app"))
    }

    @Test
    fun readsHowAndroidTurnedItsScreen() {
        // Android 5's dumpsys input, cut down: the touchscreen's surface orientation.
        assertEquals(1, Adb.parseDisplayRotation("  Device 1: qwerty2\n    Touch Input Mapper:\n      SurfaceOrientation: 1\n"))
        // Without one, the default viewport's.
        assertEquals(3, Adb.parseDisplayRotation("  mDefaultViewport=DisplayViewport{valid=true, displayId=0, orientation=3, logicalFrame=Rect(0, 0 - 800, 480)}\n"))
        assertEquals(2, Adb.parseDisplayRotation("Viewport INTERNAL: displayId=0, uniqueId=local:0, port=0, orientation=2, logicalFrame=[0, 0, 1080, 2400]"))
        assertNull(Adb.parseDisplayRotation("INPUT MANAGER (dumpsys input)\n"))
    }
}
