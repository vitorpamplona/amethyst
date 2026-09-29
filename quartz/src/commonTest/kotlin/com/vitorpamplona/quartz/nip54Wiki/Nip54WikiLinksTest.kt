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

import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkTarget
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip54WikiLinksTest {
    private val id = "0".repeat(64)
    private val author = "1".repeat(64)
    private val sig = "0".repeat(128)
    private val destination = "a".repeat(64)
    private val requester = "b".repeat(64)
    private val cited = "c".repeat(64)
    private val base = "2".repeat(64)
    private val source = "3".repeat(64)
    private val result = "4".repeat(64)
    private val request = "5".repeat(64)
    private val article = "30818:$destination:nostr"

    @Test
    fun mergeRequestNamesTheArticleItsBaseAndItsSource() {
        val event =
            WikiMergeRequestEvent(
                id,
                requester,
                1,
                arrayOf(
                    arrayOf("a", article),
                    arrayOf("p", destination),
                    arrayOf("e", base),
                    arrayOf("e", source, "", "source"),
                    arrayOf("e", result, "", "unknown"),
                ),
                "",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.DESTINATION, LinkTarget.Address(article), "a"),
                Link(Relation.DESTINATION_AUTHOR, LinkTarget.User(destination), "p"),
                Link(Relation.BASE_VERSION, LinkTarget.Event(base), "e"),
                Link(Relation.SOURCE, LinkTarget.Event(source), "e"),
            ),
            event.links(),
        )
    }

    @Test
    fun mergeAcceptanceNamesTheResultAndTheRequest() {
        val event =
            WikiMergeAcceptanceEvent(
                id,
                destination,
                1,
                arrayOf(arrayOf("e", result, "", "result"), arrayOf("e", request, "", "request"), arrayOf("p", requester)),
                "",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.RESULT, LinkTarget.Event(result), "e"),
                Link(Relation.REQUEST, LinkTarget.Event(request), "e"),
                Link(Relation.REQUEST_AUTHOR, LinkTarget.User(requester), "p"),
            ),
            event.links(),
        )
    }

    @Test
    fun articleForksDefersAndCites() {
        val deferred = "30818:$cited:nostr"
        val nevent = NEvent.create(request, null, null, null)
        val event =
            WikiArticleEvent(
                id,
                requester,
                1,
                arrayOf(
                    arrayOf("d", "nostr"),
                    arrayOf("a", article, "", "fork"),
                    arrayOf("e", base, "", "fork"),
                    arrayOf("a", deferred, "", "defer"),
                    arrayOf("e", source),
                    arrayOf("p", cited),
                    arrayOf("q", result),
                    arrayOf("t", "Wiki"),
                ),
                "see nostr:$nevent",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.FORK, LinkTarget.Address(article), "a"),
                Link(Relation.FORK, LinkTarget.Event(base), "e"),
                Link(Relation.DEFER, LinkTarget.Address(deferred), "a"),
                Link(Relation.MENTION, LinkTarget.Event(source), "e"),
                Link(Relation.MENTION, LinkTarget.User(cited), "p"),
                Link(Relation.QUOTE, LinkTarget.Event(result), "q"),
                Link(Relation.HASHTAG, LinkTarget.Tag("t", "wiki"), "t"),
                Link(Relation.MENTION, LinkTarget.Event(request), Link.VIA_CONTENT),
            ),
            event.links(),
        )
    }

    @Test
    fun redirectPointsAtItsDestination() {
        val event = WikiRedirectEvent(id, destination, 1, arrayOf(arrayOf("d", "bitcoin"), arrayOf("a", article)), "", sig)
        assertEquals(listOf(Link(Relation.REDIRECT, LinkTarget.Address(article), "a")), event.links())
    }
}
