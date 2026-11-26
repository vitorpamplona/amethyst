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
package com.vitorpamplona.iossimulator

import com.vitorpamplona.devicecore.CommandResult
import com.vitorpamplona.devicecore.CommandRunner
import com.vitorpamplona.iossimulator.simctl.Simctl
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A stand-in for xcrun: records each command and answers what [answer] says. */
class FakeRunner(
    private val answer: (List<String>) -> CommandResult,
) : CommandRunner {
    val commands = ArrayList<List<String>>()

    override suspend fun run(
        command: List<String>,
        timeoutMs: Long,
        environment: Map<String, String>,
        directory: File?,
        log: File?,
    ): CommandResult {
        commands += command
        return answer(command)
    }

    override fun start(
        command: List<String>,
        environment: Map<String, String>,
        log: File?,
    ): Process = throw UnsupportedOperationException()

    companion object {
        fun ok(text: String = "") = CommandResult(0, text.toByteArray(), "")

        fun fail(stderr: String) = CommandResult(1, ByteArray(0), stderr)
    }
}

class SimctlTest {
    private val list =
        """
        {
          "runtimes": [
            {"identifier": "com.apple.CoreSimulator.SimRuntime.iOS-26-0", "name": "iOS 26.0", "version": "26.0", "isAvailable": true, "platform": "iOS"},
            {"identifier": "com.apple.CoreSimulator.SimRuntime.watchOS-26-0", "name": "watchOS 26.0", "version": "26.0", "isAvailable": true}
          ],
          "devices": {
            "com.apple.CoreSimulator.SimRuntime.iOS-26-0": [
              {"udid": "9F3B5D2A-1C4E-4F6A-8B7C-0D1E2F3A4B5C", "name": "iPhone 17 Pro", "state": "Booted", "isAvailable": true,
               "deviceTypeIdentifier": "com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro", "dataPath": "/Users/me/Library/Developer/CoreSimulator/Devices/9F3B/data"},
              {"udid": "11111111-2222-3333-4444-555555555555", "name": "iPad Air 13-inch (M3)", "state": "Shutdown", "isAvailable": true,
               "deviceTypeIdentifier": "com.apple.CoreSimulator.SimDeviceType.iPad-Air-13-inch-M3"}
            ],
            "com.apple.CoreSimulator.SimRuntime.iOS-17-5": [
              {"udid": "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE", "name": "iPhone 15", "state": "Shutdown", "isAvailable": false,
               "deviceTypeIdentifier": "com.apple.CoreSimulator.SimDeviceType.iPhone-15", "availabilityError": "runtime profile not found"}
            ],
            "com.apple.CoreSimulator.SimRuntime.watchOS-26-0": [
              {"udid": "FFFFFFFF-0000-1111-2222-333333333333", "name": "Apple Watch Series 11 (46mm)", "state": "Shutdown", "isAvailable": true,
               "deviceTypeIdentifier": "com.apple.CoreSimulator.SimDeviceType.Apple-Watch-Series-11-46mm"}
            ]
          },
          "pairs": {}
        }
        """.trimIndent()

    @Test
    fun listReadsDevicesWithTheirRuntimes() {
        val parsed = Simctl.parseList(list)
        assertEquals(4, parsed.devices.size)
        val phone = parsed.devices.first { it.name == "iPhone 17 Pro" }
        assertTrue(phone.isBooted)
        assertTrue(phone.isIos)
        assertFalse(phone.isIpad)
        assertEquals("iOS 26.0", phone.runtimeName)
        assertEquals("26.0", phone.osVersion)
        assertTrue(parsed.devices.first { it.name.startsWith("iPad") }.isIpad)
        // A runtime no longer installed is named from its identifier.
        val old = parsed.devices.first { it.name == "iPhone 15" }
        assertEquals("iOS 17.5", old.runtimeName)
        assertFalse(old.isAvailable)
        assertFalse(parsed.devices.first { it.name.startsWith("Apple Watch") }.isIos)
    }

    @Test
    fun runtimeLabelsComeFromTheIdentifier() {
        assertEquals("iOS 26.0", Simctl.runtimeLabel("com.apple.CoreSimulator.SimRuntime.iOS-26-0"))
        assertEquals("xrOS 2.5", Simctl.runtimeLabel("com.apple.CoreSimulator.SimRuntime.xrOS-2-5"))
    }

