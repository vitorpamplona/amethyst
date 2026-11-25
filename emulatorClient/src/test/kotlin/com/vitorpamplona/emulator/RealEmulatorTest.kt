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

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Against a real emulator, when one runs and `EMULATOR_IT=1` is set; otherwise every case returns
 * at once. Start one the way the devices panel does, then run:
 *
 * ```
 * emulator -avd <name> -no-window -grpc 8554 -grpc-use-token -no-boot-anim
 * EMULATOR_IT=1 ./gradlew :emulatorClient:test --tests '*RealEmulatorTest*'
 * ```
 *
 * It prints what it saw, which is how the claims in this module's README were checked.
 */
class RealEmulatorTest {
    private val enabled = System.getenv("EMULATOR_IT") == "1"

    private fun emulator(): RunningEmulator? = if (!enabled) null else EmulatorDiscovery().running().firstOrNull { it.grpcPort != null && !it.usesJwt }

    private fun controller(emulator: RunningEmulator) = EmulatorController(emulator, OkHttpClient())

    @Test
    fun discoveryFindsItWithItsPortAndToken() {
        val emulator = emulator() ?: return
        println("found $emulator")
        assertNotNull(emulator.grpcPort)
        assertNotNull(emulator.grpcToken)
        assertNotNull(emulator.avdId)
    }

    @Test
    fun statusNamesTheDisplay() =
        runBlocking {
            val emulator = emulator() ?: return@runBlocking
            val status = controller(emulator).getStatus()
            println("status: version=${status.version} booted=${status.booted} display=${status.displayWidth}x${status.displayHeight}")
            assertTrue(status.version.isNotBlank())
            assertNotNull(status.displayWidth)
            assertNotNull(status.displayHeight)
        }

    @Test
    fun aPngScreenshotDecodesAtTheAskedSize() =
        runBlocking {
            val emulator = emulator() ?: return@runBlocking
            val frame = controller(emulator).getScreenshot(ImageEncoding.PNG, width = 240, height = 400)
            val image = assertNotNull(ImageIO.read(ByteArrayInputStream(frame.pixels)))
            println("png: frame ${frame.width}x${frame.height}, image ${image.width}x${image.height}, rotation ${frame.rotation}")
            assertEquals(frame.width, image.width)
            assertEquals(frame.height, image.height)
            assertTrue(image.width <= 240 && image.height <= 400)
        }

    /** Which way the raw RGBA rows run: compared, pixel by pixel, with the PNG of the same screen. */
    @Test
    fun rawRowsAgainstThePng() =
        runBlocking {
            val emulator = emulator() ?: return@runBlocking
            val controller = controller(emulator)
            val png = ImageIO.read(ByteArrayInputStream(controller.getScreenshot(ImageEncoding.PNG).pixels))
            val raw = controller.getScreenshot(ImageEncoding.RGBA8888)

            fun rawAt(
                x: Int,
                y: Int,
            ): Int {
                val i = (y * raw.width + x) * 4
                val p = raw.pixels
                return ((p[i].toInt() and 0xff) shl 16) or ((p[i + 1].toInt() and 0xff) shl 8) or (p[i + 2].toInt() and 0xff)
            }
            var topDown = 0
            var bottomUp = 0
            for (y in 0 until raw.height step 7) {
                for (x in 0 until raw.width step 11) {
                    val expected = png.getRGB(x, y) and 0xffffff
                    if (rawAt(x, y) == expected) topDown++
                    if (rawAt(x, raw.height - 1 - y) == expected) bottomUp++
                }
            }
            println("raw rows: top-down matches=$topDown, bottom-up matches=$bottomUp")
        }

    @Test
    fun theStreamSendsFrames() =
        runBlocking {
            val emulator = emulator() ?: return@runBlocking
            val controller = controller(emulator)
            val frames =
                withTimeout(60_000) {
                    controller.screenshots(ImageEncoding.PNG, width = 240, height = 400).take(1).toList()
                }
            println("stream: ${frames.map { it.toString() }}")
            assertEquals(1, frames.size)
        }

