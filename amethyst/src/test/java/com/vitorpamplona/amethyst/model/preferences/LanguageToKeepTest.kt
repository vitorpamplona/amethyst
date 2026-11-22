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
package com.vitorpamplona.amethyst.model.preferences

import com.vitorpamplona.amethyst.model.preferences.UiSharedPreferences.Companion.languageToKeep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The language preference the app starts from. On API 33+ Android's per-app locale wins over the
 * stored choice; below 33 (no system value) the stored choice is kept and pushed.
 */
class LanguageToKeepTest {
    @Test
    fun belowApi33TheStoredChoiceIsKept() {
        assertEquals("de", languageToKeep(stored = "de", systemTags = null))
        assertNull(languageToKeep(stored = null, systemTags = null))
    }

    @Test
    fun aClearedPerAppLocaleBecomesSystemDefault() {
        assertNull(languageToKeep(stored = "en", systemTags = ""))
    }

    @Test
    fun aPerAppLocalePickedInAndroidSettingsWins() {
        assertEquals("fr", languageToKeep(stored = null, systemTags = "fr"))
        assertEquals("fr", languageToKeep(stored = "de", systemTags = "fr"))
    }

    @Test
    fun aMatchingPerAppLocaleKeepsTheStoredChoice() {
        assertEquals("de", languageToKeep(stored = "de", systemTags = "de"))
    }
}
