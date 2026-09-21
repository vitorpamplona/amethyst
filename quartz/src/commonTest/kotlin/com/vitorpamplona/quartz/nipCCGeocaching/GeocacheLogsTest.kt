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

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nipCCGeocaching.comment.GeocacheLogComment
import com.vitorpamplona.quartz.nipCCGeocaching.comment.declaredGeocacheLogType
import com.vitorpamplona.quartz.nipCCGeocaching.comment.geocacheLogType
import com.vitorpamplona.quartz.nipCCGeocaching.comment.tags.GeocacheLogType
import com.vitorpamplona.quartz.nipCCGeocaching.foundLog.GeocacheFoundLogEvent
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Kind 7516 found logs, and the kind 1111 comments that carry everything else. */
class GeocacheLogsTest {
    private val owner = "0461fcbecc4c3374439932d6b8f11269ccdb7cc973ad7a50ae362db135a474dd"
    private val finder = "a".repeat(64)
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.example.com")!!

    private val cacheAddress = Address(GeocacheListingEvent.KIND, owner, "first-treasure-1748619568668")

    private val listing =
        GeocacheListingEvent(
            "9".repeat(64),
            owner,
            1_748_619_568L,
            arrayOf(
                arrayOf("d", cacheAddress.dTag),
                arrayOf("name", "First Treasure"),
                arrayOf("g", "u4xsu6ryb"),
                arrayOf("D", "1"),
                arrayOf("T", "1"),
                arrayOf("S", "small"),
            ),
            "",
            "sig",
        )

    private fun Array<Array<String>>.values(name: String) = filter { it.isNotEmpty() && it[0] == name }.map { it[1] }

    @Test
    fun parsesTheSpecsFoundLogExample() {
        val log =
            GeocacheFoundLogEvent(
                "1".repeat(64),
                finder,
                1_748_619_700L,
                arrayOf(arrayOf("a", "37516:$owner:first-treasure-1748619568668")),
                "Found it! Great hiding spot.",
                "sig",
            )

        assertEquals(cacheAddress, log.geocache())
        assertEquals("37516:$owner:first-treasure-1748619568668", log.geocacheId())
        assertEquals(listOf("37516:$owner:first-treasure-1748619568668"), log.linkedAddressIds())
        assertTrue(!log.hasVerificationAttached())
    }

    @Test
    fun aFoundLogBuiltFromAHintCarriesTheRelay() {
        val template = GeocacheFoundLogEvent.build("Found it!", EventHintBundle(listing, relay), createdAt = 1L)

        assertEquals(GeocacheFoundLogEvent.KIND, template.kind)
        assertEquals("Found it!", template.content)
        assertEquals(listOf(cacheAddress.toValue()), template.tags.values("a"))
        assertEquals(listOf(relay.url), template.tags.first { it[0] == "a" }.drop(2))
    }

    @Test
    fun aLogPointingAtSomethingThatIsNotACacheHasNoCache() {
        // The `a` tag is whatever its author wrote. Resolving it unfiltered files a "Found it!"
        // under an unrelated addressable — an article, a calendar event — and hands a caller a
        // note of the wrong type to cast.
        listOf(
            "30023:$owner:an-article",
            "31923:$owner:a-calendar-slot",
            "30000:$owner:a-people-list",
        ).forEach { coordinate ->
            val log =
                GeocacheFoundLogEvent("1".repeat(64), finder, 1L, arrayOf(arrayOf("a", coordinate)), "Found it!", "sig")

            assertNull(log.geocache(), "<$coordinate> should not resolve as a geocache")
            assertNull(log.geocacheId())
        }
    }

    @Test
    fun theUndefinedLegacyListingKindStillResolves() {
        // NIP-CC references 37515 without defining it; a log naming one is still about a cache.
        val log =
            GeocacheFoundLogEvent("1".repeat(64), finder, 1L, arrayOf(arrayOf("a", "37515:$owner:old-cache")), "Found it!", "sig")

        assertEquals("old-cache", log.geocache()?.dTag)
    }

