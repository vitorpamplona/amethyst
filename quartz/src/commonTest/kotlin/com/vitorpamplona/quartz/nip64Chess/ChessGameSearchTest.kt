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
package com.vitorpamplona.quartz.nip64Chess

import com.vitorpamplona.quartz.nip50Search.IndexableFields
import com.vitorpamplona.quartz.nip50Search.SearchFieldExtractor
import com.vitorpamplona.quartz.nip64Chess.game.ChessGameEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class ChessGameSearchTest {
    private val pgn =
        """
        [Event "Casual Game"]
        [Site "Berlin GER"]
        [Date "1852.??.??"]
        [Round "?"]
        [White "Adolf Anderssen"]
        [Black "Jean Dufresne"]
        [Result "1-0"]
        [ECO "C52"]
        [Annotator "?"]

        1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 4. b4 {The Evans Gambit} Bxb4
        5. c3 Ba5 {A natural
        retreat} 6. d4 exd4 1-0
        """.trimIndent()

    private fun game(content: String) = ChessGameEvent("00".repeat(32), "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c", 1, emptyArray(), content, "00".repeat(64))

    @Test
    fun onlyTheNaturalLanguagePartsOfAPgnAreExtracted() {
        val fields = PgnSearchText.extract(pgn)

        // Date/Round/Result/ECO are machine values; `?` placeholders are dropped
        assertEquals(listOf("Casual Game", "Berlin GER", "Adolf Anderssen", "Jean Dufresne"), fields.headers)
        assertEquals(listOf("The Evans Gambit", "A natural retreat"), fields.comments)
    }

    @Test
    fun aGameIndexesItsPlayersAndCommentsNeverItsMoves() {
        val event = game(pgn)
        val expected = "Casual Game\nBerlin GER\nAdolf Anderssen\nJean Dufresne\nThe Evans Gambit\nA natural retreat"
        assertEquals(expected, event.indexableContent())

        val visited = mutableListOf<String>()
        event.forEachIndexableField { field ->
            field?.let { visited.add(it) }
            true
        }
        assertEquals(expected, visited.joinToString(event.indexableSeparator()))

        val tiered = SearchFieldExtractor.extract(event) as IndexableFields.Tiered
        assertEquals(listOf("Casual Game", "Berlin GER", "Adolf Anderssen", "Jean Dufresne"), tiered.primary)
        assertEquals("The Evans Gambit\nA natural retreat", tiered.text)
    }

    @Test
    fun garbageAndEmptyPgnIndexNothing() {
        assertEquals("", game("").indexableContent())
        assertEquals("", game("1. e4 e5 {unterminated").indexableContent())
        assertEquals("", game("[White]\n[Black \"\"]").indexableContent())
    }
}
