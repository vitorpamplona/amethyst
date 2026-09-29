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
package com.vitorpamplona.amethyst.commons.napplet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NappletBridgeDocumentsTest {
    private class Proxy(
        val name: String,
    )

    private val docs = NappletBridgeDocuments<Proxy>()
    private val a = Proxy("a.com")
    private val b = Proxy("b.com")

    @Test
    fun replyReachesTheDocumentThatAsked() {
        assertFalse(docs.onMessage(a))
        val id = docs.brokerIdFor("r0")
        val (pageId, proxy) = docs.resolve(id)!!
        assertEquals("r0", pageId)
        assertSame(a, proxy)
    }

    @Test
    fun sameDocumentKeepsItsRequests() {
        docs.onMessage(a)
        val id = docs.brokerIdFor("r0")
        assertFalse(docs.onMessage(a))
        assertSame(a, docs.resolve(id)!!.second)
    }

    @Test
    fun replyForANavigatedAwayDocumentIsDropped() {
        docs.onMessage(a)
        val aRequest = docs.brokerIdFor("r0")
        assertTrue(docs.onMessage(b))
        // b.com numbers its own requests from r0 too; a.com's late reply must not resolve it.
        val bRequest = docs.brokerIdFor("r0")
        assertNull(docs.resolve(aRequest))
        assertSame(b, docs.resolve(bRequest)!!.second)
    }

    @Test
    fun returningToAnEarlierProxyIsStillANewDocument() {
        docs.onMessage(a)
        val first = docs.brokerIdFor("r0")
        docs.onMessage(b)
        docs.onMessage(a)
        assertNull(docs.resolve(first))
    }

    @Test
    fun pageIdsMayContainTheSeparator() {
        docs.onMessage(a)
        assertEquals("fire:7", docs.resolve(docs.brokerIdFor("fire:7"))!!.first)
    }

    @Test
    fun clearedSurfaceResolvesNothing() {
        docs.onMessage(a)
        val id = docs.brokerIdFor("r0")
        docs.clear()
        assertNull(docs.resolve(id))
        assertNull(docs.currentProxy)
    }

    @Test
    fun unstampedIdsAreRejected() {
        docs.onMessage(a)
        assertNull(docs.resolve("r0"))
        assertNull(docs.resolve(":r0"))
    }
}
