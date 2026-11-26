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

import com.vitorpamplona.androiddevice.emulator.LaunchOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The agent's device tools against an emulator that runs now, whoever started it:
 * `EMULATOR_IT=1 ./gradlew :commons:jvmTest --tests '*RealDeviceToolsTest*'` with `ANDROID_HOME`
 * set. Skipped without `EMULATOR_IT`. Leaves the device on Settings' main screen, in portrait.
 */
class RealDeviceToolsTest {
    @Test
    fun theToolsDriveARealEmulator() {
        if (System.getenv("EMULATOR_IT") == null) return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val devices = AndroidDevices(scope = scope)
        val server = DeviceToolServer(devices, AndroidAppRunner(devices), owner = { "it" }, folder = Files.createTempDirectory("it").toFile())

        fun call(
            tool: String,
            json: String,
        ) = runBlocking {
            val started = System.nanoTime()
            val result = server.call(tool, Json.parseToJsonElement(json).jsonObject)
            println("$tool $json (${(System.nanoTime() - started) / 1_000_000} ms, ${result.images.size} image(s)):\n${result.text.prependIndent("  ")}")
            assertFalse(result.isError, result.text)
            result
        }
        try {
            val serial =
                runBlocking { devices.refresh() }.let {
                    devices.state.value.devices
                        .first { it.kind == DeviceKind.EMULATOR && it.status == DeviceStatus.ONLINE }
                        .serial
                }
            call(DeviceToolServer.LIST, "{}")
            call(DeviceToolServer.SELECT, """{"device":"$serial"}""")
            call(DeviceToolServer.LAUNCH_APP, """{"package":"com.android.settings"}""")
            assertTrue(call(DeviceToolServer.SCREENSHOT, "{}").images.size == 1)
            val tree = call(DeviceToolServer.UI_TREE, "{}")
            assertTrue(tree.text.contains("Display"), tree.text)
            call(DeviceToolServer.TAP, """{"text":"Display","screenshot":true}""")
            call(DeviceToolServer.WAIT_FOR, """{"text":"Brightness level"}""")
            call(DeviceToolServer.KEY, """{"key":"back"}""")
            call(DeviceToolServer.SWIPE, """{"direction":"up"}""")
            call(DeviceToolServer.ROTATE, """{"orientation":"landscape","screenshot":true}""")
            call(DeviceToolServer.ROTATE, """{"orientation":"portrait"}""")
            call(DeviceToolServer.LOG, """{"lines":5}""")
        } finally {
            scope.cancel()
        }
    }

    /**
     * Two workstreams' copies of one AVD at once (`-read-only`), and a reset of one: the AVD named
     * by `EMULATOR_IT_COPIES`, which must not be running writable. Stops both copies at the end.
     */
    @Test
    fun twoReadOnlyCopiesRunSideBySide() {
        val avd = System.getenv("EMULATOR_IT_COPIES") ?: return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val devices = AndroidDevices(scope = scope)
        try {
            runBlocking {
                val copies =
                    listOf("a", "b").map { owner ->
                        async { devices.start(avd, LaunchOptions(readOnly = true), owner = owner, ownerLabel = "Workstream $owner") }
                    }
                val serials = copies.awaitAll()
                println("copies: $serials")
                assertEquals(2, serials.toSet().size)
                devices.refresh()
                serials.forEach { serial ->
                    val info =
                        devices.state.value.devices
                            .first { it.serial == serial }
                    println("  $serial owner=${info.owner} live=${info.liveStream} status=${info.status}")
                    assertTrue(info.liveStream)
                }
                // A change on one copy, then its reset: back to the saved state, the other untouched.
                val first = serials[0]
                devices.androidConnection(first).adb.shell(first, "settings", "put", "system", "screen_off_timeout", "123456")
                devices.reset(first)
                val after =
                    devices
                        .androidConnection(first)
                        .adb
                        .shell(first, "settings", "get", "system", "screen_off_timeout")
                        .trim()
                println("  after reset: screen_off_timeout=$after")
                assertTrue(after != "123456", after)
                serials.forEach { devices.stop(it) }
            }
        } finally {
            scope.cancel()
        }
    }
}
