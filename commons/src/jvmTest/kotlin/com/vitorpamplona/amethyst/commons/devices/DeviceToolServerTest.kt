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
package com.vitorpamplona.amethyst.commons.devices

import com.vitorpamplona.amethyst.commons.devices.android.AndroidAppRunner
import com.vitorpamplona.amethyst.commons.devices.android.AndroidBackend
import com.vitorpamplona.amethyst.commons.devices.android.AndroidConnection
import com.vitorpamplona.androiddevice.emulator.EmulatorController
import com.vitorpamplona.devicecore.CommandResult
import com.vitorpamplona.devicecore.CommandRunner
import com.vitorpamplona.devicecore.grpc.GrpcChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The agent's device tools against a stand-in `adb`: one emulator, one phone the user hasn't allowed. */
class DeviceToolServerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @AfterTest
    fun stop() = scope.cancel()

    private val uiXml =
        """
        <?xml version='1.0' encoding='UTF-8' standalone='yes' ?><hierarchy rotation="0">
        <node index="0" text="" resource-id="" class="android.widget.FrameLayout" package="p" content-desc="" checkable="false" checked="false" clickable="false" enabled="true" focusable="false" focused="false" scrollable="false" long-clickable="false" password="false" selected="false" bounds="[0,0][1080,2400]">
        <node index="0" text="Post" resource-id="" class="android.widget.Button" package="p" content-desc="" checkable="false" checked="false" clickable="true" enabled="true" focusable="true" focused="false" scrollable="false" long-clickable="false" password="false" selected="false" bounds="[100,200][300,400]" />
        </node></hierarchy>
        """.trimIndent()

    private val png: ByteArray =
        ByteArrayOutputStream().also { ImageIO.write(BufferedImage(1080, 2400, BufferedImage.TYPE_INT_RGB), "png", it) }.toByteArray()

    private inner class FakeAdb : CommandRunner {
        val commands = mutableListOf<String>()

        /** What the crash and system buffers hold now. */
        @Volatile
        var crashLog = ""

        /** What `pidof` answers: empty when the app isn't running. */
        @Volatile
        var pid = "4242"

        override suspend fun run(
            command: List<String>,
            timeoutMs: Long,
            environment: Map<String, String>,
            directory: File?,
            log: File?,
        ): CommandResult {
            val line = command.drop(1).joinToString(" ")
            commands += line

            fun ok(text: String) = CommandResult(0, text.toByteArray(), "")
            return when {
                line == "devices -l" -> ok("List of devices attached\nemulator-5554 device product:sdk model:sdk_phone\nR58M device product:x model:Pixel_8\n")
                line.endsWith("getprop sys.boot_completed") -> ok("1\n")
                line.endsWith("wm size") -> ok("Physical size: 1080x2400\n")
                line.endsWith("exec-out uiautomator dump /dev/tty") -> ok(uiXml + "UI hierchary dumped to: /dev/tty")
                line.endsWith("exec-out screencap -p") -> CommandResult(0, png, "")
                line.contains("logcat -d -v threadtime -b crash") -> ok(crashLog)
                line.contains("pidof") -> CommandResult(if (pid.isEmpty()) 1 else 0, pid.toByteArray(), "")
                else -> ok("")
            }
        }

        override fun start(
            command: List<String>,
            environment: Map<String, String>,
            log: File?,
        ): Process = throw UnsupportedOperationException()
    }

    private val adb = FakeAdb()
    private val sdk: File =
        Files.createTempDirectory("sdk").toFile().also { dir ->
            FakeSdk.tool(dir, "platform-tools/adb")
            dir.deleteOnExit()
        }
    private val android =
        AndroidBackend(
            sdkHome = { sdk },
            scope = scope,
            runner = adb,
            discoveryDirs = { emptyList() },
            avdHome = { File(sdk, "no-avds") },
        )
    private val devices = Devices(listOf(android), scope)
    private val server = DeviceToolServer(devices, listOf(AndroidAppRunner(android, devices::report, adb)), owner = { "chat-1" }, folder = sdk)

    private fun args(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    @Test
    fun listsDevicesAndWhatTheAgentMayUse() =
        runBlocking {
            val result = server.call(DeviceToolServer.LIST, args("{}"))
            assertFalse(result.isError, result.text)
            assertTrue(result.text.contains("- emulator-5554 (Android emulator, online)"), result.text)
            assertTrue(result.text.contains("R58M: Pixel 8 (Android phone, online, the user hasn't allowed agents on it)"), result.text)
        }

    @Test
    fun uiTreeBoundsAreInScreenshotPixels() =
        runBlocking {
            val result = server.call(DeviceToolServer.UI_TREE, args("""{"device":"emulator-5554"}"""))
            assertFalse(result.isError, result.text)
            // 1080x2400 is shown at 1568/2400 so the model sees it unresized: 100 → 65, 300 → 196.
            assertTrue(result.text.contains("#1 Button \"Post\" [65,131][196,261] clickable"), result.text)
        }

    @Test
    fun tapsAnElementOrAPointInTheDevicesOwnPixels() =
        runBlocking {
            server.call(DeviceToolServer.UI_TREE, args("""{"device":"emulator-5554"}"""))
            val byElement = server.call(DeviceToolServer.TAP, args("""{"device":"emulator-5554","element":1}"""))
            assertFalse(byElement.isError, byElement.text)
            assertTrue(adb.commands.contains("-s emulator-5554 shell input tap 200 300"), adb.commands.toString())

            val byText = server.call(DeviceToolServer.TAP, args("""{"device":"emulator-5554","text":"post"}"""))
            assertEquals("Tapped #1 Button \"Post\"", byText.text)

            // 653 / (1568 / 2400) is 999.5: the device pixel it falls in.
            server.call(DeviceToolServer.TAP, args("""{"device":"emulator-5554","x":653,"y":784}"""))
            assertTrue(adb.commands.contains("-s emulator-5554 shell input tap 999 1200"), adb.commands.toString())
        }

    @Test
    fun aScreenshotIsShrunkToWhatTheModelSeesWhole() =
        runBlocking {
            val result = server.call(DeviceToolServer.SCREENSHOT, args("""{"device":"emulator-5554"}"""))
            assertFalse(result.isError, result.text)
            val image = ImageIO.read(Base64.getDecoder().decode(result.images.single().base64).inputStream())
            assertEquals(1568, image.height)
            assertEquals(706, image.width)
            assertTrue(result.text.contains("706x1568"), result.text)
        }

    @Test
    fun aPhoneWaitsForTheUsersLeave() =
        runBlocking {
            val refused = server.call(DeviceToolServer.SCREENSHOT, args("""{"device":"R58M"}"""))
            assertTrue(refused.isError)
            assertTrue(refused.text.contains("allow agents"), refused.text)

            devices.allowAgent("R58M", true)
            val allowed = server.call(DeviceToolServer.KEY, args("""{"device":"R58M","key":"back"}"""))
            assertFalse(allowed.isError, allowed.text)
            assertTrue(adb.commands.contains("-s R58M shell input keyevent 4"), adb.commands.toString())
        }

    @Test
    fun withoutADeviceNamedAndTwoOnlineItAsksWhich() =
        runBlocking {
            val result = server.call(DeviceToolServer.SCREENSHOT, args("{}"))
            assertTrue(result.isError)
            assertTrue(result.text.contains("Several devices"), result.text)
            // Once a tool names one, it is the chat's.
            server.call(DeviceToolServer.SELECT, args("""{"device":"emulator-5554"}"""))
            assertEquals("emulator-5554", devices.selected("chat-1")?.serial)
            assertFalse(server.call(DeviceToolServer.UI_TREE, args("{}")).isError)
        }

    private fun crash(
        time: String,
        message: String,
    ) = """
        11-25 $time  4242  4242 E AndroidRuntime: FATAL EXCEPTION: main
        11-25 $time  4242  4242 E AndroidRuntime: Process: com.example.app, PID: 4242
        11-25 $time  4242  4242 E AndroidRuntime: java.lang.IllegalStateException: $message
        11-25 $time  4242  4242 E AndroidRuntime: 	at com.example.app.Feed.load(Feed.kt:12)
        """.trimIndent() + "\n"

    @Test
    fun aCrashAfterAnActionLeadsItsResult() =
        runBlocking {
            // A crash from before the agent looked is old news.
            adb.crashLog = crash("05:00:00.000", "old")
            val first = server.call(DeviceToolServer.TAP, args("""{"device":"emulator-5554","x":10,"y":10}"""))
            assertFalse(first.text.contains("CRASHED"), first.text)

            adb.crashLog += crash("05:01:00.000", "boom")
            val second = server.call(DeviceToolServer.TAP, args("""{"device":"emulator-5554","x":10,"y":10}"""))
            assertTrue(second.text.startsWith("SINCE YOUR LAST DEVICE ACTION, AN APP CRASHED:"), second.text)
            assertTrue(second.text.contains("com.example.app crashed at 11-25 05:01:00.000: java.lang.IllegalStateException: boom"), second.text)
            assertTrue(second.text.contains("at com.example.app.Feed.load(Feed.kt:12)"), second.text)

            // Told once.
            val third = server.call(DeviceToolServer.TAP, args("""{"device":"emulator-5554","x":10,"y":10}"""))
            assertFalse(third.text.contains("CRASHED"), third.text)
            // The panel keeps its own place: it still sees the crash the agent was told of.
            assertEquals(listOf("java.lang.IllegalStateException: boom"), devices.crashesSince("emulator-5554", 0).map { it.crash.headline })
        }

    @Test
    fun anAppThatDiesWithoutACrashIsNoticed() =
        runBlocking {
            server.call(DeviceToolServer.LAUNCH_APP, args("""{"device":"emulator-5554","package":"com.example.app","screenshot":false}"""))
            adb.pid = ""
            val result = server.call(DeviceToolServer.KEY, args("""{"device":"emulator-5554","key":"back"}"""))
            assertTrue(result.text.startsWith("com.example.app IS NO LONGER RUNNING"), result.text)
            // Said once, and not after stopping it on purpose.
            assertFalse(server.call(DeviceToolServer.KEY, args("""{"device":"emulator-5554","key":"back"}""")).text.contains("NO LONGER"))
        }

    @Test
    fun resetOnlyTouchesTheWorkstreamsOwnCopy() =
        runBlocking {
            // emulator-5554 is shared (nobody's copy): the user's state may live there.
            val result = server.call(DeviceToolServer.RESET, args("""{"device":"emulator-5554"}"""))
            assertTrue(result.isError)
            assertTrue(result.text.contains("only resets this workstream's own copy"), result.text)
        }

    @Test
    fun anEmulatorWhoseControlPortIsGoneIsTouchedThroughAdb() =
        runBlocking {
            // Nothing listens on this port: the gRPC touch fails and adb takes over.
            val dead = EmulatorController(GrpcChannel("127.0.0.1", 1, null, callTimeoutMs = 2_000))
            val connection = AndroidConnection("emulator-5554", android.androidConnection("emulator-5554").adb, dead)
            connection.tap(100, 200)
            assertTrue(adb.commands.contains("-s emulator-5554 shell input tap 100 200"), adb.commands.toString())
        }
}
