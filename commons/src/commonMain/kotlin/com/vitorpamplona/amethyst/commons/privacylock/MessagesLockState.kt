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
package com.vitorpamplona.amethyst.commons.privacylock

import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * App-global state holder for the Messages privacy lock.
 *
 * - Single instance per app, provided via [LocalMessagesLockState] at the
 *   App composition root.
 * - Initial value is seeded synchronously from [settings.lockEnabled.value]
 *   so the first composition sees [LockState.Locked] without flashing
 *   content (deep-link race fix, plan §Security Hardening H1).
 * - The underlying StateFlow is hot (`MutableStateFlow`); notification path
 *   can read `state.value` synchronously without subscribing.
 */
class MessagesLockState(
    private val settings: PrivacyLockSettings,
    private val scope: CoroutineScope,
) {
    private val seed: LockState =
        if (settings.lockEnabled.value) LockState.Locked else LockState.Disabled

    private val mutableState = MutableStateFlow(seed)
    val state: StateFlow<LockState> = mutableState.asStateFlow()

    private var idleTimerJob: Job? = null

    init {
        settings.lockEnabled
            .onEach { enabled ->
                if (!enabled) {
                    cancelIdleTimer()
                    mutableState.value = LockState.Disabled
                } else if (mutableState.value is LockState.Disabled) {
                    mutableState.value = LockState.Locked
                }
            }.launchIn(scope)

        combine(settings.lockEnabled, settings.inactivityTimer) { enabled, timer -> enabled to timer }
            .onEach { _ ->
                if (mutableState.value is LockState.Unlocked) restartIdleTimer()
            }.launchIn(scope)
    }

    /** Resets the inactivity timer. No-op unless currently Unlocked. */
    fun onUserInteraction() {
        if (mutableState.value !is LockState.Unlocked) return
        restartIdleTimer()
    }

    /** Re-lock immediately on route exit or account switch. Idempotent. */
    fun onLeaveRoute() {
        if (mutableState.value is LockState.Unlocked) {
            cancelIdleTimer()
            mutableState.value = LockState.Locked
        }
    }

    /** Transition Locked → Unlocked. Idempotent. Starts the idle timer. */
    fun onUnlockSuccess() {
        if (mutableState.value is LockState.Locked) {
            mutableState.value = LockState.Unlocked
            restartIdleTimer()
        }
    }

    /**
     * Triggered when biometric / OS credential is permanently unavailable.
     * Auto-disables the lock so the user can keep accessing Messages.
     */
    fun onCredentialUnavailable() {
        cancelIdleTimer()
        settings.setLockEnabled(false)
        mutableState.value = LockState.Disabled
    }

    private fun restartIdleTimer() {
        cancelIdleTimer()
        val millis = settings.inactivityTimer.value.millis ?: return
        idleTimerJob =
            scope.launch {
                delay(millis)
                if (mutableState.value is LockState.Unlocked) {
                    mutableState.value = LockState.Locked
                }
            }
    }

    private fun cancelIdleTimer() {
        idleTimerJob?.cancel()
        idleTimerJob = null
    }
}

/** Provided once at the App composition root. */
val LocalMessagesLockState =
    compositionLocalOf<MessagesLockState> {
        error("LocalMessagesLockState not provided — wrap App() with CompositionLocalProvider")
    }
