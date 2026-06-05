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
package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.utils.EventFactory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepostRenderabilityTest {
    private val id = "00".repeat(32)
    private val pubKey = "11".repeat(32)
    private val sig = "22".repeat(64)
    private val createdAt = 1_700_000_000L
    private val boostedEventId = "33".repeat(32)

    private fun genericRepost(tags: Array<Array<String>>) = GenericRepostEvent(id, pubKey, createdAt, tags, "", sig)

    private fun repost(tags: Array<Array<String>>) = RepostEvent(id, pubKey, createdAt, tags, "", sig)

    @Test
    fun hidesGenericRepostOfUnknownKind() {
        // kind 16767 (Ditto profile theme) has no Quartz class → cannot render → hide.
        val event = genericRepost(arrayOf(arrayOf("e", boostedEventId), arrayOf("k", "16767")))
        assertFalse(event.isRenderableRepost())
    }

    @Test
    fun showsGenericRepostOfKnownKind() {
        val event = genericRepost(arrayOf(arrayOf("e", boostedEventId), arrayOf("k", "1")))
        assertTrue(event.isRenderableRepost())
    }

    @Test
    fun showsGenericRepostWithoutKindTag() {
        // Conservative: with no `k` tag we cannot prove the inner kind is unknown, so keep it.
        val event = genericRepost(arrayOf(arrayOf("e", boostedEventId)))
        assertTrue(event.isRenderableRepost())
    }

    @Test
    fun hidesKind6RepostOfUnknownKind() {
        val event = repost(arrayOf(arrayOf("e", boostedEventId), arrayOf("k", "16767")))
        assertFalse(event.isRenderableRepost())
    }

    @Test
    fun nonRepostReturnsFalse() {
        val textNote: Event = EventFactory.create(id, pubKey, createdAt, 1, emptyArray(), "", sig)
        assertFalse(textNote.isRenderableRepost())
    }

    @Test
    fun nullReturnsFalse() {
        val nothing: Event? = null
        assertFalse(nothing.isRenderableRepost())
    }
}
