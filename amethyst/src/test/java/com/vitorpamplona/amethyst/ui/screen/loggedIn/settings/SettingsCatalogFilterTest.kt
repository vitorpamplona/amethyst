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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCatalogFilterTest {
    private val strings =
        mapOf(
            100 to "Account Settings",
            200 to "Danger Zone",
            1 to "Relay Setup",
            2 to "UI Preferences",
            3 to "Backup Keys",
            20 to "dark mode, theme, font size",
        )

    private fun entry(
        titleRes: Int,
        keywordsRes: Int? = null,
        isDanger: Boolean = false,
    ) = SettingsEntry(
        titleRes = titleRes,
        icon = SettingsIcon.Painter(0, 0),
        keywordsRes = keywordsRes,
        isDanger = isDanger,
        onClick = {},
    )

    private val catalog =
        listOf(
            SettingsCategory(
                titleRes = 100,
                entries =
                    listOf(
                        entry(1),
                        entry(2, keywordsRes = 20),
                    ),
            ),
            SettingsCategory(
                titleRes = 200,
                isDanger = true,
                entries = listOf(entry(3, isDanger = true)),
            ),
        )

    private fun run(query: String) =
        filterSettings(
            catalog = catalog,
            query = query,
            stringLookup = { strings.getValue(it) },
        )

    @Test
    fun blankQueryReturnsFullCatalog() {
        val result = run("")
        assertEquals(2, result.size)
        assertEquals(2, result[0].entries.size)
        assertEquals(1, result[1].entries.size)
    }

    @Test
    fun whitespaceQueryReturnsFullCatalog() {
        assertEquals(2, run("   ").size)
    }

    @Test
    fun titleMatchIsCaseInsensitive() {
        val result = run("relay")
        assertEquals(1, result.size)
        assertEquals(100, result[0].titleRes)
        assertEquals(1, result[0].entries.size)
        assertEquals(1, result[0].entries[0].titleRes)
    }

    @Test
    fun keywordMatchSurfacesEntryWhoseTitleDoesNotMatch() {
        val result = run("dark mode")
        assertEquals(1, result.size)
        assertEquals(2, result[0].entries[0].titleRes) // UI Preferences, matched via keywords
    }

    @Test
    fun categoryTitleMatchSurfacesWholeCategory() {
        val result = run("account")
        assertEquals(1, result.size)
        assertEquals(100, result[0].titleRes)
        assertEquals(2, result[0].entries.size) // both rows shown because the category name matched
    }

    @Test
    fun categoryWithNoMatchesIsDropped() {
        val result = run("relay")
        assertTrue(result.none { it.titleRes == 200 })
    }

    @Test
    fun noMatchesReturnsEmptyList() {
        assertTrue(run("zzzznomatch").isEmpty())
    }

    @Test
    fun dangerFlagsPreservedThroughFiltering() {
        val result = run("backup")
        assertEquals(1, result.size)
        assertTrue(result[0].isDanger)
        assertTrue(result[0].entries[0].isDanger)
    }

    @Test
    fun prefixOfAWordMatches() {
        // "rel" is a prefix of "Relay" (title); "the" is a prefix of "theme" (keyword).
        assertEquals(1, run("rel")[0].entries[0].titleRes)
        assertEquals(2, run("the")[0].entries[0].titleRes)
    }

    @Test
    fun midWordTermDoesNotMatch() {
        // Word-prefix, not substring: "ackup" is inside "Backup" but not a prefix of any word.
        assertTrue(run("ackup").isEmpty())
    }

    @Test
    fun everyQueryTermMustPrefixSomeWord() {
        assertEquals(2, run("dark size")[0].entries[0].titleRes) // both terms hit UI Preferences
        assertTrue(run("dark zzz").isEmpty()) // second term matches nothing
    }
}
