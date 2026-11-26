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

import com.vitorpamplona.androiddevice.CommandResult
import com.vitorpamplona.androiddevice.CommandRunner
import com.vitorpamplona.androiddevice.emulator.LaunchOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Each workstream's own emulator copy, against a stand-in SDK: `emulator` starts a placeholder
 * process and writes the discovery file a real one would; `adb` lists what runs.
 */
class DeviceCopiesTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val root: File = Files.createTempDirectory("copies").toFile()
    private val sdk = File(root, "sdk")
    private val discovery = File(root, "running").apply { mkdirs() }
    private val avdHome = File(root, "avd")

    /** serial → (process, read-only, avd). */
    private val running = ConcurrentHashMap<String, Triple<Process, Boolean, String>>()
    private val launches = mutableListOf<List<String>>()
    private var nextPort = 5554

    @AfterTest
    fun stop() {
        running.values.forEach { it.first.destroyForcibly() }
        scope.cancel()
        root.deleteRecursively()
    }

    private val runner =
        object : CommandRunner {
            override suspend fun run(
                command: List<String>,
                timeoutMs: Long,
                environment: Map<String, String>,
                directory: File?,
                log: File?,
            ): CommandResult {
                val line = command.drop(1).joinToString(" ")

                fun ok(text: String = "") = CommandResult(0, text.toByteArray(), "")
                return when {
                    line == "devices -l" -> ok("List of devices attached\n" + running.keys.joinToString("") { "$it device model:sdk\n" })
                    line.endsWith("getprop sys.boot_completed") -> ok("1")
                    line.endsWith("emu kill") -> {
                        val serial = command[2]
                        running.remove(serial)?.first?.destroyForcibly()
                        ok()
                    }
                    else -> ok()
                }
            }

            override fun start(
                command: List<String>,
                environment: Map<String, String>,
                log: File?,
            ): Process {
                launches += command
                val avd = command[command.indexOf("-avd") + 1]
                val readOnly = "-read-only" in command
                // The emulator's own rule: one AVD runs several times only if every instance is read-only.
                val clash = running.values.any { it.third == avd && (!it.second || !readOnly) }
                if (clash) {
                    log?.writeText("ERROR   | Another emulator instance is running. Please close it or run all emulators with -read-only flag.\n")
                    return FakeSdk.failed()
                }
                val process = FakeSdk.longLived()
                val port = nextPort.also { nextPort += 2 }
                File(discovery, "pid_${process.pid()}.ini").writeText("port.serial=$port\navd.name=$avd\ngrpc.port=${port + 3000}\ngrpc.token=t\n")
                running["emulator-$port"] = Triple(process, readOnly, avd)
                return process
            }
        }

    private val devices: AndroidDevices

    init {
        listOf("platform-tools/adb", "emulator/emulator").forEach { FakeSdk.tool(sdk, it) }
        val avdDir = File(avdHome, "Pixel.avd").apply { mkdirs() }
        File(avdHome, "Pixel.ini").writeText("path=${avdDir.absolutePath}\n")
        File(avdDir, "config.ini").writeText("avd.ini.displayname=Pixel\n")
        devices = AndroidDevices(sdkHome = { sdk }, scope = scope, runner = runner, discoveryDirs = { listOf(discovery) }, avdHome = { avdHome }, logDir = File(root, "logs"))
    }

    @Test
    fun eachWorkstreamGetsItsOwnReadOnlyCopy() =
        runBlocking {
            val a = devices.start("Pixel", owner = "ws-a", ownerLabel = "Feed fix")
            val b = devices.start("Pixel", owner = "ws-b", ownerLabel = "Zap flow")
            assertNotEquals(a, b)
            assertTrue(launches.all { "-read-only" in it }, launches.toString())
            // A workstream that starts again gets the copy it has.
            assertEquals(a, devices.start("Pixel", owner = "ws-a"))
            assertEquals(2, launches.size)

            assertEquals(a, devices.selected("ws-a")?.serial)
            assertEquals(b, devices.selected("ws-b")?.serial)
            assertEquals("Zap flow", devices.device(b)?.ownerLabel)
            // Another chat doesn't fall into someone else's copy.
            assertNull(devices.selected("ws-c"))

            devices.releaseOwner("ws-a")
            waitUntil { !running.containsKey(a) }
            devices.refresh()
            assertEquals(
                listOf(b),
                devices.state.value.devices
                    .map { it.serial },
            )
        }

    @Test
    fun theAvdItselfCantRunBesideCopiesAndSaysWhy() =
        runBlocking {
            devices.start("Pixel", owner = "ws-a")
            val e = assertFailsWith<IOException> { devices.start("Pixel") }
            assertTrue(e.message!!.contains("Workstreams are running their own copies of Pixel"), e.message)
            assertTrue(
                devices.state.value.startFailures
                    .containsKey("Pixel"),
            )
            Unit
        }

    private suspend fun waitUntil(check: () -> Boolean) {
        val until = System.currentTimeMillis() + 5_000
        while (!check() && System.currentTimeMillis() < until) delay(50)
        assertTrue(check())
    }

    @Test
    fun aSerialReusedByAnotherEmulatorDoesntKeepTheOldOwner() =
        runBlocking {
            val copy = devices.start("Pixel", owner = "ws-a", ownerLabel = "Feed fix")
            assertEquals("ws-a", devices.device(copy)?.owner)
            // The copy dies on its own (closed from its window, crashed), and its port comes back.
            running
                .remove(copy)
                ?.first
                ?.destroyForcibly()
                ?.waitFor()
            devices.refresh()
            nextPort = copy.removePrefix("emulator-").toInt()
            val next = devices.start("Pixel")
            assertEquals(copy, next)
            assertNull(devices.device(next)?.owner)
        }

    @Test
    fun aColdBootStopsTheOldInstanceBeforeStartingAgain() =
        runBlocking {
            val copy = devices.start("Pixel", owner = "ws-a")
            var booted: String? = null
            devices.restartInBackground(copy, LaunchOptions(coldBoot = true)) { booted = it }
            waitUntil { booted != null }
            assertNotEquals(copy, booted)
            assertTrue("-no-snapshot-load" in launches.last(), launches.last().toString())
            assertEquals("ws-a", devices.device(booted!!)?.owner)
            assertTrue(!running.containsKey(copy))
        }
}
