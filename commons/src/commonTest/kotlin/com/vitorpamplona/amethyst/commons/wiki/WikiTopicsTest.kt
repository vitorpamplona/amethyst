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
package com.vitorpamplona.amethyst.commons.wiki

import com.vitorpamplona.amethyst.commons.model.navigation.Route
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WikiTopicsTest {
    private val author = "a".repeat(64)

    @Test
    fun linksRoundTrip() {
        val uri = WikiTopics.uri("hummingbird", author)
        assertEquals("nostr:wiki?d=hummingbird&p=$author", uri)
        assertTrue(WikiTopics.isTopicUri(uri))
        assertEquals(Route.WikiTopic("hummingbird", author), WikiTopics.route(uri))
        assertEquals(Route.WikiTopic("hummingbird", null), WikiTopics.route(WikiTopics.uri("hummingbird")))
    }

    @Test
    fun slugsAreNormalizedAsNip54Asks() {
        assertEquals(Route.WikiTopic("bee-hummingbird", null), WikiTopics.route("nostr:wiki?d=Bee_Hummingbird"))
    }

    @Test
    fun badPartsAreDropped() {
        assertNull(WikiTopics.route("nostr:wiki?d=%21%21"))
        assertNull(WikiTopics.route("nostr:wiki?d="))
        assertEquals(Route.WikiTopic("x", null), WikiTopics.route("nostr:wiki?d=x&p=not-a-key"))
    }
}
