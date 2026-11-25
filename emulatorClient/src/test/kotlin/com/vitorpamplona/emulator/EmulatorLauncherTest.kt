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

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class EmulatorLauncherTest {
    @Test
    fun headlessGrpcCommandLine() {
        assertEquals(
            listOf("/sdk/emulator/emulator", "-avd", "Pixel_8", "-no-window", "-grpc", "8600", "-grpc-use-token", "-no-boot-anim"),
            EmulatorLauncher.command("/sdk/emulator/emulator", "Pixel_8", EmulatorLaunchOptions(grpcPort = 8600)),
        )
        assertEquals(
            listOf("emulator", "-avd", "A", "-grpc", "1", "-no-audio", "-no-snapshot-save", "-read-only", "-gpu", "swiftshader_indirect", "-verbose"),
            EmulatorLauncher.command(
                "emulator",
                "A",
                EmulatorLaunchOptions(
                    grpcPort = 1,
                    headless = false,
                    useToken = false,
                    noBootAnimation = false,
                    noAudio = true,
                    noSnapshotSave = true,
                    readOnly = true,
                    gpu = "swiftshader_indirect",
                    extraArgs = listOf("-verbose"),
                ),
            ),
        )
    }

    @Test
    fun listAvdsOutputDropsLogLines() {
        val output = "INFO    | Storing crashdata in: /tmp/x\nPixel_8_API_35\n\nMedium_Phone_API_36.0\r\nWARNING | something odd\n"
        assertEquals(listOf("Pixel_8_API_35", "Medium_Phone_API_36.0"), EmulatorLauncher.parseListAvds(output))
    }

    @Test
    fun avdFilesAreListed() {
        val home = Files.createTempDirectory("avd-home")
        home.resolve("Pixel_8.ini").writeText("avd.ini.encoding=UTF-8\npath=/home/me/.android/avd/Pixel_8.avd\npath.rel=avd/Pixel_8.avd\n")
        home.resolve("Pixel_8.avd").createDirectories()
        home.resolve("a_tablet.ini").writeText("path.rel=avd/a_tablet.avd\n")
        val avds = AndroidSdk.listAvdFiles(home)
        assertEquals(listOf("a_tablet", "Pixel_8"), avds.map { it.name })
        assertNull(avds[0].avdDir)
        assertEquals(Paths.get("/home/me/.android/avd/Pixel_8.avd"), avds[1].avdDir)
        assertEquals(emptyList(), AndroidSdk.listAvdFiles(home.resolve("missing")))
    }

    @Test
    fun avdHomeOrder() {
        assertEquals(Paths.get("/a"), AndroidSdk.avdHome(mapOf("ANDROID_AVD_HOME" to "/a", "ANDROID_USER_HOME" to "/u"), "/h"))
        assertEquals(Paths.get("/u/avd"), AndroidSdk.avdHome(mapOf("ANDROID_USER_HOME" to "/u", "ANDROID_EMULATOR_HOME" to "/e"), "/h"))
        assertEquals(Paths.get("/e/avd"), AndroidSdk.avdHome(mapOf("ANDROID_EMULATOR_HOME" to "/e"), "/h"))
        assertEquals(Paths.get("/h/.android/avd"), AndroidSdk.avdHome(emptyMap(), "/h"))
    }

    @Test
    fun sdkCandidatesAndLocate() {
        assertEquals(
            listOf(Paths.get("/x"), Paths.get("/y"), Paths.get("/h/Android/Sdk")),
            AndroidSdk.candidates("Linux", mapOf("ANDROID_HOME" to "/x", "ANDROID_SDK_ROOT" to "/y"), "/h"),
        )
        assertEquals(listOf(Paths.get("/h/Library/Android/sdk")), AndroidSdk.candidates("Mac OS X", emptyMap(), "/h"))
        assertEquals(
            listOf(Paths.get("C:\\L", "Android", "Sdk")),
            AndroidSdk.candidates("Windows 10", mapOf("LOCALAPPDATA" to "C:\\L"), "C:\\h"),
        )

        val sdk = Files.createTempDirectory("sdk")
        val located = AndroidSdk.locate("Linux", mapOf("ANDROID_HOME" to sdk.resolve("missing").toString(), "ANDROID_SDK_ROOT" to sdk.toString()), "/nowhere")
        assertEquals(sdk, located?.root)
        assertNull(located?.emulatorBinary)
        sdk
            .resolve("emulator")
            .createDirectories()
            .resolve("emulator")
            .writeText("#!/bin/sh\n")
        assertEquals(sdk.resolve("emulator/emulator"), located?.emulatorBinary)
        assertNull(AndroidSdk.locate("Linux", emptyMap(), "/nowhere"))
    }

    @Test
    fun awaitRunningMatchesTheProcessOrItsAvd() =
        runBlocking {
            val dir = Files.createTempDirectory("avd-running")
            val me = ProcessHandle.current()
            val discovery = EmulatorDiscovery(listOf(dir))

            // Nothing yet: times out.
            assertNull(EmulatorLauncher.awaitRunning(me, "Pixel_8", discovery, timeout = 300.milliseconds, pollInterval = 50.milliseconds))

            // A file without a gRPC port is not ready.
            dir.resolve("pid_${me.pid()}.ini").writeText("avd.name=Other\n")
            assertNull(EmulatorLauncher.awaitRunning(me, null, discovery, timeout = 300.milliseconds, pollInterval = 50.milliseconds))

            dir.resolve("pid_${me.pid()}.ini").writeText("avd.name=Other\ngrpc.port=8554\ngrpc.token=t\n")
            val found = EmulatorLauncher.awaitRunning(me, "Pixel_8", discovery, timeout = 5.seconds, pollInterval = 50.milliseconds)
            assertEquals(me.pid(), found?.pid)
            assertEquals("t", found?.grpcToken)
        }

    @Test
    fun awaitRunningFallsBackToTheAvdName() {
        val unrelated = EmulatorDiscovery.parse(1, mapOf("avd.name" to "Pixel_8", "grpc.port" to "8554"), Paths.get("pid_1.ini"))
        val other = EmulatorDiscovery.parse(2, mapOf("avd.name" to "Tablet", "grpc.port" to "8556"), Paths.get("pid_2.ini"))
        val me = ProcessHandle.current()
        assertEquals(1L, EmulatorLauncher.match(me, "Pixel_8", listOf(other, unrelated))?.pid)
        assertNull(EmulatorLauncher.match(me, "Phone", listOf(other, unrelated)))
    }

    @Test
    fun freePortIsUsable() {
        val port = EmulatorLauncher.freeLocalPort()
        assertTrue(port in 1..65535)
    }
}
