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

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.StateFlow

/**
 * User-tunable settings for the Messages privacy lock.
 *
 * Device-global per the brainstorm decision (no NIP-78 sync, no per-account
 * variants). Platform implementations back this with `java.util.prefs`
 * (Desktop) or `SharedPreferences` (Android). Initial values must be read
 * synchronously from storage in the constructor so the first `setContent { }`
 * sees the seeded state — required to close the deep-link race fix (security
 * hardening H1 in the plan).
 */
@Stable
interface PrivacyLockSettings {
    val lockEnabled: StateFlow<Boolean>
    val inactivityTimer: StateFlow<InactivityTimer>
    val redactionLevel: StateFlow<DmRedactionLevel>
    val firstRunCardSeen: StateFlow<Boolean>

    fun setLockEnabled(enabled: Boolean)

    fun setInactivityTimer(timer: InactivityTimer)

    fun setRedactionLevel(level: DmRedactionLevel)

    fun setFirstRunCardSeen(seen: Boolean)

    companion object {
        const val DEFAULT_LOCK_ENABLED = false
        const val NODE_NAME = "com/vitorpamplona/amethyst/privacylock"
        const val KEY_LOCK_ENABLED = "lock_enabled"
        const val KEY_INACTIVITY_TIMER = "inactivity_timer_ordinal"
        const val KEY_REDACTION_LEVEL = "redaction_level_ordinal"
        const val KEY_FIRST_RUN_CARD_SEEN = "first_run_card_seen"
    }
}