    @Test
    fun launchctlNamesTheRunningApps() {
        val out =
            """
            PID	Status	Label
            4821	0	UIKitApplication:com.example.notes[1a2b][rb-legacy]
            -	0	UIKitApplication:com.apple.mobilesafari[5c6d][rb-legacy]
            512	0	com.apple.backboardd
            """.trimIndent()
        assertEquals(mapOf("com.example.notes" to 4821), Simctl.runningApps(out))
    }

    @Test
    fun commandsGoThroughXcrunSimctl() =
        runBlocking {
            val runner =
                FakeRunner { cmd ->
                    when (cmd.getOrNull(2)) {
                        "clone" -> FakeRunner.ok("6E1A9C3B-0F2D-4E5A-9B8C-7D6E5F4A3B2C\n")
                        "launch" -> FakeRunner.ok("com.example.notes: 9134\n")
                        "shutdown" -> FakeRunner.fail("An error was encountered processing the command (domain=com.apple.CoreSimulator.SimError, code=405):\nUnable to shutdown device in current state: Shutdown")
                        "terminate" -> FakeRunner.fail("found nothing to terminate")
                        else -> FakeRunner.ok()
                    }
                }
            val simctl = Simctl(File("/usr/bin/xcrun"), runner)

            assertEquals("6E1A9C3B-0F2D-4E5A-9B8C-7D6E5F4A3B2C", simctl.clone("9F3B5D2A-1C4E-4F6A-8B7C-0D1E2F3A4B5C", "iPhone 17 Pro (Fix login)"))
            assertEquals(9134, simctl.launch("9F3B", "com.example.notes"))
            // Already shut down, nothing to terminate: what was asked holds.
            simctl.shutdown("9F3B")
            simctl.terminate("9F3B", "com.example.notes")
            simctl.bootAndWait("9F3B")

            assertEquals(listOf("/usr/bin/xcrun", "simctl", "clone", "9F3B5D2A-1C4E-4F6A-8B7C-0D1E2F3A4B5C", "iPhone 17 Pro (Fix login)"), runner.commands[0])
            assertEquals(listOf("/usr/bin/xcrun", "simctl", "launch", "--terminate-running-process", "9F3B", "com.example.notes"), runner.commands[1])
            assertEquals(listOf("/usr/bin/xcrun", "simctl", "bootstatus", "9F3B", "-b"), runner.commands.last())
        }

    @Test
    fun aFailureSaysWhatSimctlSaid() =
        runBlocking {
            val simctl = Simctl(File("/usr/bin/xcrun"), FakeRunner { FakeRunner.fail("Invalid device: 0000") })
            val e = assertFailsWith<IOException> { simctl.erase("0000") }
            assertEquals("simctl erase: Invalid device: 0000", e.message)
        }

    @Test
    fun anAppNotInstalledHasNoPath() =
        runBlocking {
            val simctl = Simctl(File("/usr/bin/xcrun"), FakeRunner { FakeRunner.fail("No such file or directory") })
            assertNull(simctl.appPath("9F3B", "com.example.none"))
        }

    @Test
    fun xcrunAloneIsNotXcode() {
        val dev = Files.createTempDirectory("xcode").toFile()
        try {
            val bin = File(dev, "bin").apply { mkdirs() }
            File(bin, "xcrun").apply { writeText("#!/bin/sh\n") }.setExecutable(true)
            // The command-line tools, or nothing: xcode-select names no folder with simctl.
            assertFalse(XcodeTools(env = emptyMap(), os = "Mac OS X", path = bin.path, xcodeSelect = { "/Library/Developer/CommandLineTools" }).available)
            assertFalse(XcodeTools(env = emptyMap(), os = "Mac OS X", path = bin.path, xcodeSelect = { null }).available)
            // A full Xcode: its developer folder has simctl.
            val xcode = File(dev, "Xcode.app/Contents/Developer")
            File(xcode, "usr/bin").mkdirs()
            File(xcode, "usr/bin/simctl").apply { writeText("#!/bin/sh\n") }.setExecutable(true)
            val tools = XcodeTools(env = emptyMap(), os = "Mac OS X", path = bin.path, xcodeSelect = { xcode.path })
            assertTrue(tools.available)
            assertEquals(xcode.path, tools.developerDir?.path)
            // Not a Mac: never, whatever is on the PATH.
            assertFalse(XcodeTools(env = emptyMap(), os = "Linux", path = bin.path, xcodeSelect = { xcode.path }).available)
        } finally {
            dev.deleteRecursively()
        }
    }
}
