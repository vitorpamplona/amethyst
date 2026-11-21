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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/**
 * The Language row's value: the stored tag's display name, or null ("System default") when
 * nothing is stored. It must never fall back to the JVM/system locale.
 */
class LanguageLabelTest {
    private val entries =
        mapOf(
            "English" to "en",
            "English (United Kingdom)" to "en-GB",
            "Deutsch" to "de",
            "Deutsch (Deutschland)" to "de-DE",
        )

    @Test
    fun noStoredLanguageIsSystemDefault() {
        assertNull(languageLabel(entries, null))
    }

    @Test
    fun exactTagMatches() {
        assertEquals("Deutsch", languageLabel(entries, "de"))
    }

    @Test
    fun exactTagBeatsTheLanguageSubtag() {
        assertEquals("English (United Kingdom)", languageLabel(entries, "en-GB"))
    }

    @Test
    fun unlistedRegionFallsBackToTheLanguageSubtag() {
        assertEquals("Deutsch", languageLabel(entries, "de-AT"))
    }

    @Test
    fun aLocaleListIsNamedByItsFirstLocale() {
        assertEquals("Deutsch", languageLabel(entries, "de-AT,en"))
    }

    @Test
    fun unknownTagIsShownAsIs() {
        assertEquals("xx-YY", languageLabel(entries, "xx-YY"))
    }

    @Test
    fun systemDefaultIgnoresTheDefaultLocale() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("de-AT-u-fw-mon-mu-celsius"))
            assertNull(languageLabel(entries, null))
        } finally {
            Locale.setDefault(saved)
        }
    }
}
