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

import com.vitorpamplona.amethyst.commons.privacylock.InactivityTimer
import com.vitorpamplona.amethyst.commons.privacylock.LockScope
import com.vitorpamplona.amethyst.commons.privacylock.PrivacyLockSettings
import com.vitorpamplona.quartz.utils.Log
import java.util.prefs.Preferences

/**
 * Carries the legacy desktop app's privacy lock over on the first start of the new one: its one
 * switch locked both the messages and the wallet, so both locks come on, with the same password
 * hash and idle timer. A failed read leaves every lock off.
 */
fun importLegacyPrivacyLock(settings: PrivacyLockSettings) {
    try {
        val root = Preferences.userRoot()
        if (!root.nodeExists(PrivacyLockSettings.NODE_NAME)) return
        val legacy = root.node(PrivacyLockSettings.NODE_NAME)

        val hash = legacy.get(PrivacyLockSettings.KEY_PASSWORD_HASHED, null) ?: return
        settings.setPasswordHashed(hash)
        settings.setInactivityTimer(
            InactivityTimer.fromOrdinal(legacy.getInt(PrivacyLockSettings.KEY_INACTIVITY_TIMER, InactivityTimer.DEFAULT.ordinal)),
        )
        if (legacy.getBoolean(PrivacyLockSettings.KEY_LOCK_ENABLED, false)) {
            settings.setScopeLocked(LockScope.Messages, true)
            settings.setScopeLocked(LockScope.Wallet, true)
        }
    } catch (e: Exception) {
        Log.w("LegacyPrivacyLock", "Could not read the legacy privacy lock", e)
    }
}
