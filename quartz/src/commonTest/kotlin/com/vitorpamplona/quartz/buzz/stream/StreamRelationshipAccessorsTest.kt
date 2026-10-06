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
package com.vitorpamplona.quartz.buzz.stream

import com.vitorpamplona.quartz.buzz.stream.tags.ActorTag
import com.vitorpamplona.quartz.buzz.stream.tags.ExpectedRevisionTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamRelationshipAccessorsTest {
    private val author = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val member = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val root = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val reply = "a7a12e7bbd4b4c2ae6cd5ae1c8a5ab8a4e7f0f53bd21cd6f4b51f2cc1f6fe0e2"
    private val channel = "6f1a8a3c-9f1e-4d2b-8c1a-2b3c4d5e6f70"
    private val id = "00".repeat(32)
    private val sig = "00".repeat(64)

    @Test
    fun editMentionsAreItsValidPTagsInOrder() {
        val event =
            StreamMessageEditEvent(
                id,
                author,
                1,
                arrayOf(arrayOf("h", channel), arrayOf("e", root), arrayOf("p", member), arrayOf("p", "short"), arrayOf("p", author)),
                "fixed",
                sig,
            )
        assertEquals(listOf(member, author), event.mentions())
        assertEquals(event.mentions(), event.linkedPubKeys())
        assertTrue(StreamMessageEditEvent(id, author, 1, arrayOf(arrayOf("h", channel)), "", sig).mentions().isEmpty())
    }

    @Test
    fun v2ThreadRootAndReplyReadTheMarkedETags() {
        val event =
            StreamMessageV2Event(
                id,
                author,
                1,
                arrayOf(arrayOf("h", channel), arrayOf("e", root, "", "root"), arrayOf("e", reply, "", "reply")),
                "hi",
                sig,
            )
        assertEquals(root, event.threadRoot())
        assertEquals(reply, event.replyTo())
        assertEquals(listOf(root, reply), event.linkedEventIds())
    }

    @Test
    fun v2ThreadMarkersFollowTheRelayRules() {
        // A 3-element ["e", id, "reply"] is not a thread link, an unmarked e is not one either,
        // and a malformed id is ignored — Buzz's relay reads them the same way.
        val event =
            StreamMessageV2Event(
                id,
                author,
                1,
                arrayOf(arrayOf("e", reply, "reply"), arrayOf("e", root), arrayOf("e", "nothex", "", "root")),
                "hi",
                sig,
            )
        assertNull(event.threadRoot())
        assertNull(event.replyTo())
        assertTrue(event.linkedEventIds().isEmpty())
    }

    @Test
    fun canvasExpectedRevisionIdSkipsTheNoneSentinel() {
        val withHead = CanvasEvent(id, author, 1, arrayOf(arrayOf("h", channel), arrayOf("expected-revision", root)), "# doc", sig)
        assertEquals(root, withHead.expectedRevisionId())

        val fresh = CanvasEvent(id, author, 1, arrayOf(arrayOf("h", channel), arrayOf("expected-revision", "none")), "# doc", sig)
        assertEquals(ExpectedRevisionTag.NONE, fresh.expectedRevision())
        assertNull(fresh.expectedRevisionId())

        assertNull(CanvasEvent(id, author, 1, arrayOf(arrayOf("h", channel), arrayOf("expected-revision", "xyz")), "", sig).expectedRevisionId())
        assertNull(CanvasEvent(id, author, 1, arrayOf(arrayOf("h", channel)), "", sig).expectedRevisionId())
    }

    @Test
    fun onBehalfOfPrefersTheActorTag() {
        val tags = arrayOf(arrayOf("h", channel), arrayOf("p", author), arrayOf(ActorTag.TAG_NAME, member))
        assertEquals(member, tags.buzzOnBehalfOf())
    }

    @Test
    fun onBehalfOfFallsBackToTheFirstValidPTagOnlyInAChannel() {
        assertEquals(member, arrayOf(arrayOf("h", channel), arrayOf("p", "short"), arrayOf("p", member), arrayOf("p", author)).buzzOnBehalfOf())
        assertNull(arrayOf(arrayOf("p", member)).buzzOnBehalfOf())
        assertNull(arrayOf(arrayOf("h", channel)).buzzOnBehalfOf())
    }

    @Test
    fun onBehalfOfIgnoresMalformedKeys() {
        assertEquals(author, arrayOf(arrayOf("h", channel), arrayOf("actor", "nothex"), arrayOf("p", author)).buzzOnBehalfOf())
        assertNull(arrayOf(arrayOf("h", channel), arrayOf("actor", "z".repeat(64)), arrayOf("p", "z".repeat(64))).buzzOnBehalfOf())
    }
}
