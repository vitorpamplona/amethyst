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
package com.vitorpamplona.amethyst.commons.ui.search

import com.vitorpamplona.amethyst.commons.search.PartialTokens
import com.vitorpamplona.amethyst.commons.search.QueryParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Changing and removing a chip the reader tapped.
 *
 * The text is the query, so both operations are splices — and a splice that leaves the whitespace
 * wrong is not cosmetic: the field's text round-trips through the parser on every keystroke, so a
 * doubled space compounds every time a filter is dropped.
 */
class SearchFieldStateTest {
    private companion object {
        const val NPUB = "npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6"
    }

    /** A focused field with the caret placed on [caret], which is what a tap produces. */
    private fun fieldAt(
        text: String,
        caret: Int,
    ): SearchFieldState =
        SearchFieldState(text).also {
            it.onFocusChanged(true)
            it.setText(text, caret)
        }

    private fun tokenAt(
        state: SearchFieldState,
        caret: Int,
    ) = PartialTokens.tokenAt(state.text, caret)

    @Test
    fun removingAChipInTheMiddleLeavesOneSpace() {
        val state = fieldAt("bitcoin kind:article rest", 12)
        state.removeToken(assertNotNull(tokenAt(state, 12)))
        assertEquals("bitcoin rest", state.text)
    }

    @Test
    fun removingTheOnlyChipLeavesAnEmptyField() {
        val state = fieldAt("kind:article", 4)
        state.removeToken(assertNotNull(tokenAt(state, 4)))
        assertEquals("", state.text)
    }

    @Test
    fun removingTheLastChipDoesNotStrandTheSpaceBeforeIt() {
        val state = fieldAt("bitcoin kind:article", 12)
        state.removeToken(assertNotNull(tokenAt(state, 12)))
        assertEquals("bitcoin", state.text)
    }

    @Test
    fun removingASeededChipLeavesTheFieldTrulyEmpty() {
        // A seed arrives with a trailing space so its chip settles; dropping it must not leave
        // that space behind, or the box looks used when it is not.
        val state = fieldAt("kind:article ", 4)
        state.removeToken(assertNotNull(tokenAt(state, 4)))
        assertEquals("", state.text)
    }

    @Test
    fun removingAChipRemovesItsFilterFromTheQuery() {
        val state = fieldAt("from:$NPUB kind:article bitcoin", 3)
        assertEquals(1, QueryParser.parse(state.text).authors.size)
        state.removeToken(assertNotNull(tokenAt(state, 3)))
        val after = QueryParser.parse(state.text)
        assertTrue(after.authors.isEmpty())
        // and leaves everything else exactly as it was
        assertEquals(listOf(30023), after.kinds)
        assertEquals("bitcoin", after.text)
    }

    @Test
    fun changingAChipCutsItBackToItsPrefixSoThePickerOpens() {
        val state = fieldAt("bitcoin kind:article rest", 12)
        state.changeToken(assertNotNull(tokenAt(state, 12)))
        assertEquals("bitcoin kind: rest", state.text)
        // The field is now in exactly the state typing `kind:` and stopping would produce.
        assertNotNull(state.activePicker)
        assertNull(state.editableToken)
    }

    @Test
    fun changingAChipWithNoPickerSelectsItForRetyping() {
        val state = fieldAt("bitcoin #nostr rest", 10)
        state.changeToken(assertNotNull(tokenAt(state, 10)))
        // Text untouched; the token is selected, so the next keystroke replaces it.
        assertEquals("bitcoin #nostr rest", state.text)
        assertEquals(8, state.value.selection.start)
        assertEquals(14, state.value.selection.end)
    }

    @Test
    fun aCaretOnPlainTextOffersNoEditor() {
        val state = fieldAt("bitcoin kind:article", 2)
        assertNull(state.editableToken)
    }

    @Test
    fun aChipUnderTheCaretOffersAnEditor() {
        val state = fieldAt("bitcoin kind:article", 12)
        assertNotNull(state.editableToken)
    }

    @Test
    fun anUnfocusedFieldOffersNoEditor() {
        val state = fieldAt("bitcoin kind:article", 12)
        state.onFocusChanged(false)
        assertNull(state.editableToken)
    }

    @Test
    fun aHalfWrittenTokenBelongsToThePickerNotTheEditor() {
        // Both must never be up at once, or they fight over the space under the field.
        val state = fieldAt("kind:art", 8)
        assertNotNull(state.activePicker)
        assertNull(state.editableToken)
    }
}
