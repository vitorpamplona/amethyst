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
package com.vitorpamplona.amethyst.service.applock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AppLockController] — the pure-state core of the app-lock gate.
 * Drives the lifecycle transitions directly with a controllable clock so the
 * 5-minute threshold logic doesn't need a real wall-clock wait.
 */
class AppLockControllerTest {
    private val fiveMinutes = AppLockController.DEFAULT_INACTIVITY_TIMEOUT_MS

    @Test
    fun disabledByDefaultAndNotLocked() {
        val controller = AppLockController()
        assertFalse(controller.enabled)
        assertFalse(controller.isLocked.value)
    }

    @Test
    fun lockIfEnabledIsNoOpWhenDisabled() {
        // Cold start with the feature off must never gate the first paint.
        val controller = AppLockController()
        controller.lockIfEnabled()
        assertFalse(controller.isLocked.value)
    }

    @Test
    fun lockIfEnabledLocksOnColdStartWhenEnabled() {
        val controller = AppLockController()
        controller.setEnabled(true)
        controller.lockIfEnabled()
        assertTrue("enabling + lockIfEnabled must lock on cold start", controller.isLocked.value)
    }

    @Test
    fun disablingClearsAnActiveLock() {
        val controller = AppLockController()
        controller.setEnabled(true)
        controller.lockIfEnabled()
        assertTrue(controller.isLocked.value)

        controller.setEnabled(false)
        assertFalse("turning the feature off must release the lock screen", controller.isLocked.value)
    }

    @Test
    fun shortBackgroundDoesNotRelock() {
        // A quick app switch (well under 5 min) must not demand re-auth, otherwise
        // the "5 minutes since last usage" rule would be meaningless.
        var fakeNow = 0L
        val controller = AppLockController(nowMillis = { fakeNow })
        controller.setEnabled(true)

        fakeNow = 1_000L
        controller.onActivityStarted() // cold start, no prior background
        fakeNow = 2_000L
        controller.onActivityStopped()
        fakeNow = 2_000L + (fiveMinutes - 1) // backgrounded for just under 5 min
        controller.onActivityStarted()

        assertFalse("background < threshold must not re-lock", controller.isLocked.value)
    }

    @Test
    fun backgroundLongerThanThresholdRelocks() {
        var fakeNow = 0L
        val controller = AppLockController(nowMillis = { fakeNow })
        controller.setEnabled(true)

        fakeNow = 1_000L
        controller.onActivityStarted()
        fakeNow = 2_000L
        controller.onActivityStopped()
        fakeNow = 2_000L + fiveMinutes // backgrounded for exactly 5 min
        controller.onActivityStarted()

        assertTrue("background ≥ threshold must re-lock on resume", controller.isLocked.value)
    }

    @Test
    fun longBackgroundDoesNotLockWhenDisabled() {
        var fakeNow = 0L
        val controller = AppLockController(nowMillis = { fakeNow })
        // feature never enabled

        fakeNow = 1_000L
        controller.onActivityStarted()
        fakeNow = 2_000L
        controller.onActivityStopped()
        fakeNow = 2_000L + fiveMinutes * 10
        controller.onActivityStarted()

        assertFalse("disabled gate must never lock regardless of background time", controller.isLocked.value)
    }

    @Test
    fun unlockClearsLockAndAvoidsImmediateRelock() {
        // The keyguard PIN prompt is a separate activity: confirming it stops and
        // restarts MainActivity. After unlock(), that restart must NOT re-lock even
        // though the stop recorded a background timestamp.
        var fakeNow = 0L
        val controller = AppLockController(nowMillis = { fakeNow })
        controller.setEnabled(true)
        controller.lockIfEnabled()

        fakeNow = 1_000L
        controller.onActivityStarted() // app foreground, lock screen visible
        // user taps unlock -> keyguard activity launches -> main activity stops
        fakeNow = 2_000L
        controller.onActivityStopped()
        // auth succeeds
        controller.unlock()
        assertFalse(controller.isLocked.value)
        // keyguard returns, main activity restarts much later
        fakeNow = 2_000L + fiveMinutes * 2
        controller.onActivityStarted()

        assertFalse("a fresh unlock must reset the timer so the return doesn't re-lock", controller.isLocked.value)
    }

    @Test
    fun firstForegroundAfterProcessStartDoesNotLock() {
        // The very first onActivityStarted has no prior background timestamp;
        // cold-start locking is lockIfEnabled()'s job, not this path's.
        var fakeNow = 0L
        val controller = AppLockController(nowMillis = { fakeNow })
        controller.setEnabled(true)

        fakeNow = 1_000L
        controller.onActivityStarted()

        assertFalse(controller.isLocked.value)
    }

    @Test
    fun multipleActivitiesTrackForegroundCorrectly() {
        // PiP / dialog activities overlay the main one. The app only truly
        // backgrounds when the last started activity stops.
        var fakeNow = 0L
        val controller = AppLockController(nowMillis = { fakeNow })
        controller.setEnabled(true)

        fakeNow = 1_000L
        controller.onActivityStarted() // cold start
        fakeNow = 2_000L
        controller.onActivityStarted() // second activity on top, still foreground
        fakeNow = 3_000L
        controller.onActivityStopped() // one left -> still foreground
        fakeNow = 3_000L + fiveMinutes * 2
        controller.onActivityStarted() // another comes on top, never backgrounded
        assertFalse("never truly backgrounded must not lock", controller.isLocked.value)

        // Now everything stops -> truly background, then resume after > 5 min.
        controller.onActivityStopped()
        controller.onActivityStopped()
        fakeNow += fiveMinutes + 1
        controller.onActivityStarted()
        assertTrue("resume after a long true background must lock", controller.isLocked.value)
    }
}
