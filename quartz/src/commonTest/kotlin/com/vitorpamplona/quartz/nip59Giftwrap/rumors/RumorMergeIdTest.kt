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
package com.vitorpamplona.quartz.nip59Giftwrap.rumors

import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class RumorMergeIdTest {
    private val author = "7".repeat(64)
    private val other = "8".repeat(64)
    private val tags = arrayOf(arrayOf("p", other))

    private fun seal(sealTags: Array<Array<String>> = emptyArray()) = SealEvent("a".repeat(64), author, 1_700_000_000, sealTags, "", "")

    private fun honestId() = EventHasher.hashId(author, 1_700_000_100, 14, tags, "hi")

    @Test
    fun forgedIdIsReplacedByTheComputedOne() {
        val publicNoteId = "f".repeat(64)
        val merged = Rumor(publicNoteId, author, 1_700_000_100, 14, tags, "hi").mergeWith(seal())

        assertEquals(honestId(), merged.id)
    }

    @Test
    fun honestIdSurvivesASealWithAnExpiration() {
        val merged = Rumor(honestId(), author, 1_700_000_100, 14, tags, "hi").mergeWith(seal(arrayOf(arrayOf("expiration", "1800000000"))))

        assertEquals(honestId(), merged.id)
    }

    @Test
    fun missingIdIsComputed() {
        val merged = Rumor(null, author, 1_700_000_100, 14, tags, "hi").mergeWith(seal())

        assertEquals(honestId(), merged.id)
    }
}
