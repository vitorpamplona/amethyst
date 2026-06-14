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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Pure-state core of the app-lock gate, decoupled from `android.app.Activity`
 * so the unit tests can drive it without Robolectric or Mockito.
 *
 * See [AppLockLifecycleHook] / [AppLockState] for the production wiring — this
 * class is just the testable core. It mirrors the structure of
 * [com.vitorpamplona.amethyst.service.nests.AppForegroundCounter].
 *
 * When the feature is [enabled], the app becomes [isLocked] when:
 *  - it cold-starts ([lockIfEnabled] is called once the persisted setting loads), or
 *  - it returns to the foreground after spending at least [inactivityTimeoutMs]
 *    in the background.
 *
 * A short background (notification pull-down, the biometric/keyguard prompt itself,
 * a quick app switch) under the threshold does NOT re-lock — otherwise the
 * "5 minutes since last usage" rule would be meaningless and every glance at
 * another app would demand a fingerprint.
 *
 * Threading: all state-mutating methods are documented to run on the Android main
 * thread (Application lifecycle callbacks fire there); tests call them serially,
 * so no synchronisation is needed.
 */
class AppLockController(
    val inactivityTimeoutMs: Long = DEFAULT_INACTIVITY_TIMEOUT_MS,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked

    @Volatile
    var enabled: Boolean = false
        private set

    private var startedActivities = 0
    private var lastBackgroundedAtMillis: Long = -1L

    /**
     * Enable or disable the gate. Disabling immediately clears any active lock so the
     * user isn't stranded on the lock screen after turning the feature off.
     */
    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) _isLocked.value = false
    }

    /** Lock now if the feature is on. Called at cold start to gate the first paint. */
    fun lockIfEnabled() {
        if (enabled) _isLocked.value = true
    }

    /** Clears the lock after a successful authentication. */
    fun unlock() {
        _isLocked.value = false
        // The keyguard PIN prompt is a separate activity, so confirming it stops and
        // restarts MainActivity. Reset the timer so that return-to-foreground doesn't
        // immediately re-lock.
        lastBackgroundedAtMillis = -1L
    }

    /**
     * Increment the started-activity counter and, on the 0 → 1 transition (app
     * returning to the foreground) with the feature enabled, lock if the app spent
     * at least [inactivityTimeoutMs] in the background. The first onActivityStarted
     * after process start is a no-op (no prior background timestamp); cold-start
     * locking is handled by [lockIfEnabled] instead.
     */
    fun onActivityStarted() {
        val wasBackgrounded = startedActivities == 0
        startedActivities++
        if (!wasBackgrounded) return
        if (!enabled) return
        val backgroundedAt = lastBackgroundedAtMillis
        if (backgroundedAt < 0L) return
        val backgroundedFor = nowMillis() - backgroundedAt
        if (backgroundedFor >= inactivityTimeoutMs) {
            _isLocked.value = true
        }
    }

    /** Decrement the counter; on N → 0 transition, record the background timestamp. */
    fun onActivityStopped() {
        startedActivities--
        if (startedActivities <= 0) {
            startedActivities = 0
            lastBackgroundedAtMillis = nowMillis()
        }
    }

    companion object {
        /** 5 minutes — the inactivity window after which a resume requires re-auth. */
        const val DEFAULT_INACTIVITY_TIMEOUT_MS = 5 * 60 * 1000L
    }
}
