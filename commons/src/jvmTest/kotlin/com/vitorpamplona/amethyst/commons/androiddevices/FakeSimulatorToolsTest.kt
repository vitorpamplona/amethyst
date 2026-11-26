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
package com.vitorpamplona.amethyst.commons.androiddevices

import com.vitorpamplona.androiddevice.ProcessCommandRunner
import com.vitorpamplona.androiddevice.emulator.PixelFormat
import com.vitorpamplona.iossimulator.XcodeTools
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The iOS path end to end against `tools/fake-ios-simulator`: a stand-in `xcrun simctl`, and a
 * stand-in idb companion that serves gRPC over HTTP/2 on its Unix socket and draws a small app.
 * It runs the app's own code from the device list down to the bytes on the socket: a
 * workstream's copy made with `simctl clone`, the companion started and asked to describe the
 * screen, the minicap video stream, touches and typing through the HID stream, the accessibility
 * tree read back as a UI tree, reset to a fresh copy, and the copy deleted when it stops.
 *
 * Skipped where the stand-in can't run: it needs `python3` with `h2` and `pillow`
 * (`pip install h2 pillow`).
 */
class FakeSimulatorToolsTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dir: File = Files.createTempDirectory("fake-ios").toFile()
    private val tools = File("../tools/fake-ios-simulator").absoluteFile

    @AfterTest
    fun stop() {
        scope.cancel()
        dir.deleteRecursively()
    }

    private fun standInRuns(): Boolean =
        try {
            val p = ProcessBuilder("python3", "-c", "import h2, PIL").redirectErrorStream(true).start()
            p.waitFor(20, TimeUnit.SECONDS) && p.exitValue() == 0 && File(tools, "idb_companion").canExecute()
        } catch (_: Exception) {
            false
        }

    private fun args(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    @Test
    fun aWorkstreamDrivesItsOwnCopyOfASimulator() {
        if (!standInRuns()) return
        // The stand-ins read their simulators from this file: one per test, so runs don't meet.
        val state = File(dir, "sims.json")
        val env = mapOf("AMETHYST_XCRUN" to File(tools, "xcrun").path, "AMETHYST_IDB_COMPANION" to File(tools, "idb_companion").path, "FAKE_IOS_STATE" to state.path)
        val xcode = XcodeTools(env = env, os = "Linux")
        val ios = IosSimulators(xcode, ProcessCommandRunner, scope, File(dir, "logs"), environment = mapOf("FAKE_IOS_STATE" to state.path))
        val devices = AndroidDevices(sdkHome = { null }, scope = scope, discoveryDirs = { emptyList() }, avdHome = { File(dir, "no-avds") }, ios = ios)
        val server = DeviceToolServer(devices, AndroidAppRunner(devices), owner = { "ws-1" }, ownerLabel = { "Fix login" }, folder = dir)

        runBlocking {
            withTimeout(120_000) {
                val list = server.call(DeviceToolServer.LIST, args("{}"))
                assertFalse(list.isError, list.text)
                assertTrue("iPhone 17 Pro (iOS 26.0, 9F3B5D2A-1C4E-4F6A-8B7C-0D1E2F3A4B5C)" in list.text, list.text)

                val started = server.call(DeviceToolServer.START_SIMULATOR, args("""{"simulator":"iPhone 17 Pro"}"""))
                assertFalse(started.isError, started.text)
                val copy = devices.selected("ws-1")!!
                assertTrue(copy.isIos)
                assertEquals("ws-1", copy.owner)
                assertEquals("iPhone 17 Pro", copy.name)
                assertNotEquals("9F3B5D2A-1C4E-4F6A-8B7C-0D1E2F3A4B5C", copy.serial)
                // As simctl lists it: named for the workstream, and as this app's copy.
                devices.refresh()
                assertEquals(
                    "iPhone 17 Pro – Fix login (Amethyst copy)",
                    devices.state.value.ios.simulators
                        .single { it.udid == copy.serial }
                        .name,
                )

                // The live view's stream: JPEG frames, scaled on the companion's side to the view.
                val frame =
                    devices
                        .connection(copy.serial)
                        .screen()
                        .frames(375, 667)
                        .first()
                assertEquals(PixelFormat.JPEG, frame.format)
                assertEquals(375, frame.width)
                assertEquals(667, frame.height)

                val tree = server.call(DeviceToolServer.UI_TREE, args("{}"))
                assertTrue("Button \"Tap me: 0\" id=tap.button" in tree.text, tree.text)

                assertFalse(server.call(DeviceToolServer.TAP, args("""{"text":"Tap me"}""")).isError)
                assertFalse(server.call(DeviceToolServer.TAP, args("""{"element":2}""")).isError)
                assertTrue("\"Tap me: 2\"" in server.call(DeviceToolServer.UI_TREE, args("{}")).text)

                assertFalse(server.call(DeviceToolServer.TYPE, args("""{"text":"gm ünicode","into":"Message"}""")).isError)
                assertTrue("desc=\"gm ünicode\"" in server.call(DeviceToolServer.UI_TREE, args("{}")).text)

                val shot = server.call(DeviceToolServer.SCREENSHOT, args("{}"))
                assertEquals(1, shot.images.size)
                assertTrue("750x1334" in shot.text, shot.text)

                val log = server.call(DeviceToolServer.LOG, args("""{"lines":20}"""))
                assertTrue("The relay didn't answer" in log.text, log.text)

                // Reset: a fresh copy in its place, this workstream's still, with nothing tapped.
                val reset = server.call(DeviceToolServer.RESET, args("""{"screenshot":false}"""))
                assertFalse(reset.isError, "reset said: " + reset.text)
                val fresh = devices.selected("ws-1")!!
                assertNotEquals(copy.serial, fresh.serial)
                assertTrue("\"Tap me: 0\"" in server.call(DeviceToolServer.UI_TREE, args("{}")).text)
                devices.refresh()
                assertTrue(
                    devices.state.value.ios.simulators
                        .none { it.udid == copy.serial },
                )

                // Stopped, the copy goes with its changes.
                assertFalse(server.call(DeviceToolServer.STOP_DEVICE, args("{}")).isError)
                devices.refresh()
                assertTrue(
                    devices.state.value.ios.simulators
                        .none { it.udid == fresh.serial },
                )
                assertNull(
                    devices.state.value.devices
                        .firstOrNull { it.serial == fresh.serial },
                )
            }
        }
        ios.shutdown()
    }
}
