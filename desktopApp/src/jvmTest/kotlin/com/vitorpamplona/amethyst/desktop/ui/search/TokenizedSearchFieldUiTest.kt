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

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.text.TextRange
import com.vitorpamplona.amethyst.commons.ui.search.SearchFieldState
import com.vitorpamplona.amethyst.commons.ui.search.TokenizedSearchField
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The shared search field driven through a real Compose text field: what it draws over the
 * typed text, and that typing and picking still edit the typed text underneath.
 */
class TokenizedSearchFieldUiTest {
    @get:Rule
    val compose = createComposeRule()

    private companion object {
        const val NPUB = "npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6"
    }

    private fun setField(state: SearchFieldState) {
        compose.setContent {
            MaterialTheme {
                TokenizedSearchField(state = state, displayName = { "Alice" })
            }
        }
    }

    private fun drawn(): String =
        compose
            .onNode(hasSetTextAction())
            .fetchSemanticsNode()
            .config[SemanticsProperties.EditableText]
            .text

    @Test
    fun anUnfocusedFieldDrawsItsKeyAsAChipOverTheTypedText() {
        val state = SearchFieldState("from:$NPUB rest")
        setField(state)
        assertEquals("from:Alice rest", drawn())
        assertEquals("from:$NPUB rest", state.text)
    }

    @Test
    fun aPhraseSettlesIntoAChipOnceTheCaretLeavesIt() {
        val state = SearchFieldState()
        setField(state)
        val field = compose.onNode(hasSetTextAction())
        field.performClick()
        field.performTextInput("\"hello world\"")
        // Still under the caret: drawn exactly as typed.
        assertEquals("\"hello world\"", drawn())
        field.performTextInput(" x")
        assertEquals("hello world x", drawn())
        assertEquals("\"hello world\" x", state.text)
    }

    @Test
    fun enterPicksTheHighlightedKindIntoTheTypedText() {
        val state = SearchFieldState()
        setField(state)
        val field = compose.onNode(hasSetTextAction())
        field.performClick()
        field.performTextInput("kind:pictur")
        compose.onNodeWithText("picture", substring = true, useUnmergedTree = true).assertExists()
        field.performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()
        assertEquals("kind:picture", state.text.trimEnd())
    }

    @Test
    fun aMouseClickOnAPickerRowPicksIt() {
        // A mouse press asks for focus. If the row takes it, the field blurs, the picker closes
        // under the pointer and the release lands on nothing. Touch input never asked, which is
        // why performClick() alone would not catch this.
        val state = SearchFieldState()
        setField(state)
        val field = compose.onNode(hasSetTextAction())
        field.performClick()
        field.performTextInput("kind:pictur")
        compose.onNodeWithText("picture", useUnmergedTree = true).performMouseInput { click() }
        compose.waitForIdle()
        assertEquals("kind:picture", state.text.trimEnd())
    }

    @Test
    fun endLandsPastTheSpaceAPickLeaves() {
        // A pick always leaves one trailing space. Compose's End stops at the line's visible
        // end, before that space, so the next token typed after End was glued to the chip.
        val state = SearchFieldState("from:$NPUB ")
        setField(state)
        val field = compose.onNode(hasSetTextAction())
        field.performMouseInput { click() }
        state.textState.edit { selection = TextRange(0) }
        field.performKeyInput { pressKey(Key.MoveEnd) }
        compose.waitForIdle()
        field.performTextInput("kind:pic")
        assertEquals("from:$NPUB kind:pic", state.text)
    }

    @Test
    fun shiftEndSelectsThroughTheTrailingSpace() {
        val state = SearchFieldState("hello world ")
        setField(state)
        val field = compose.onNode(hasSetTextAction())
        field.performMouseInput { click() }
        state.textState.edit { selection = TextRange(6) }
        field.performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.MoveEnd) } }
        compose.waitForIdle()
        assertEquals(TextRange(6, 12), state.textState.selection)
    }

    @Test
    fun mouseClicksInTheCalendarKeepItOpenAndPick() {
        val state = SearchFieldState()
        setField(state)
        val field = compose.onNode(hasSetTextAction())
        field.performClick()
        field.performTextInput("since:")
        // Paging the month must leave the calendar up: it only exists under a focused field.
        compose.onNodeWithContentDescription("Next month", useUnmergedTree = true).performMouseInput { click() }
        compose.onNodeWithText("15", useUnmergedTree = true).performMouseInput { click() }
        compose.waitForIdle()
        assertTrue(Regex("since:\\d{4}-\\d{2}-15").matches(state.text.trimEnd()), state.text)

        field.performTextInput("until:")
        compose.onNodeWithText("Today", useUnmergedTree = true).performMouseInput { click() }
        compose.waitForIdle()
        assertTrue(Regex("since:\\S+ until:\\d{4}-\\d{2}-\\d{2}").matches(state.text.trimEnd()), state.text)
    }
}
