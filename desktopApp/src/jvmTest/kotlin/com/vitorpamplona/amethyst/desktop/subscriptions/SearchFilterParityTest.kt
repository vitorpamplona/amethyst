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
package com.vitorpamplona.amethyst.desktop.subscriptions

import com.vitorpamplona.amethyst.commons.relayClient.search.searchPostsByText
import com.vitorpamplona.amethyst.commons.search.QueryParser
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one test that would have caught all four of them.
 *
 * The same query, asked by both front ends, must reach a relay as the same REQ. This is the only
 * place in the repo where both paths can be called from — `desktopApp` sees `commons`, and
 * `commons` is where Android's subscription builder lives — so it is the only place the claim can
 * be checked rather than asserted in a comment.
 *
 * Every bug this refactor started from was a violation of it: a kind window that had drifted on
 * one side, a `kind:` chip that reached the REQ on one platform and not the other, a hashtag
 * fan-out that differed by an arm. They were invisible because nothing ever put the two answers
 * side by side.
 */
class SearchFilterParityTest {
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.example.com")!!

    private fun androidAsks(text: String) = searchPostsByText(text, relay).map { it.filter }

    private fun desktopAsks(text: String) = SearchFilterFactory.createFilters(QueryParser.parse(text))

    private fun assertSameREQ(text: String) {
        val android = androidAsks(text)
        val desktop = desktopAsks(text)
        assertEquals("filter count differs for \"$text\"", desktop.size, android.size)
        android.zip(desktop).forEachIndexed { i, (a, d) ->
            assertEquals("kinds differ at arm $i of \"$text\"", d.kinds, a.kinds)
            assertEquals("authors differ at arm $i of \"$text\"", d.authors, a.authors)
            assertEquals("tags differ at arm $i of \"$text\"", d.tags, a.tags)
            assertEquals("search differs at arm $i of \"$text\"", d.search, a.search)
            assertEquals("since differs at arm $i of \"$text\"", d.since, a.since)
            assertEquals("until differs at arm $i of \"$text\"", d.until, a.until)
        }
    }

    @Test
    fun bothFrontEndsAskTheSameThing() {
        listOf(
            "bitcoin",
            "kind:article bitcoin",
            "kind:picture",
            "kind:video nostr",
            "#nostr",
            "#nostr bitcoin",
            "from:npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6 bitcoin",
            "to:npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6 hello",
            "geo:9q8yy coffee",
            "bitcoin -scam",
            "kind:reply bitcoin",
            "lang:en bitcoin",
            "domain:example.com bitcoin",
            "\"exact phrase\"",
            "bitcoin since:2024-01-01 until:2024-12-31",
            "from:npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6",
        ).forEach(::assertSameREQ)
    }

    @Test
    fun aQueryNamingItsOwnKindCollapsesToOneArmOnBothSides() {
        // The fan-out exists for the default window; a named kind is one group, and a platform
        // that kept fanning out would be asking for kinds the chip on screen excluded.
        assertTrue(androidAsks("kind:article bitcoin").all { it.kinds == listOf(30023) })
        assertTrue(desktopAsks("kind:article bitcoin").all { it.kinds == listOf(30023) })
    }

    @Test
    fun neitherSideAsksAnythingForAQueryThatSaysNothing() {
        // A bare `kind:` is not one of these on a technicality: "every recent article" is an
        // unbounded REQ. A bare `from:` is bounded by its author, so it is a real search and both
        // sides do ask for it — which is why it is in the parity list above rather than here.
        listOf("", "   ", "kind:article").forEach {
            assertEquals("android asked for \"$it\"", emptyList<Any>(), androidAsks(it))
            assertEquals("desktop asked for \"$it\"", emptyList<Any>(), desktopAsks(it))
        }
    }
}
