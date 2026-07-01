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
package com.vitorpamplona.amethyst.commons.moderation

import com.vitorpamplona.amethyst.commons.moderation.HashtagSpamSettings.Companion.DEFAULT_ENABLED
import com.vitorpamplona.amethyst.commons.moderation.HashtagSpamSettings.Companion.DEFAULT_THRESHOLD
import com.vitorpamplona.amethyst.commons.moderation.HashtagSpamSettings.Companion.MAX_THRESHOLD
import com.vitorpamplona.amethyst.commons.moderation.HashtagSpamSettings.Companion.MIN_THRESHOLD
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.prefs.Preferences

/**
 * JVM-platform implementation backed by [java.util.prefs.Preferences].
 *
 * The default node `com/vitorpamplona/amethyst/filters` is shared between
 * the Desktop app and the `amy` CLI — both binaries running as the same OS
 * user observe the same setting without any extra plumbing.
 *
 * `Preferences` auto-flushes on JVM shutdown and periodically, so no
 * explicit `flush()` call is needed and avoids per-set disk thrash.
 */
class PreferencesHashtagSpamSettings(
    private val prefs: Preferences = Preferences.userRoot().node(NODE_NAME),
) : HashtagSpamSettings {
    private val mutableEnabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, DEFAULT_ENABLED))
    private val mutableThreshold =
        MutableStateFlow(
            prefs.getInt(KEY_THRESHOLD, DEFAULT_THRESHOLD).coerceIn(MIN_THRESHOLD, MAX_THRESHOLD),
        )

    override val enabled: StateFlow<Boolean> = mutableEnabled.asStateFlow()
    override val threshold: StateFlow<Int> = mutableThreshold.asStateFlow()

    override fun setEnabled(enabled: Boolean) {
        mutableEnabled.value = enabled
        prefs.putBoolean(KEY_ENABLED, enabled)
    }

    override fun setThreshold(threshold: Int) {
        val clamped = threshold.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
        mutableThreshold.value = clamped
        prefs.putInt(KEY_THRESHOLD, clamped)
    }

    companion object {
        const val NODE_NAME = "com/vitorpamplona/amethyst/filters"
        const val KEY_ENABLED = "hashtag_spam_enabled"
        const val KEY_THRESHOLD = "hashtag_spam_threshold"
    }
}
