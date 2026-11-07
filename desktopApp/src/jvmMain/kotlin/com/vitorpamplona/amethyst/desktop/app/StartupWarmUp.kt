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
package com.vitorpamplona.amethyst.desktop.app

import com.vitorpamplona.amethyst.desktop.platform.IconResources
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import kotlin.concurrent.thread

/**
 * Does the slow, side-effect-free first-time work of a start on a spare core while the main thread
 * builds the modules, so the steps that need it find it done.
 *
 * Measured on a cold start (no AppCDS), all one step after another on one core: the main thread
 * spent ~170 ms loading OkHttp's classes, the login ~250 ms loading the secp256k1 native library
 * and most of ~650 ms building the Account loading classes, and the first composition of the
 * logged-in window about half of its ~1.7 s loading classes. Nothing here changes what the app
 * does: a thread that reaches a class or step the warm-up is still on waits for it, as it would
 * for its own loading.
 */
object StartupWarmUp {
    private const val TAG = "StartupWarmUp"

    /** The classes a start loads from the app's jars, in the order it loads them. */
    const val CLASS_LIST = "/startup/preload-classes.txt"

    // Two loaders: the main and UI threads load classes as fast as one core can, so one helper
    // would only keep pace; two get ahead of them.
    private const val LOADERS = 2

    fun start() {
        // Off for recording a new class list (the order must be the app's own) and for A/B runs.
        if (System.getProperty("amethyst.startupWarmUp") == "false") return
        val names = readClassList()
        repeat(LOADERS) { index ->
            thread(isDaemon = true, name = "startup-warm-up-$index") {
                if (index == 0) {
                    // Native setup the login needs, ahead of everything else on this thread.
                    step("secp256k1") { Secp256k1Instance.isPrivateKeyValid(ByteArray(32) { 1 }) }
                }
                step("classes") { preload(names, index) }
                if (index == 0) step("icon") { IconResources.adaptedBufferedImage }
            }
        }
    }

    private fun readClassList(): List<String> =
        try {
            StartupWarmUp::class.java
                .getResourceAsStream(CLASS_LIST)
                ?.bufferedReader()
                ?.useLines { lines -> lines.filter { it.isNotBlank() && !it.startsWith("#") }.toList() }
                .orEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "Could not read $CLASS_LIST", e)
            emptyList()
        }

    private inline fun step(
        name: String,
        block: () -> Unit,
    ) {
        try {
            block()
        } catch (e: Throwable) {
            // A warm-up that fails only means the real step pays for it later.
            Log.w(TAG, "Warm-up step $name failed", e)
        }
    }

    /**
     * Loads, without initializing, every [LOADERS]-th class of [names] from [offset]: reads, inflates
     * and defines it, so the thread that first uses it finds it loaded. Initializing stays with the
     * code that uses them, so no static state runs early or on this thread. A name that no longer
     * exists (the list is older than the code) is skipped.
     */
    private fun preload(
        names: List<String>,
        offset: Int,
    ) {
        val loader = StartupWarmUp::class.java.classLoader
        var i = offset
        while (i < names.size) {
            try {
                Class.forName(names[i], false, loader)
            } catch (e: ClassNotFoundException) {
                // Renamed or removed since the list was made.
            } catch (e: LinkageError) {
                // Present but unloadable here (another platform's class): its user will say so.
            }
            i += LOADERS
        }
    }
}
