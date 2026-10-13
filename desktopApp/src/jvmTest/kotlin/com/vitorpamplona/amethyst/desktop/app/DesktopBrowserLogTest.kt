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

import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.LogSink
import org.junit.Assume.assumeTrue
import java.awt.GraphicsEnvironment
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A link that fails to open is logged by its scheme only: a nostrconnect:// code carries the
 * pairing secret, and the whole address used to go to the log, in the message and again inside
 * the exception's.
 */
class DesktopBrowserLogTest {
    private val original = Log.sink

    @AfterTest
    fun restore() {
        Log.sink = original
    }

    @Test
    fun aFailedOpenLogsNoPartOfTheAddress() {
        // Only where nothing can open it: elsewhere this would launch a real app.
        assumeTrue(GraphicsEnvironment.isHeadless())
        assumeTrue(
            System
                .getenv("PATH")
                .orEmpty()
                .split(File.pathSeparator)
                .none { File(it, "xdg-open").canExecute() },
        )

        val logged = mutableListOf<String>()
        Log.sink = LogSink { _, tag, message, throwable -> logged += "$tag $message ${throwable?.stackTraceToString().orEmpty()}" }

        val secret = "5ec2e7c0de5ec2e7c0de"
        val opened = DesktopBrowser.open("nostrconnect://${"ab".repeat(32)}?relay=wss%3A%2F%2Frelay.example.com&secret=$secret")

        assertFalse(opened)
        assertTrue(logged.isNotEmpty(), "the failure is still logged")
        assertTrue(logged.none { secret in it || "relay.example.com" in it }, logged.joinToString("\n"))
        assertTrue(logged.all { "nostrconnect" in it }, "the scheme still says what failed")
    }
}
