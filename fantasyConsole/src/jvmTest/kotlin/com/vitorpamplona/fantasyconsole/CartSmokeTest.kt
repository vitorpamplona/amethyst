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
package com.vitorpamplona.fantasyconsole

import com.vitorpamplona.fantasyconsole.cart.CartLoader
import com.vitorpamplona.fantasyconsole.cart.P8scii
import com.vitorpamplona.fantasyconsole.runtime.FantasyConsole
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import javax.imageio.ImageIO
import kotlin.concurrent.thread
import kotlin.test.Test

/**
 * Runs every cart in `-DfantasyConsole.carts=<dir>` for a few seconds of game time, pressing
 * buttons now and then to get past title screens, and writes a scaled screenshot of each to
 * `-DfantasyConsole.out=<dir>` (default: the build directory). Prints one line per cart with its
 * state and error. Skipped when no directory is given, so the regular build never needs carts.
 *
 * ./gradlew :fantasyConsole:jvmTest --tests '*CartSmokeTest*' -DfantasyConsole.carts=/path/to/carts
 */
class CartSmokeTest {
    @Test
    fun runCarts() {
        val dir = System.getProperty("fantasyConsole.carts")?.let(::File) ?: return
        val out = File(System.getProperty("fantasyConsole.out") ?: "build/cart-screens").apply { mkdirs() }
        val frames = System.getProperty("fantasyConsole.frames")?.toInt() ?: 300
        val only = System.getProperty("fantasyConsole.only")
        val files =
            dir
                .listFiles { f -> f.name.endsWith(".p8.png") || f.name.endsWith(".p8") }!!
                .filter { only == null || it.name.startsWith(only) }
                .sortedBy { it.name }
        for (file in files) {
            val worker = Thread(null, { runCart(file, out, frames) }, "cart", FantasyConsole.RECOMMENDED_STACK_BYTES).apply { start() }
            worker.join()
        }
    }

    private fun runCart(
        file: File,
        out: File,
        frames: Int,
    ) {
        val line = StringBuilder(file.name.padEnd(36))
        try {
            val cart = CartLoader.load(file.readBytes())
            File(out, file.name.removeSuffix(".png").removeSuffix(".p8") + ".lua").writeText(P8scii.toUnicode(cart.code))
            val console = FantasyConsole(cart)
            val started = System.nanoTime()
            // A cart stuck inside one frame (an endless loop in _draw) is stopped after 10 s.
            val stepStarted = AtomicLong(0)
            val watchdog =
                thread(isDaemon = true) {
                    while (!Thread.currentThread().isInterrupted) {
                        val at = stepStarted.get()
                        if (at != 0L && System.nanoTime() - at > 10_000_000_000L) console.abort()
                        try {
                            Thread.sleep(200)
                        } catch (_: InterruptedException) {
                            break
                        }
                    }
                }
            for (frame in 0 until frames) {
                // Nothing in the first half second (no player is that fast), then taps and holds.
                val buttons =
                    when {
                        frame < 15 -> 0
                        frame % 30 in 0..1 -> 0x30
                        frame % 90 in 40..70 -> 0x02
                        else -> 0
                    }
                console.setButtons(0, buttons)
                stepStarted.set(System.nanoTime())
                console.step()
                stepStarted.set(0)
                if (console.state != FantasyConsole.State.RUNNING) break
            }
            watchdog.interrupt()
            val ms = (System.nanoTime() - started) / 1_000_000
            line.append("${console.state} ${console.frameCount}f ${ms}ms code=${cart.code.length}")
            console.error?.let { line.append("  !! $it") }
            save(console, File(out, file.name.removeSuffix(".png").removeSuffix(".p8") + ".png"))
        } catch (e: Exception) {
            line.append("LOAD FAILED: $e")
        }
        println(line)
    }

    private fun save(
        console: FantasyConsole,
        file: File,
    ) {
        val pixels = IntArray(128 * 128)
        console.render(pixels)
        val scale = 3
        val image = BufferedImage(128 * scale, 128 * scale, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until 128 * scale) for (x in 0 until 128 * scale) image.setRGB(x, y, pixels[(y / scale) * 128 + x / scale])
        ImageIO.write(image, "png", file)
    }
}
