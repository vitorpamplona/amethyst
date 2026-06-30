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

import com.vitorpamplona.amethyst.commons.privacylock.PrivacyLockSettings.Companion.DEFAULT_LOCK_ENABLED
import com.vitorpamplona.amethyst.commons.privacylock.PrivacyLockSettings.Companion.KEY_FIRST_RUN_CARD_SEEN
import com.vitorpamplona.amethyst.commons.privacylock.PrivacyLockSettings.Companion.KEY_INACTIVITY_TIMER
import com.vitorpamplona.amethyst.commons.privacylock.PrivacyLockSettings.Companion.KEY_LOCK_ENABLED
import com.vitorpamplona.amethyst.commons.privacylock.PrivacyLockSettings.Companion.KEY_REDACTION_LEVEL
import com.vitorpamplona.amethyst.commons.privacylock.PrivacyLockSettings.Companion.NODE_NAME
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.prefs.Preferences

/**
 * Desktop JVM-platform implementation backed by [java.util.prefs.Preferences].
 *
 * Default node `com/vitorpamplona/amethyst/privacylock` is shared between
 * the Desktop app and `amy` CLI running as the same OS user — both observe
 * the same setting without extra plumbing. `Preferences` auto-flushes on
 * shutdown and periodically; no explicit `flush()` calls needed.
 *
 * Initial values are read synchronously in the constructor — required so
 * the first composition sees seeded state without flashing content.
 */
class PreferencesPrivacyLockSettings(
    private val prefs: Preferences = Preferences.userRoot().node(NODE_NAME),
) : PrivacyLockSettings {
    private val mutableEnabled = MutableStateFlow(prefs.getBoolean(KEY_LOCK_ENABLED, DEFAULT_LOCK_ENABLED))
    private val mutableTimer =
        MutableStateFlow(InactivityTimer.fromOrdinal(prefs.getInt(KEY_INACTIVITY_TIMER, InactivityTimer.DEFAULT.ordinal)))
    private val mutableRedaction =
        MutableStateFlow(DmRedactionLevel.fromOrdinal(prefs.getInt(KEY_REDACTION_LEVEL, DmRedactionLevel.DEFAULT.ordinal)))
    private val mutableFirstRunSeen = MutableStateFlow(prefs.getBoolean(KEY_FIRST_RUN_CARD_SEEN, false))

    override val lockEnabled: StateFlow<Boolean> = mutableEnabled.asStateFlow()
    override val inactivityTimer: StateFlow<InactivityTimer> = mutableTimer.asStateFlow()
    override val redactionLevel: StateFlow<DmRedactionLevel> = mutableRedaction.asStateFlow()
    override val firstRunCardSeen: StateFlow<Boolean> = mutableFirstRunSeen.asStateFlow()

    override fun setLockEnabled(enabled: Boolean) {
        mutableEnabled.value = enabled
        prefs.putBoolean(KEY_LOCK_ENABLED, enabled)
        // First time the user enables the lock, auto-bump redaction to Generic
        // unless they've explicitly chosen Full (deepen review — closes the
        // "locked UI / leaking notifications" anti-pattern).
        if (enabled && mutableRedaction.value == DmRedactionLevel.Full) {
            val userPickedFull = prefs.getBoolean("redaction_user_set", false)
            if (!userPickedFull) setRedactionLevel(DmRedactionLevel.Generic)
        }
    }

    override fun setInactivityTimer(timer: InactivityTimer) {
        mutableTimer.value = timer
        prefs.putInt(KEY_INACTIVITY_TIMER, timer.ordinal)
    }

    override fun setRedactionLevel(level: DmRedactionLevel) {
        mutableRedaction.value = level
        prefs.putInt(KEY_REDACTION_LEVEL, level.ordinal)
        prefs.putBoolean("redaction_user_set", true)
    }

    override fun setFirstRunCardSeen(seen: Boolean) {
        mutableFirstRunSeen.value = seen
        prefs.putBoolean(KEY_FIRST_RUN_CARD_SEEN, seen)
    }
}
