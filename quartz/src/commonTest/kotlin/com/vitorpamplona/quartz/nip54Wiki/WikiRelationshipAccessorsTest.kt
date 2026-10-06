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
package com.vitorpamplona.quartz.nip54Wiki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WikiRelationshipAccessorsTest {
    private val a = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val b = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val version = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val quoted = "11".repeat(32)
    private val relay = "wss://relay.damus.io/"
    private val forkedArticle = "30818:$b:hot-ice-creams"
    private val deferredArticle = "30818:$b:ice-cream"
    private val quotedArticle = "30023:$b:essay"
    private val id = "00".repeat(32)
    private val sig = "00".repeat(64)

    private fun article(vararg tags: Array<String>) = WikiArticleEvent(id, a, 1700000000, arrayOf(arrayOf("d", "hot-ice-creams"), *tags), "", sig)

    @Test
    fun articleExposesMentionsQuotesAndReferences() {
        val event =
            article(
                arrayOf("p", b, relay),
                arrayOf("p", "short"),
                arrayOf("a", forkedArticle, relay, "fork"),
                arrayOf("e", version, relay, "fork"),
                arrayOf("a", deferredArticle, relay, "defer"),
                arrayOf("a", "garbage"),
                arrayOf("q", quoted, relay),
                arrayOf("q", quotedArticle),
            )

        assertEquals(listOf(b), event.mentions().map { it.pubKey })
        assertEquals(listOf(b), event.mentionKeys())
        assertEquals(listOf(quoted), event.quotedEvents().map { it.eventId })
        assertEquals(listOf(quotedArticle), event.quotedAddresses().map { it.address.toValue() })
        assertEquals(listOf(version), event.referencedEvents())
        assertEquals(listOf(forkedArticle, deferredArticle), event.referencedAddresses())

        assertEquals(listOf(b), event.linkedPubKeys())
        assertEquals(listOf(version, quoted), event.linkedEventIds())
        assertEquals(listOf(forkedArticle, deferredArticle, quotedArticle), event.linkedAddressIds())
    }

    @Test
    fun isAForkReadsTheForkAccessors() {
        assertTrue(article(arrayOf("a", forkedArticle, relay, "fork")).isAFork())
        assertTrue(article(arrayOf("e", version, relay, "fork")).isAFork())
        assertFalse(article(arrayOf("a", deferredArticle, relay, "defer")).isAFork())
        assertFalse(article().isAFork())
    }

    @Test
    fun mergeRequestLinksOnlyItsNamedRelationships() {
        val base = "bb".repeat(32)
        val event =
            WikiMergeRequestEvent(
                id,
                a,
                1700000000,
                arrayOf(
                    arrayOf("a", forkedArticle, relay),
                    arrayOf("p", b),
                    arrayOf("e", base, relay),
                    arrayOf("e", version, relay, "source"),
                ),
                "",
                sig,
            )
        assertEquals(listOf(version, base), event.linkedEventIds())
        assertEquals(listOf(forkedArticle), event.linkedAddressIds())
        assertEquals(listOf(b), event.linkedPubKeys())
    }

    @Test
    fun mergeRequestIgnoresMalformedVersionIds() {
        val event =
            WikiMergeRequestEvent(id, a, 1700000000, arrayOf(arrayOf("e", "short", "", "source"), arrayOf("e", "also-short")), "", sig)
        assertNull(event.mergeSource())
        assertNull(event.baseVersion())
        assertTrue(event.linkedEventIds().isEmpty())
    }

    @Test
    fun mergeAcceptanceLinksResultRequestAndRequester() {
        val request = "aa".repeat(32)
        val event =
            WikiMergeAcceptanceEvent(
                id,
                a,
                1700000000,
                arrayOf(arrayOf("e", version, "", "result"), arrayOf("e", request, "", "request"), arrayOf("e", "short", "", "result"), arrayOf("p", b)),
                "",
                sig,
            )
        assertEquals(version, event.result())
        assertEquals(listOf(version, request), event.linkedEventIds())
        assertEquals(listOf(b), event.linkedPubKeys())
        assertNull(WikiMergeAcceptanceEvent(id, a, 1700000000, arrayOf(arrayOf("e", "short", "", "request")), "", sig).request())
    }

    @Test
    fun redirectLinksItsTarget() {
        val event = WikiRedirectEvent(id, a, 1700000000, arrayOf(arrayOf("d", "ice-cream"), arrayOf("a", forkedArticle, relay)), "", sig)
        assertEquals(listOf(forkedArticle), event.linkedAddressIds())
    }
}
