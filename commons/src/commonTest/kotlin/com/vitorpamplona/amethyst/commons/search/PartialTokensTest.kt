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
package com.vitorpamplona.amethyst.commons.search

import com.vitorpamplona.amethyst.commons.search.calendar.DateField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PartialTokensTest {
    private companion object {
        const val NPUB = "npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6"
    }

    private fun pickerAtEnd(text: String) = PartialTokens.activePicker(text, text.length)

    @Test
    fun aHalfWrittenFromOpensThePeoplePicker() {
        val picker = pickerAtEnd("zaps from:ali")
        assertTrue(picker is ActivePicker.People)
        assertEquals(KeyField.FROM, (picker as ActivePicker.People).keyField)
        assertEquals("ali", picker.token.partial)
        assertEquals(5, picker.token.start)
    }

    @Test
    fun aBarePrefixOpensThePickerWithNothingTypedYet() {
        val picker = pickerAtEnd("to:")
        assertTrue(picker is ActivePicker.People)
        assertEquals("", picker.token.partial)
    }

    @Test
    fun aFinishedKeyClosesThePicker() {
        assertNull(pickerAtEnd("from:$NPUB"))
    }

    @Test
    fun aToThatHasBegunAPointerNamesNoPerson() {
        assertNull(pickerAtEnd("to:note1abc"))
        assertNull(pickerAtEnd("to:naddr1"))
    }

    @Test
    fun aHalfWrittenDateOpensTheCalendar() {
        val picker = pickerAtEnd("since:2026-0")
        assertTrue(picker is ActivePicker.Calendar)
        assertEquals(DateField.SINCE, (picker as ActivePicker.Calendar).dateField)
    }

    @Test
    fun aFinishedDayClosesTheCalendar() {
        assertNull(pickerAtEnd("until:2026-04-01"))
    }

    @Test
    fun aGroupTokenStaysOpenBecauseAnyPrefixIsAPlausibleId() {
        // `group:gen` is both a plausible id and a prefix of `general`, so only a space ends it.
        assertTrue(pickerAtEnd("group:gen") is ActivePicker.Group)
        assertTrue(pickerAtEnd("group:general") is ActivePicker.Group)
        assertNull(pickerAtEnd("group:general "))
    }

    @Test
    fun aCaretInsideAWordNamesNobodysToken() {
        // The caret sits before "li", so this is editing text, not writing a token.
        assertNull(PartialTokens.activePicker("from:ali", 6))
    }

    @Test
    fun aCaretBeforeWhitespaceStillNamesItsToken() {
        assertTrue(PartialTokens.activePicker("from:ali next", 8) is ActivePicker.People)
    }

    @Test
    fun aPickSplicesOverThePartialAndLeavesOneSpace() {
        val token = PartialTokens.mentionAt("zaps from:ali", 13)!!
        val (text, caret) = PartialTokens.replaceToken("zaps from:ali", token, "from:$NPUB")
        assertEquals("zaps from:$NPUB ", text)
        assertEquals(text.length, caret)
    }

    @Test
    fun aPickDoesNotDoubleTheSpaceThatIsAlreadyThere() {
        val text = "zaps from:ali more"
        val token = PartialTokens.mentionAt(text, 13)!!
        val (next, caret) = PartialTokens.replaceToken(text, token, "from:$NPUB")
        assertEquals("zaps from:$NPUB more", next)
        // The caret lands past the token and its separator, ready for the next word.
        assertEquals("zaps from:$NPUB ".length, caret)
    }

    @Test
    fun aPickedTokenReadsBackAsTheFilterItPromised() {
        val token = PartialTokens.mentionAt("from:ali", 8)!!
        val (text, _) = PartialTokens.replaceToken("from:ali", token, "from:$NPUB")
        assertEquals(1, QueryParser.parse(text).authors.size)
    }

    @Test
    fun onlyAnNpubCountsAsAFinishedKey() {
        assertTrue(PartialTokens.isKey(NPUB))
        // Hex stays unfinished, so the picker resolves it and writes the npub back in its place.
        assertTrue(!PartialTokens.isKey("a".repeat(64)))
        assertTrue(!PartialTokens.isKey("npub1notarealkey"))
    }

    // ---- the kind picker ------------------------------------------------------------------

    @Test
    fun aBareKindOpensThePickerOnTheWholeVocabulary() {
        val picker = PartialTokens.activePicker("kind:", 5)
        assertTrue(picker is ActivePicker.Kind)
        assertEquals("", picker.token.partial)
        // A reader who typed `kind:` and stopped is asking what the options are.
        assertEquals(KindRegistry.aliases.size + KindRegistry.pseudoKinds.size, KindRegistry.candidates("").size)
    }

    @Test
    fun aHalfWrittenKindOffersOnlyWhatItCouldBecome() {
        val picker = PartialTokens.activePicker("kind:art", 8)
        assertTrue(picker is ActivePicker.Kind)
        assertEquals(listOf("article"), KindRegistry.candidates(picker.token.partial).map { it.alias })
    }

    @Test
    fun aFinishedKindClosesThePicker() {
        // The vocabulary is closed, so an exact match really is finished — unlike a group id,
        // where `gen` is both plausible and a prefix of `general`.
        assertNull(PartialTokens.activePicker("kind:article", 12))
        assertNull(PartialTokens.activePicker("kind:media", 10))
    }

    @Test
    fun theKindPickerSaysWhatEachRowWillActuallyAskFor() {
        val video = KindRegistry.candidates("video").single()
        assertEquals(listOf(21, 22, 34235, 34236), video.kinds)
        assertFalse(video.isPseudo)
        // A pseudo-kind asks a relay for no kinds at all; it filters what came back.
        assertTrue(KindRegistry.candidates("media").single().isPseudo)
    }

    @Test
    fun theKindPickerIsCaseInsensitiveAndPrefixOnly() {
        assertEquals(listOf("article"), KindRegistry.candidates("ART").map { it.alias })
        // Not a substring match: `ideo` must not find `video`, or the list would be noise.
        assertTrue(KindRegistry.candidates("ideo").isEmpty())
    }

    @Test
    fun aKindTokenTheCaretIsNotAtTheEndOfOpensNothing() {
        // Standing in the middle of a finished token is editing it, not writing it.
        assertNull(PartialTokens.activePicker("kind:article extra", 8))
    }

    // ---- tapping a finished chip -----------------------------------------------------------

    @Test
    fun theCaretOnAFinishedChipNamesThatChip() {
        val text = "bitcoin kind:article rest"
        val token = PartialTokens.tokenAt(text, 12)
        assertTrue(token?.segment is SearchSegment.Kind)
        assertEquals(8, token.start)
        assertEquals(20, token.end)
        assertEquals("kind:article", text.substring(token.start, token.end))
    }

    @Test
    fun theCaretOnPlainTextNamesNoChip() {
        assertNull(PartialTokens.tokenAt("bitcoin kind:article rest", 2))
        assertNull(PartialTokens.tokenAt("bitcoin kind:article rest", 23))
        assertNull(PartialTokens.tokenAt("", 0))
    }

    @Test
    fun everyOffsetOfEveryChipResolvesToThatChip() {
        // A tap can land anywhere in a chip, including either edge, and must always name it.
        val text = "#bitcoin"
        (0..text.length).forEach { at ->
            assertTrue(PartialTokens.tokenAt(text, at)?.segment is SearchSegment.Hashtag, "offset $at")
        }
    }

    @Test
    fun aChipWithAPickerReopensOnItsPrefix() {
        val text = "kind:article"
        assertEquals("kind:", PartialTokens.tokenAt(text, 4)?.editPrefix)
        assertEquals("from:", PartialTokens.tokenAt("from:$NPUB", 3)?.editPrefix)
        assertEquals("since:", PartialTokens.tokenAt("since:2026-01-01", 3)?.editPrefix)
        assertEquals("group:", PartialTokens.tokenAt("group:dev", 3)?.editPrefix)
        assertEquals("geo:", PartialTokens.tokenAt("geo:9q8yy", 2)?.editPrefix)
    }

    @Test
    fun aChipWithNoPickerHasNoPrefixAndIsChangedByRetyping() {
        assertNull(PartialTokens.tokenAt("#bitcoin", 3)?.editPrefix)
        assertNull(PartialTokens.tokenAt("-scam", 3)?.editPrefix)
        assertNull(PartialTokens.tokenAt("\"a phrase\"", 3)?.editPrefix)
    }

    @Test
    fun aBareKeyIsNotChangedThroughAPrefixItNeverHad() {
        // `npub1…` on its own is a search term, not a `from:`; it has no prefix to reopen.
        assertNull(PartialTokens.tokenAt(NPUB, 3)?.editPrefix)
    }
}
