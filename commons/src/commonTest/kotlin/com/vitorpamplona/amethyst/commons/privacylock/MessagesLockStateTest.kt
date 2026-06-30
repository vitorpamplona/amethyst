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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MessagesLockStateTest {
    private class FakeSettings(
        lockEnabled: Boolean = false,
        timer: InactivityTimer = InactivityTimer.OneMin,
    ) : PrivacyLockSettings {
        private val mutableLockEnabled = MutableStateFlow(lockEnabled)
        private val mutableTimer = MutableStateFlow(timer)
        private val mutableRedaction = MutableStateFlow(DmRedactionLevel.DEFAULT)
        private val mutableFirstRunSeen = MutableStateFlow(false)

        override val lockEnabled: StateFlow<Boolean> = mutableLockEnabled.asStateFlow()
        override val inactivityTimer: StateFlow<InactivityTimer> = mutableTimer.asStateFlow()
        override val redactionLevel: StateFlow<DmRedactionLevel> = mutableRedaction.asStateFlow()
        override val firstRunCardSeen: StateFlow<Boolean> = mutableFirstRunSeen.asStateFlow()

        override fun setLockEnabled(enabled: Boolean) {
            mutableLockEnabled.value = enabled
        }

        override fun setInactivityTimer(timer: InactivityTimer) {
            mutableTimer.value = timer
        }

        override fun setRedactionLevel(level: DmRedactionLevel) {
            mutableRedaction.value = level
        }

        override fun setFirstRunCardSeen(seen: Boolean) {
            mutableFirstRunSeen.value = seen
        }
    }

    @Test
    fun cold_start_with_lock_enabled_seeds_to_locked() =
        runTest {
            val settings = FakeSettings(lockEnabled = true)
            val state = MessagesLockState(settings, backgroundScope)
            assertEquals(LockState.Locked, state.state.value)
        }

    @Test
    fun cold_start_with_lock_disabled_seeds_to_disabled() =
        runTest {
            val settings = FakeSettings(lockEnabled = false)
            val state = MessagesLockState(settings, backgroundScope)
            assertEquals(LockState.Disabled, state.state.value)
        }

    @Test
    fun unlock_success_transitions_to_unlocked_and_idle_timer_fires() =
        runTest {
            val settings = FakeSettings(lockEnabled = true, timer = InactivityTimer.OneMin)
            val state = MessagesLockState(settings, backgroundScope)
            state.onUnlockSuccess()
            assertEquals(LockState.Unlocked, state.state.value)
            advanceTimeBy(InactivityTimer.OneMin.millis!! + 1_000L)
            assertEquals(LockState.Locked, state.state.value)
        }

    @Test
    fun leave_route_locks_immediately() =
        runTest {
            val settings = FakeSettings(lockEnabled = true, timer = InactivityTimer.OneHour)
            val state = MessagesLockState(settings, backgroundScope)
            state.onUnlockSuccess()
            assertEquals(LockState.Unlocked, state.state.value)
            state.onLeaveRoute()
            assertEquals(LockState.Locked, state.state.value)
        }

    @Test
    fun toggling_lock_off_transitions_to_disabled() =
        runTest(UnconfinedTestDispatcher()) {
            val settings = FakeSettings(lockEnabled = true)
            val state = MessagesLockState(settings, backgroundScope)
            state.onUnlockSuccess()
            assertEquals(LockState.Unlocked, state.state.value)
            settings.setLockEnabled(false)
            assertEquals(LockState.Disabled, state.state.value)
        }

    @Test
    fun never_timer_does_not_fire() =
        runTest {
            val settings = FakeSettings(lockEnabled = true, timer = InactivityTimer.Never)
            val state = MessagesLockState(settings, backgroundScope)
            state.onUnlockSuccess()
            advanceTimeBy(InactivityTimer.OneHour.millis!! * 2)
            assertEquals(LockState.Unlocked, state.state.value)
        }

    @Test
    fun user_interaction_resets_idle_timer() =
        runTest {
            val settings = FakeSettings(lockEnabled = true, timer = InactivityTimer.OneMin)
            val state = MessagesLockState(settings, backgroundScope)
            state.onUnlockSuccess()
            advanceTimeBy(InactivityTimer.OneMin.millis!! - 1_000L)
            state.onUserInteraction()
            advanceTimeBy(InactivityTimer.OneMin.millis!! - 1_000L)
            assertTrue(state.state.value is LockState.Unlocked)
            advanceTimeBy(2_000L)
            assertEquals(LockState.Locked, state.state.value)
        }

    @Test
    fun credential_unavailable_disables_lock() =
        runTest {
            val settings = FakeSettings(lockEnabled = true)
            val state = MessagesLockState(settings, backgroundScope)
            state.onCredentialUnavailable()
            assertEquals(LockState.Disabled, state.state.value)
            assertEquals(false, settings.lockEnabled.value)
        }
}
