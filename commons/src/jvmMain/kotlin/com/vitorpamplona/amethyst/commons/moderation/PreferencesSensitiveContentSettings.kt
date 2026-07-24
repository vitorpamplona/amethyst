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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.prefs.Preferences

/**
 * "Always show sensitive content" (NIP-36) preference, backed by
 * [java.util.prefs.Preferences] under the same shared node as the other content
 * filters (so Desktop and the `amy` CLI observe the same value).
 *
 * [showSensitiveContent] follows the `LiveHiddenUsers`/`Note.isHiddenFor`
 * convention: `null` = respect content warnings (blur), `true` = always show.
 * `false` is never emitted — the toggle is binary (on ⇒ true, off ⇒ null).
 */
class PreferencesSensitiveContentSettings(
    private val prefs: Preferences = Preferences.userRoot().node(NODE_NAME),
) {
    private val mutable = MutableStateFlow(toChoice(prefs.getBoolean(KEY_ALWAYS_SHOW, DEFAULT_ALWAYS_SHOW)))

    /** `null` = respect warnings (blur); `true` = always show. */
    val showSensitiveContent: StateFlow<Boolean?> = mutable.asStateFlow()

    fun setAlwaysShow(alwaysShow: Boolean) {
        mutable.value = toChoice(alwaysShow)
        prefs.putBoolean(KEY_ALWAYS_SHOW, alwaysShow)
    }

    companion object {
        const val NODE_NAME = "com/vitorpamplona/amethyst/filters"
        const val KEY_ALWAYS_SHOW = "always_show_sensitive"
        const val DEFAULT_ALWAYS_SHOW = false

        private fun toChoice(alwaysShow: Boolean): Boolean? = if (alwaysShow) true else null
    }
}
