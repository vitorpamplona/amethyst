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
package com.vitorpamplona.quartz.buzz.forum

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ForumHintProviderTest {
    private val author = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val other = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val root = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val parent = "a7a12e7bbd4b4c2ae6cd5ae1c8a5ab8a4e7f0f53bd21cd6f4b51f2cc1f6fe0e2"
    private val relay = "wss://relay.damus.io/"
    private val channel = "6f1a8a3c-9f1e-4d2b-8c1a-2b3c4d5e6f70"
    private val id = "00".repeat(32)
    private val sig = "00".repeat(64)

    @Test
    fun commentLinksRootParentAndMentions() {
        val template = ForumCommentEvent.build(channel, "agreed", root, parent, listOf(other))
        val event = ForumCommentEvent(id, author, template.createdAt, template.tags + arrayOf(arrayOf("p", author, relay)), template.content, sig)

        assertEquals(listOf(root, parent), event.linkedEventIds())
        // Buzz writes an empty relay slot on thread markers: no hint to report.
        assertTrue(event.eventHints().isEmpty())
        assertEquals(listOf(other, author), event.linkedPubKeys())
        assertEquals(listOf(author), event.pubKeyHints().map { it.pubkey })
        assertEquals(listOf(relay), event.pubKeyHints().map { it.relay.url })
    }

    @Test
    fun postLinksMentions() {
        val template = ForumPostEvent.build(channel, "kickoff", listOf(other, author))
        val event = ForumPostEvent(id, author, template.createdAt, template.tags, template.content, sig)

        assertEquals(listOf(other, author), event.linkedPubKeys())
        assertTrue(event.pubKeyHints().isEmpty())
    }

    @Test
    fun voteLinksItsTarget() {
        val event = ForumVoteEvent(id, author, 1, arrayOf(arrayOf("h", channel), arrayOf("e", root, relay)), "+", sig)

        assertEquals(listOf(root), event.linkedEventIds())
        assertEquals(listOf(root), event.eventHints().map { it.eventId })
    }
}
