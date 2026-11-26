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

import com.vitorpamplona.androiddevice.adb.Adb
import com.vitorpamplona.androiddevice.device.AdbScreen
import com.vitorpamplona.androiddevice.emulator.EmulatorLauncher
import com.vitorpamplona.devicecore.ProcessCommandRunner
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CommandsTest {
    @Test
    fun cancellingACommandEndsItsProcess() =
        runBlocking {
            val started = System.currentTimeMillis()
            assertFailsWith<TimeoutCancellationException> {
                withTimeout(500) { ProcessCommandRunner.run(listOf("sleep", "30"), timeoutMs = 60_000) }
            }
            // Back at once, not after the 30 s sleep or the minute's timeout.
            assertTrue(System.currentTimeMillis() - started < 10_000)
            Thread.sleep(200)
            val survivors =
                ProcessHandle
                    .current()
                    .children()
                    .filter {
                        it
                            .info()
                            .command()
                            .orElse("")
                            .endsWith("sleep")
                    }.count()
            assertTrue(survivors == 0L, "the sleep is still running")
        }

    @Test
    fun portsBeingStartedAreNotHandedOutTwice() {
        val first = EmulatorLauncher.reservePort()
        val second = EmulatorLauncher.reservePort()
        try {
            assertNotEquals(first, second)
        } finally {
            EmulatorLauncher.releasePort(first)
            EmulatorLauncher.releasePort(second)
        }
    }
}

class AdbScreenPacingTest {
    @Test
    fun anIdleScreenIsLookedAtLessOftenUntilTheUserActs() =
        run {
            val screen =
                AdbScreen(
                    Adb(File("adb"), ProcessCommandRunner),
                    "serial",
                    minFrameIntervalMs = 150,
                    idleFrameIntervalMs = 1_000,
                )
            assertEquals(150, screen.nextInterval(0))
            assertEquals(600, screen.nextInterval(2))
            assertEquals(1_000, screen.nextInterval(10))
        }
}
