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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.prefs.Preferences

class PreferencesHashtagSpamSettingsTest {
    private val testNode = "com/vitorpamplona/amethyst/test/hashtag_spam_${System.currentTimeMillis()}"

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
    fun defaultsAreOnAndFive() {
        val settings = PreferencesHashtagSpamSettings(prefs())
        assertTrue(settings.enabled.value)
        assertEquals(5, settings.threshold.value)
    }

    @Test
    fun setEnabledPersists() {
        val settings = PreferencesHashtagSpamSettings(prefs())
        settings.setEnabled(false)
        assertFalse(settings.enabled.value)

        val reloaded = PreferencesHashtagSpamSettings(prefs())
        assertFalse(reloaded.enabled.value)
    }

    @Test
    fun setThresholdPersists() {
        val settings = PreferencesHashtagSpamSettings(prefs())
        settings.setThreshold(12)
        assertEquals(12, settings.threshold.value)

        val reloaded = PreferencesHashtagSpamSettings(prefs())
        assertEquals(12, reloaded.threshold.value)
    }

    @Test
    fun thresholdClampsBelowMin() {
        val settings = PreferencesHashtagSpamSettings(prefs())
        settings.setThreshold(-50)
        assertEquals(HashtagSpamSettings.MIN_THRESHOLD, settings.threshold.value)
    }

    @Test
    fun thresholdClampsAboveMax() {
        val settings = PreferencesHashtagSpamSettings(prefs())
        settings.setThreshold(9999)
        assertEquals(HashtagSpamSettings.MAX_THRESHOLD, settings.threshold.value)
    }
}
