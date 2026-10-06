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
package com.vitorpamplona.quartz.buzz.presence

import com.vitorpamplona.quartz.buzz.presence.typing.typingThreadReply
import com.vitorpamplona.quartz.buzz.presence.typing.typingThreadRoot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PresenceRelationshipAccessorsTest {
    private val author = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val subject = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val root = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val reply = "a7a12e7bbd4b4c2ae6cd5ae1c8a5ab8a4e7f0f53bd21cd6f4b51f2cc1f6fe0e2"
    private val id = "00".repeat(32)
    private val sig = "00".repeat(64)

    @Test
    fun presenceSubjectIsTheFirstValidPTag() {
        assertEquals(subject, arrayOf(arrayOf("p", "short"), arrayOf("p", subject)).presenceSubject())
        assertNull(arrayOf(arrayOf("status", "online")).presenceSubject())
    }

    @Test
    fun relayFormReportsItsPTagSubject() {
        val event = PresenceUpdateEvent(id, author, 1, arrayOf(arrayOf("status", "online"), arrayOf("p", subject)), "online", sig)
        assertEquals(subject, event.subjectPubKey())
        assertEquals(listOf(subject), event.linkedPubKeys())
    }

    @Test
    fun clientFormAndMalformedPTagFallBackToTheAuthor() {
        val own = PresenceUpdateEvent(id, author, 1, arrayOf(arrayOf("status", "away")), "away", sig)
        assertEquals(author, own.subjectPubKey())
        assertTrue(own.linkedPubKeys().isEmpty())

        val malformed = PresenceUpdateEvent(id, author, 1, arrayOf(arrayOf("p", "not-a-key")), "online", sig)
        assertEquals(author, malformed.subjectPubKey())
        assertTrue(malformed.linkedPubKeys().isEmpty())
    }

    @Test
    fun typingThreadMarkersRequireAWellFormedId() {
        val tags = arrayOf(arrayOf("e", "nothex", "", "root"), arrayOf("e", root, "", "root"), arrayOf("e", "x".repeat(64), "", "reply"))
        assertEquals(root, tags.typingThreadRoot())
        assertNull(tags.typingThreadReply())

        val event = TypingIndicatorEvent(id, author, 1, arrayOf(arrayOf("h", "c"), arrayOf("e", root, "", "root"), arrayOf("e", reply, "", "reply")), "", sig)
        assertEquals(listOf(root, reply), event.linkedEventIds())
    }
}
