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
package com.vitorpamplona.amethyst.desktop.ui.search

import androidx.compose.foundation.text.input.insert
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.vitorpamplona.amethyst.commons.search.AdvancedSearchBarState
import com.vitorpamplona.amethyst.commons.ui.search.SearchFieldState
import com.vitorpamplona.amethyst.desktop.ui.BindSearchField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The desktop search screen's two-way binding between the field and the query, driven frame by
 * frame so two keystrokes can land between one frame and the next.
 */
class SearchSyncRaceTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun twoKeystrokesBetweenFramesAreNotRolledBack() {
        val field = SearchFieldState()
        val state = AdvancedSearchBarState(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val displayText by state.displayText.collectAsState()
            BindSearchField(field, state, displayText)
        }
        compose.mainClock.advanceTimeByFrame()
        field.textState.edit { insert(length, "a") }
        compose.mainClock.advanceTimeByFrame()
        field.textState.edit { insert(length, "b") }
        repeat(5) { compose.mainClock.advanceTimeByFrame() }
        assertEquals("ab", field.text)
    }

    @Test
    fun aFormDrivenChangeStillReachesTheField() {
        val field = SearchFieldState("bitcoin")
        val state = AdvancedSearchBarState(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        compose.setContent {
            val displayText by state.displayText.collectAsState()
            BindSearchField(field, state, displayText)
        }
        compose.waitForIdle()
        state.updateKinds(listOf(1))
        compose.waitForIdle()
        assertEquals(state.displayText.value, field.text)
        assertTrue(field.text.contains("kind:"), field.text)
    }
}
