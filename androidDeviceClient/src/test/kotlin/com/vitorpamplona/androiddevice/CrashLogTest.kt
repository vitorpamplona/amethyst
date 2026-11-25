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

import com.vitorpamplona.androiddevice.adb.CrashLog
import com.vitorpamplona.androiddevice.adb.DeviceCrash
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CrashLogTest {
    private val log =
        """
        --------- beginning of crash
        11-25 05:10:03.400  4242  4242 E AndroidRuntime: FATAL EXCEPTION: main
        11-25 05:10:03.400  4242  4242 E AndroidRuntime: Process: com.vitorpamplona.amethyst.debug, PID: 4242
        11-25 05:10:03.400  4242  4242 E AndroidRuntime: java.lang.IllegalStateException: boom
        11-25 05:10:03.400  4242  4242 E AndroidRuntime: 	at com.vitorpamplona.amethyst.Feed.load(Feed.kt:12)
        11-25 05:10:03.400  4242  4242 E AndroidRuntime: 	at android.os.Handler.dispatchMessage(Handler.java:99)
        11-25 05:10:03.400  4242  4242 E AndroidRuntime: Caused by: java.io.IOException: closed
        11-25 05:12:00.001  5555  5580 F libc    : Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0 in tid 5580 (Tor), pid 5555 (vitorpamplona.a)
        11-25 05:12:00.200  5600  5600 F DEBUG   : *** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***
        11-25 05:12:00.200  5600  5600 F DEBUG   : Cmdline: com.vitorpamplona.amethyst.debug
        11-25 05:12:00.201  5600  5600 F DEBUG   : backtrace:
        11-25 05:12:00.201  5600  5600 F DEBUG   :       #00 pc 0000000000012345  /data/app/lib/x86_64/libarti.so
        --------- beginning of system
        11-25 05:15:10.000   600   650 E ActivityManager: ANR in com.vitorpamplona.amethyst.debug (com.vitorpamplona.amethyst.debug/.ui.MainActivity)
        11-25 05:15:10.000   600   650 E ActivityManager: PID: 4300
        11-25 05:15:10.000   600   650 E ActivityManager: Reason: Input dispatching timed out
        11-25 05:15:10.000   600   650 E ActivityManager: Load: 1.2 / 0.8 / 0.5
        """.trimIndent()

    @Test
    fun findsJavaNativeAndAnr() {
        val crashes = CrashLog.parse(log)
        assertEquals(listOf(DeviceCrash.Kind.JAVA, DeviceCrash.Kind.NATIVE, DeviceCrash.Kind.ANR), crashes.map { it.kind })

        val java = crashes[0]
        assertEquals("com.vitorpamplona.amethyst.debug", java.packageName)
        assertEquals("java.lang.IllegalStateException: boom", java.headline)
        assertEquals("at com.vitorpamplona.amethyst.Feed.load(Feed.kt:12)", java.details.first())
        assertTrue(java.details.contains("Caused by: java.io.IOException: closed"))

        val native = crashes[1]
        assertEquals("com.vitorpamplona.amethyst.debug", native.packageName)
        assertEquals("Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR)", native.headline)
        assertTrue(native.details.single().startsWith("#00 pc"))

        val anr = crashes[2]
        assertEquals("com.vitorpamplona.amethyst.debug", anr.packageName)
        assertEquals(4300, anr.pid)
        assertEquals("Input dispatching timed out", anr.headline)
    }

    @Test
    fun theSameCrashReadTwiceHasTheSameId() {
        assertEquals(CrashLog.parse(log).map { it.id }, CrashLog.parse(log).map { it.id })
        assertTrue(CrashLog.parse(log)[0].summary().startsWith("com.vitorpamplona.amethyst.debug crashed at 11-25 05:10:03.400: java.lang.IllegalStateException: boom"))
    }

    @Test
    fun aQuietLogHasNoCrashes() {
        assertTrue(CrashLog.parse("11-25 05:10:01.100  4242  4242 I Amethyst: Account loaded\n").isEmpty())
    }
}
