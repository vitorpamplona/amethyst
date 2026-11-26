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

import com.vitorpamplona.androiddevice.emulator.EmulatorController
import com.vitorpamplona.androiddevice.emulator.EmulatorDiscovery
import com.vitorpamplona.androiddevice.emulator.EmulatorJwtAuth
import com.vitorpamplona.devicecore.grpc.GrpcChannel
import com.vitorpamplona.devicecore.screen.PixelFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Drives an emulator that runs now, whoever started it: `EMULATOR_IT=1 ./gradlew
 * :androidDeviceClient:test --tests '*RealEmulatorTest*'`. Skipped without `EMULATOR_IT`, so CI and
 * the pre-push hook never need one. Prints how long each call takes: the device panel's feel is
 * these numbers.
 */
class RealEmulatorTest {
    @Test
    fun drivesTheRunningEmulator() {
        if (System.getenv("EMULATOR_IT") == null) return
        val emulator = EmulatorDiscovery.running().firstOrNull { it.grpcReachable } ?: error("No emulator with a gRPC port is running")
        val jwt = if (emulator.grpcToken == null && emulator.usesJwt) EmulatorJwtAuth(Path.of(emulator.jwksDir!!), emulator.activeJwks?.let { Path.of(it) }) else null
        try {
            jwt?.register()
            val channel = GrpcChannel("127.0.0.1", emulator.grpcPort!!, emulator.grpcToken, authorization = jwt?.let { it::authorization })
            val controller = EmulatorController(channel)
            runBlocking {
                println("${emulator.serial} ${emulator.avdName} (${emulator.avdDisplayName}) jwt=${jwt != null}")
                println("status: ${controller.status()}")
                timed("status x10") { repeat(10) { controller.status() } }
                val shot = timed("screenshot RGBA 480x800") { controller.screenshot(PixelFormat.RGBA8888, 480, 800) }
                println("  ${shot.width}x${shot.height} rotation ${shot.rotation} bytes ${shot.pixels.size}")
                assertTrue(shot.pixels.size >= shot.width * shot.height * 4)
                timed("first streamed frame") { controller.screenshots(PixelFormat.RGBA8888, 480, 800).first() }
                // A touch in a corner of the status bar, where it does nothing.
                timed("touch down+up x10") {
                    repeat(10) {
                        controller.touch(470, 5, pressed = true)
                        controller.touch(470, 5, pressed = false)
                    }
                }
                timed("rotation") { controller.rotation() }
                // A drag across the middle, as the panel sends one: down, moves 30 ms apart, up,
                // with the screen streaming as the panel has it.
                val frames = AtomicInteger()
                val stream = launch(Dispatchers.IO) { controller.screenshots(PixelFormat.RGBA8888, 480, 800).collect { frames.incrementAndGet() } }
                delay(500)
                timed("drag while streaming") {
                    controller.touch(400, 400, pressed = true)
                    for (x in 360 downTo 80 step 40) {
                        delay(30)
                        controller.touch(x, 400, pressed = true)
                    }
                    controller.touch(80, 400, pressed = false)
                }
                delay(1000)
                stream.cancel()
                println("  frames during the drag: ${frames.get()}")
            }
        } finally {
            jwt?.close()
        }
    }

    private inline fun <T> timed(
        what: String,
        block: () -> T,
    ): T {
        val start = System.nanoTime()
        val result = block()
        println("$what: ${(System.nanoTime() - start) / 1_000_000} ms")
        return result
    }
}
