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

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.prefs.Preferences

class PreferencesSensitiveContentSettingsTest {
    private val testNode = "com/vitorpamplona/amethyst/test/sensitive_${System.currentTimeMillis()}"

    private fun prefs(): Preferences = Preferences.userRoot().node(testNode)

    @Before
    fun setup() {
        prefs().clear()
    }

    @After
    fun teardown() {
        prefs().removeNode()
    }

    @Test
    fun defaultRespectsWarnings() {
        // null = respect content warnings (blur), matching Note.isHiddenFor.
        assertNull(PreferencesSensitiveContentSettings(prefs()).showSensitiveContent.value)
    }

    @Test
    fun onMapsToTrue() {
        val settings = PreferencesSensitiveContentSettings(prefs())
        settings.setAlwaysShow(true)
        assertEquals(true, settings.showSensitiveContent.value)
    }

    @Test
    fun offMapsToNullNotFalse() {
        val settings = PreferencesSensitiveContentSettings(prefs())
        settings.setAlwaysShow(true)
        settings.setAlwaysShow(false)
        // Off is null (respect warnings), never false — Note.isHiddenFor only
        // blurs when showSensitiveContent == false, which must never happen here.
        assertNull(settings.showSensitiveContent.value)
    }

    @Test
    fun persistsAcrossReload() {
        PreferencesSensitiveContentSettings(prefs()).setAlwaysShow(true)
        assertEquals(true, PreferencesSensitiveContentSettings(prefs()).showSensitiveContent.value)
    }
}
