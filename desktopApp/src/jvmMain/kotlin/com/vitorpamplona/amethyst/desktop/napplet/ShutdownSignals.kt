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
package com.vitorpamplona.amethyst.desktop.napplet

import com.vitorpamplona.quartz.utils.Log
import sun.misc.Signal
import kotlin.system.exitProcess

/**
 * Gives the termination signals back to the JVM once Chromium has started.
 *
 * JCEF runs Chromium's browser process inside the app's JVM, and Chromium installs its own handlers
 * for SIGTERM, SIGINT and SIGHUP when it starts. From then on the SIGTERM of a logout or a shutdown
 * ended the app from Chromium's thread with exit(0), and none of the JVM's shutdown hooks ran: not the
 * settings flushes, Tor's stop, the deep-link bus cleanup, or the JVM's own work at exit (the per-user
 * class archive). Handled by the JVM again, each signal is a normal exit with the JVM's default status
 * for it (128 + the signal's number), hooks included.
 */
object ShutdownSignals {
    private const val TAG = "ShutdownSignals"

    private val SIGNALS = listOf("TERM", "INT", "HUP")

    fun reclaim() {
        for (name in SIGNALS) {
            try {
                Signal.handle(Signal(name)) { signal -> exitProcess(128 + signal.number) }
            } catch (e: IllegalArgumentException) {
                // An OS without that signal (Windows has no HUP), or a JVM run with -Xrs.
                Log.d(TAG) { "SIG$name stays as it is: ${e.message}" }
            }
        }
    }
}
