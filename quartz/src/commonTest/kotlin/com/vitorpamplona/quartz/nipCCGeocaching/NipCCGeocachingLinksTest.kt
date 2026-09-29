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
package com.vitorpamplona.quartz.nipCCGeocaching

import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkTarget
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip01Core.links.props.FoundProps
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nipCCGeocaching.curation.GeocacheCurationListEvent
import com.vitorpamplona.quartz.nipCCGeocaching.foundLog.GeocacheFoundLogEvent
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent
import com.vitorpamplona.quartz.nipCCGeocaching.verification.GeocacheVerificationEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class NipCCGeocachingLinksTest {
    private val id = "0".repeat(64)
    private val author = "1".repeat(64)
    private val sig = "0".repeat(128)
    private val owner = "a".repeat(64)
    private val finder = "b".repeat(64)
    private val verifier = "c".repeat(64)
    private val cache = "37516:$owner:cache"

    @Test
    fun foundLogNamesTheCache() {
        val event = GeocacheFoundLogEvent(id, finder, 1, arrayOf(arrayOf("a", cache, "wss://relay.example/")), "Found it!", sig)
        assertEquals(listOf(Link(Relation.FOUND, LinkTarget.Address(cache), "a", FoundProps(verified = false))), event.links())
    }

    @Test
    fun verificationSplitsItsCompositeTag() {
        val naddr = NAddress.create(37516, owner, "cache", null)
        val event = GeocacheVerificationEvent(id, verifier, 1, arrayOf(arrayOf("a", "$finder:$naddr")), "", sig)
        assertEquals(
            listOf(
                Link(Relation.FINDER, LinkTarget.User(finder), "a"),
                Link(Relation.VERIFIED, LinkTarget.Address(cache), "a"),
            ),
            event.links(),
        )
    }

    @Test
    fun listingNamesItsWinnerAndVerificationKey() {
        val event =
            GeocacheListingEvent(
                id,
                owner,
                1,
                arrayOf(
                    arrayOf("d", "cache"),
                    arrayOf("n", "first-to-find"),
                    arrayOf("F", finder),
                    arrayOf("verification", verifier),
                    arrayOf("t", "traditional"),
                    arrayOf("g", "u4pruy"),
                    arrayOf("r", "wss://relay.example/"),
                ),
                "",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.WINNER, LinkTarget.User(finder), "F"),
                Link(Relation.VERIFIER, LinkTarget.User(verifier), "verification"),
                Link(Relation.HASHTAG, LinkTarget.Tag("t", "traditional"), "t"),
                Link(Relation.TAG, LinkTarget.Tag("g", "u4pruy"), "g"),
            ),
            event.links(),
        )
    }

    @Test
    fun curationListCuratesOnlyCaches() {
        val event =
            GeocacheCurationListEvent(
                id,
                owner,
                1,
                arrayOf(arrayOf("d", "tour"), arrayOf("a", cache), arrayOf("a", "30023:$owner:post"), arrayOf("g", "u4pr")),
                "",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.CURATED, LinkTarget.Address(cache), "a"),
                Link(Relation.TAG, LinkTarget.Tag("g", "u4pr"), "g"),
            ),
            event.links(),
        )
    }
}
