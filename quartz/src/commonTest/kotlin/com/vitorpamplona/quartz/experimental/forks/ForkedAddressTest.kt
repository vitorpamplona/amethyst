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
package com.vitorpamplona.quartz.experimental.forks

import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.experimental.nipsOnNostr.tags.ForkTag
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiArticleEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ForkedAddressTest {
    private val pk = "1".repeat(64)
    private val community = "34550:$pk:group"

    @Test
    fun aNoteForksFromItsMarkedAddressNotItsCommunity() {
        val origin = "30023:$pk:post"
        val note = TextNoteEvent("0".repeat(64), pk, 1, arrayOf(arrayOf("a", community), arrayOf("a", origin, "", "fork")), "", "0".repeat(128))
        assertEquals(origin, note.forkFromAddress()?.toValue())
    }

    @Test
    fun aWikiArticleFindsTheArticleItWasForkedFrom() {
        val origin = "30818:$pk:bitcoin"
        val article = WikiArticleEvent("0".repeat(64), pk, 1, arrayOf(arrayOf("d", "bitcoin"), arrayOf("a", origin, "", "fork")), "", "0".repeat(128))
        assertEquals(origin, article.forkFromAddress()?.toValue())
    }

    @Test
    fun aNipTextForkTagIsItsOwnKind() {
        val origin = "${NipTextEvent.KIND}:$pk:nip-01"
        assertNotNull(ForkTag.parse(arrayOf("a", origin, "", "fork")))
        assertEquals(origin, ForkTag.parseValidAddress(arrayOf("a", origin, "", "fork")))
    }
}