    @Test
    fun theCheapVerificationGateAgreesWithTheParsedOne() {
        // hasVerificationAttached() reads tag names so a feed does not run the JSON parser on
        // every recomposition. It must still answer the same question the parse does for a
        // well-formed payload, and must not claim proof for a tag with an empty value.
        val withProof =
            GeocacheFoundLogEvent(
                "1".repeat(64),
                finder,
                1L,
                arrayOf(arrayOf("a", cacheAddress.toValue()), arrayOf("verification", "{\"anything\":1}")),
                "Found it!",
                "sig",
            )
        val withoutProof =
            GeocacheFoundLogEvent("1".repeat(64), finder, 1L, arrayOf(arrayOf("a", cacheAddress.toValue())), "Found it!", "sig")
        val emptyProof =
            GeocacheFoundLogEvent(
                "1".repeat(64),
                finder,
                1L,
                arrayOf(arrayOf("a", cacheAddress.toValue()), arrayOf("verification", "")),
                "Found it!",
                "sig",
            )

        assertTrue(withProof.hasVerificationAttached())
        assertTrue(!withoutProof.hasVerificationAttached())
        assertTrue(!emptyProof.hasVerificationAttached())
    }

    @Test
    fun aFoundLogIsIndexedByItsMessage() {
        val log = GeocacheFoundLogEvent("1".repeat(64), finder, 1L, emptyArray(), "Great hiding spot", "sig")

        assertEquals("Great hiding spot", log.indexableContent())
    }

    @Test
    fun aDnfCommentCarriesTheRootAndParentTheSpecShows() {
        // NIP-CC's DNF example: the listing is both root and parent, so A/K/P and a/k/p all
        // point at the cache.
        val template = GeocacheLogComment.didNotFind("Searched for 30 minutes.", EventHintBundle(listing), createdAt = 1L)

        assertEquals(CommentEvent.KIND, template.kind)
        assertEquals(listOf(cacheAddress.toValue()), template.tags.values("A"))
        assertEquals(listOf("37516"), template.tags.values("K"))
        assertEquals(listOf(owner), template.tags.values("P"))
        assertEquals(listOf(cacheAddress.toValue()), template.tags.values("a"))
        assertEquals(listOf("37516"), template.tags.values("k"))
        assertEquals(listOf(owner), template.tags.values("p"))
        assertEquals(listOf("dnf"), template.tags.values("t"))
    }

    @Test
    fun theLogTypeReadsBackOffTheComment() {
        val template = GeocacheLogComment.needsMaintenance("Cache is soaked.", EventHintBundle(listing), createdAt = 1L)
        val comment = CommentEvent("1".repeat(64), finder, 1L, template.tags, template.content, "sig")

        assertEquals(GeocacheLogType.MAINTENANCE, comment.tags.geocacheLogType())
    }

    @Test
    fun aCommentWithNoTypeIsANote() {
        // "If no `t` tag is present, the comment is assumed to be a general note."
        val comment = CommentEvent("1".repeat(64), finder, 1L, arrayOf(arrayOf("A", cacheAddress.toValue())), "nice spot", "sig")

        assertEquals(GeocacheLogType.NOTE, comment.tags.geocacheLogType())
        assertNull(comment.tags.declaredGeocacheLogType())
    }

    @Test
    fun aHashtagIsNotMistakenForALogType() {
        // `t` is NIP-01's hashtag tag as well. Only the four defined codes parse, so a topic
        // falls through to the one that follows it.
        val comment =
            CommentEvent(
                "1".repeat(64),
                finder,
                1L,
                arrayOf(arrayOf("t", "geocaching"), arrayOf("t", "hiking"), arrayOf("t", "dnf")),
                "",
                "sig",
            )

        assertEquals(GeocacheLogType.DNF, comment.tags.geocacheLogType())
    }

    @Test
    fun addingALogTypeDoesNotWipeTheAuthorsHashtags() {
        val template =
            GeocacheLogComment.note("nice spot", EventHintBundle(listing), createdAt = 1L) {
                add(arrayOf("t", "geocaching"))
            }

        assertEquals(listOf("note", "geocaching"), template.tags.values("t"))
    }

    @Test
    fun everyDefinedLogTypeRoundTrips() {
        GeocacheLogType.entries.forEach { type ->
            val template = GeocacheLogComment.build("log", EventHintBundle(listing), type, createdAt = 1L)
            val comment = CommentEvent("1".repeat(64), finder, 1L, template.tags, template.content, "sig")

            assertEquals(type, comment.tags.geocacheLogType())
        }
    }

    @Test
    fun anOwnerCanRetireACacheThroughTheCommentVocabularyToo() {
        // NIP-CC lets owners retire a cache with an `archived` log type, keeping its history.
        val template = GeocacheLogComment.build("Retiring this one.", EventHintBundle(listing), GeocacheLogType.ARCHIVED, createdAt = 1L)

        assertEquals(listOf("archived"), template.tags.values("t"))
    }
}
