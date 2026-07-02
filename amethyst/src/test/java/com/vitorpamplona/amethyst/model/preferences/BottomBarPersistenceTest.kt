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

import com.vitorpamplona.amethyst.ui.navigation.bottombars.BottomBarEntry
import com.vitorpamplona.amethyst.ui.navigation.bottombars.DefaultBottomBarEntries
import com.vitorpamplona.amethyst.ui.navigation.bottombars.NavBarItem
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the "reset to defaults migrates automatically" behavior.
 *
 * Resetting the bottom bar (or never customizing it) is persisted as a blank sentinel rather than the
 * concrete default list, so that a user on the defaults tracks whatever [DefaultBottomBarEntries] the
 * *installed* app version ships. If a future version changes the default, that blank value resolves to
 * the new default on load — the user is migrated instead of being pinned to the old default.
 */
class BottomBarPersistenceTest {
    @Test
    fun defaultsAreStoredAsBlankSentinel() {
        assertEquals("", UiSharedPreferences.encodeBottomBarItems(DefaultBottomBarEntries))
    }

    @Test
    fun blankSentinelDecodesToCurrentDefaults() {
        // The blank sentinel resolves to whatever DefaultBottomBarEntries this build ships. Because it
        // returns the current constant (not a value frozen at reset time), a future version that changes
        // the default automatically migrates every user who is on the defaults.
        assertEquals(DefaultBottomBarEntries, UiSharedPreferences.decodeBottomBarItems(""))
    }

    @Test
    fun customizedBarIsStoredVerbatimAndRoundTrips() {
        val custom =
            listOf(
                BottomBarEntry.BuiltIn(NavBarItem.HOME),
                BottomBarEntry.Favorite("url:https://example.com"),
            )
        val encoded = UiSharedPreferences.encodeBottomBarItems(custom)
        assertEquals(custom, UiSharedPreferences.decodeBottomBarItems(encoded))
    }
}
