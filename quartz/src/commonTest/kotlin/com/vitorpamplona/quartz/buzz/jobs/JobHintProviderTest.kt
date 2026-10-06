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
package com.vitorpamplona.quartz.buzz.jobs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JobHintProviderTest {
    private val author = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val agent = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val request = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val relay = "wss://relay.damus.io/"
    private val channel = "6f1a8a3c-9f1e-4d2b-8c1a-2b3c4d5e6f70"
    private val id = "00".repeat(32)
    private val sig = "00".repeat(64)

    @Test
    fun requestIsSearchableAndLinksTheTargetAgent() {
        val template = JobRequestEvent.build("Summarize yesterday's incidents", channel, agent)
        val event = JobRequestEvent(id, author, template.createdAt, template.tags, template.content, sig)

        assertEquals("Summarize yesterday's incidents", event.indexableContent())
        assertEquals(listOf(agent), event.linkedPubKeys())
        assertTrue(event.pubKeyHints().isEmpty())
    }

    @Test
    fun repliesLinkTheRequestAndTheRequester() {
        val result =
            JobResultEvent(
                id,
                agent,
                1,
                arrayOf(arrayOf("e", request, relay), arrayOf("h", channel), arrayOf("p", author, relay), arrayOf("status", "success")),
                "Three incidents, all resolved",
                sig,
            )
        assertEquals(listOf(request), result.linkedEventIds())
        assertEquals(listOf(relay), result.eventHints().map { it.relay.url })
        assertEquals(listOf(author), result.linkedPubKeys())
        assertEquals(listOf(author), result.pubKeyHints().map { it.pubkey })
        assertEquals("Three incidents, all resolved", result.indexableContent())

        val accepted = JobAcceptedEvent.build(request, channel, author, "on it").let { JobAcceptedEvent(id, agent, it.createdAt, it.tags, it.content, sig) }
        assertEquals(listOf(request), accepted.linkedEventIds())
        assertEquals(listOf(author), accepted.linkedPubKeys())
        assertEquals("on it", accepted.indexableContent())

        val error = JobErrorEvent.build(request, "quota exceeded", channel, author).let { JobErrorEvent(id, agent, it.createdAt, it.tags, it.content, sig) }
        assertEquals(listOf(request), error.linkedEventIds())
        assertEquals("quota exceeded", error.indexableContent())

        val progress = JobProgressEvent.build(request, "halfway there").let { JobProgressEvent(id, agent, it.createdAt, it.tags, it.content, sig) }
        assertEquals(listOf(request), progress.linkedEventIds())
        assertEquals("halfway there", progress.indexableContent())

        val cancel = JobCancelEvent.build(request, "no longer needed").let { JobCancelEvent(id, author, it.createdAt, it.tags, it.content, sig) }
        assertEquals(listOf(request), cancel.linkedEventIds())
        assertEquals("no longer needed", cancel.indexableContent())
    }
}