    @Test
    fun inputAndRotationAreTaken() =
        runBlocking {
            val emulator = emulator() ?: return@runBlocking
            val controller = controller(emulator)
            val status = controller.getStatus()
            val w = status.displayWidth ?: 480
            val h = status.displayHeight ?: 800
            controller.sendMouse(w / 2, h / 2, 1)
            controller.sendMouse(w / 2, h / 2, 0)
            controller.pressKey(KeyboardEvent.GO_HOME)
            controller.pressKey("AudioVolumeUp")
            controller.sendWheel(0, -1)
            for (rotation in listOf(SkinRotation.LANDSCAPE, SkinRotation.REVERSE_PORTRAIT, SkinRotation.REVERSE_LANDSCAPE, SkinRotation.PORTRAIT)) {
                controller.rotateTo(rotation)
                kotlinx.coroutines.delay(1_500)
                val shot = controller.getScreenshot(ImageEncoding.PNG)
                println("asked $rotation: sensor ${controller.getRotation()}, screenshot ${shot.width}x${shot.height} ${shot.rotation}")
            }
        }

    /** An emulator started with `-grpc-use-jwt`, as Android Studio starts them. */
    @Test
    fun aJwtEmulatorTakesOurSignedTokens() =
        runBlocking {
            val emulator = if (enabled) EmulatorDiscovery().running().firstOrNull { it.usesJwt } else null
            emulator ?: return@runBlocking
            EmulatorController(emulator, OkHttpClient()).use { controller ->
                val status = controller.getStatus()
                println("jwt status: version=${status.version} display=${status.displayWidth}x${status.displayHeight}")
                val frame = controller.getScreenshot(ImageEncoding.PNG, width = 120, height = 200)
                println("jwt png: $frame")
                controller.sendMouse(10, 10, 0)
                controller.pressKey(KeyboardEvent.GO_HOME)
                val wheel = runCatching { controller.sendWheel(0, -1) }.exceptionOrNull()
                println("jwt wheel: ${wheel?.message ?: "taken"}")
            }
        }

    /**
     * Starts `EMULATOR_IT_LAUNCH` (an AVD id) as the devices panel does, plus `EMULATOR_IT_ARGS`
     * (for instance `-accel off` on a machine without virtualization), and waits for discovery to
     * find it. `EMULATOR_IT_KEEP=1` leaves it running.
     */
    @Test
    fun theLauncherStartsAnAvdAndFindsIt() =
        runBlocking {
            val avd = System.getenv("EMULATOR_IT_LAUNCH")?.ifBlank { null } ?: return@runBlocking
            if (!enabled) return@runBlocking
            val sdk = assertNotNull(AndroidSdk.locate())
            val launcher = EmulatorLauncher(sdk)
            println("avds: ${launcher.listAvds()}")
            val log = File.createTempFile("emulator-it", ".log")
            val extra =
                System
                    .getenv("EMULATOR_IT_ARGS")
                    ?.split(' ')
                    ?.filter { it.isNotBlank() }
                    .orEmpty()
            val process = launcher.start(avd, EmulatorLaunchOptions(grpcPort = EmulatorLauncher.freeLocalPort(), extraArgs = extra, logFile = log))
            val running = EmulatorLauncher.awaitRunning(process.toHandle(), avd, timeout = 5.minutes)
            println("launched: $running (launcher pid ${process.pid()})")
            if (running == null) println(log.readText().takeLast(2_000))
            val found = assertNotNull(running)
            assertEquals(avd, found.avdId)
            val status = EmulatorController(found, OkHttpClient()).getStatus()
            println("launched status: ${status.version}")
            if (System.getenv("EMULATOR_IT_KEEP") != "1") {
                process
                    .toHandle()
                    .descendants()
                    .forEach { it.destroy() }
                    .also { process.destroy() }
            }
        }
}
