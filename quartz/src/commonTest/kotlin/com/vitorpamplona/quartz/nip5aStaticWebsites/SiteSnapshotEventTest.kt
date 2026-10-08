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
package com.vitorpamplona.quartz.nip5aStaticWebsites

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.AddressSerializer
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * NIP-5A kind 5128 manifest snapshots. The fixture has the shape of the snapshots seen on relays
 * (nsite.run style: `a` to a root site with an empty `d`, `path`s, `x`, `server`s, `title`) with
 * synthetic keys and hashes.
 */
class SiteSnapshotEventTest {
    private val author = "b".repeat(64)
    private val other = "c".repeat(64)
    private val appAuthor = "d".repeat(64)

    private val paths =
        listOf(
            PathTag("/index.html", "f70253e146d7ab35e1d16150e1a24f1df77554e96998ef1f004156d96fa22354"),
            PathTag("/about.html", "4cbd330c7d1dff6ded6277b69b56c981b9f799993c1ab7555e92b272c27955d4"),
        )

    private fun sample(
        extraTags: Array<Array<String>> = emptyArray(),
        aggregate: String = SiteAggregateHash.compute(paths),
    ): Event =
        EventFactory.create(
            id = "1".repeat(64),
            pubKey = author,
            createdAt = 1_791_427_856L,
            kind = SiteSnapshotEvent.KIND,
            tags =
                arrayOf(
                    arrayOf("a", "15128:$author:"),
                    arrayOf("path", "/index.html", paths[0].hash),
                    arrayOf("path", "/about.html", paths[1].hash),
                    arrayOf("x", aggregate, "aggregate"),
                    arrayOf("server", "https://cdn.example.com"),
                    arrayOf("server", "https://blossom.example.org"),
                    arrayOf("title", "Kettle Site"),
                    arrayOf("description", "A small static site"),
                    arrayOf("source", "https://github.com/example/site"),
                    *extraTags,
                ),
            content = "",
            sig = "00".repeat(64),
        )

    private fun <T : Event> EventTemplate<T>.toEvent(): T = EventFactory.create("2".repeat(64), author, createdAt, kind, tags, content, "00".repeat(64))

    @Test
    fun factoryBuildsSnapshotForKind5128() {
        assertIs<SiteSnapshotEvent>(sample())
        assertTrue(EventFactory.isKnownKind(SiteSnapshotEvent.KIND))
    }

    @Test
    fun parsesManifestFields() {
        val event = assertIs<SiteSnapshotEvent>(sample())

        assertEquals(2, event.paths().size)
        assertEquals("/index.html", event.paths()[0].path)
        assertEquals(listOf("https://cdn.example.com", "https://blossom.example.org"), event.servers())
        assertEquals("Kettle Site", event.title())
        assertEquals("A small static site", event.description())
        assertEquals("https://github.com/example/site", event.source())
        assertEquals(Address(RootSiteEvent.KIND, author, ""), event.snapshotOf())
        assertNull(event.origin())
        assertEquals(emptyList(), event.apps())
        assertEquals(SiteAggregateHash.compute(paths), event.declaredAggregateHash())
        assertTrue(event.verifyAggregate())
    }

    @Test
    fun aMissingOrWrongAggregateHashFailsVerification() {
        val wrong = assertIs<SiteSnapshotEvent>(sample(aggregate = "0".repeat(64)))
        assertFalse(wrong.verifyAggregate())

        val missing =
            assertIs<SiteSnapshotEvent>(
                EventFactory.create<Event>("1".repeat(64), author, 1L, SiteSnapshotEvent.KIND, arrayOf(arrayOf("path", "/index.html", paths[0].hash)), "", ""),
            )
        // Unlike 15128/35128, a snapshot MUST carry `x`: a missing one is not a pass.
        assertNull(missing.declaredAggregateHash())
        assertFalse(missing.verifyAggregate())
    }

    @Test
    fun hintsAndLinksCoverSnapshottedSiteOriginAndApps() {
        val namedOrigin = "35128:$other:blog"
        val app = "31990:$appAuthor:my-app"
        val event =
            assertIs<SiteSnapshotEvent>(
                sample(
                    arrayOf(
                        arrayOf("A", namedOrigin, "wss://relay.example.com"),
                        arrayOf("app", app, "wss://apps.example.com"),
                    ),
                ),
            )

        assertEquals(listOf("15128:$author:", namedOrigin, app), event.linkedAddressIds())
        event.linkedAddressIds().forEach { assertEquals(it, AddressSerializer.parse(it)?.toValue()) }
        assertEquals(Address(35128, other, "blog"), event.origin())
        assertEquals(listOf(Address(31990, appAuthor, "my-app")), event.apps())

        // The `a` here carries no relay, so only `A` and `app` produce hints.
        assertEquals(listOf(namedOrigin, app), event.addressHints().map { it.addressId })
        assertEquals("wss://relay.example.com/", event.addressHints()[0].relay.url)
    }

    @Test
    fun malformedReferencesAreSkippedNotThrown() {
        val event =
            assertIs<SiteSnapshotEvent>(
                EventFactory.create<Event>(
                    "1".repeat(64),
                    author,
                    1L,
                    SiteSnapshotEvent.KIND,
                    arrayOf(
                        arrayOf("a"),
                        arrayOf("A", "not-an-address"),
                        arrayOf("app", "31990:short:d", "wss://relay.example.com"),
                        arrayOf("app"),
                        arrayOf("path", "/only-a-path"),
                        arrayOf("x", "abc"),
                        arrayOf("title"),
                    ),
                    "",
                    "",
                ),
            )

        assertNull(event.snapshotOf())
        assertNull(event.origin())
        assertEquals(emptyList(), event.apps())
        assertEquals(emptyList(), event.linkedAddressIds())
        assertEquals(emptyList(), event.paths())
        assertNull(event.declaredAggregateHash())
        assertNull(event.title())
        assertEquals("", event.indexableContent())
    }

    @Test
    fun indexesTitleAndDescriptionAndTheVisitorAgrees() {
        val event = assertIs<SiteSnapshotEvent>(sample())
        assertEquals("Kettle Site\nA small static site", event.indexableContent())

        val visited = mutableListOf<String>()
        event.forEachIndexableField { field ->
            field?.let { visited.add(it) }
            true
        }
        assertEquals(event.indexableContent(), visited.joinToString((event as SearchableEvent).indexableSeparator()))
    }

    @Test
    fun buildRoundTrips() {
        val origin = Address(35128, other, "blog")
        val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.example.com")!!
        val event =
            SiteSnapshotEvent
                .build(
                    snapshotOf = Address(NamedSiteEvent.KIND, author, "blog"),
                    paths = paths,
                    snapshotOfRelay = relay,
                    origin = origin,
                    servers = listOf("https://cdn.example.com"),
                    title = "Blog v1",
                    description = "An immutable snapshot",
                    createdAt = 1_700_000_000L,
                ).toEvent()

        assertEquals(Address(NamedSiteEvent.KIND, author, "blog"), event.snapshotOf())
        assertEquals(origin, event.origin())
        assertEquals(paths.map { it.path to it.hash }, event.paths().map { it.path to it.hash })
        assertEquals(SiteAggregateHash.compute(paths), event.declaredAggregateHash())
        assertTrue(event.verifyAggregate())
        assertEquals(listOf("https://cdn.example.com"), event.servers())
        assertEquals("Blog v1", event.title())
        assertEquals("An immutable snapshot", event.description())
        assertEquals(
            "wss://relay.example.com/",
            event
                .addressHints()
                .single()
                .relay.url,
        )
    }

    @Test
    fun snapshotOfANamedSiteCopiesWhatNip5aRequires() {
        val declared = "e".repeat(64)
        val site =
            EventFactory.create<NamedSiteEvent>(
                "3".repeat(64),
                author,
                1_700_000_000L,
                NamedSiteEvent.KIND,
                arrayOf(
                    arrayOf("d", "blog"),
                    arrayOf("path", "/index.html", paths[0].hash),
                    arrayOf("path", "/about.html", paths[1].hash),
                    // The snapshot's `x` must equal the source's, even when it is not what the
                    // paths hash to: copied verbatim, never recomputed.
                    arrayOf("x", declared, "aggregate"),
                    arrayOf("A", "35128:$other:blog", "wss://relay.example.com"),
                    arrayOf("app", "31990:$appAuthor:my-app", "wss://apps.example.com"),
                    arrayOf("server", "https://cdn.example.com"),
                    arrayOf("title", "My Blog"),
                ),
                "",
                "",
            )

        val snapshot = SiteSnapshotEvent.snapshotOf(site, createdAt = 1_700_000_100L).toEvent()

        assertEquals(site.address(), snapshot.snapshotOf())
        assertEquals(declared, snapshot.declaredAggregateHash())
        assertEquals(2, snapshot.paths().size)
        assertEquals(1, snapshot.tags.count { it[0] == "A" })
        assertEquals(listOf("A", "35128:$other:blog", "wss://relay.example.com"), snapshot.tags.first { it[0] == "A" }.toList())
        assertEquals(listOf(Address(31990, appAuthor, "my-app")), snapshot.apps())
        assertEquals(listOf("https://cdn.example.com"), snapshot.servers())
        assertEquals("My Blog", snapshot.title())
        assertEquals(1_700_000_100L, snapshot.createdAt)
    }

    @Test
    fun snapshotOfARootSiteWithoutXComputesIt() {
        val site =
            EventFactory.create<RootSiteEvent>(
                "4".repeat(64),
                author,
                1_700_000_000L,
                RootSiteEvent.KIND,
                arrayOf(
                    arrayOf("path", "/index.html", paths[0].hash),
                    arrayOf("path", "/about.html", paths[1].hash),
                ),
                "",
                "",
            )

        val snapshot = SiteSnapshotEvent.snapshotOf(site).toEvent()

        assertEquals(Address(RootSiteEvent.KIND, author, ""), snapshot.snapshotOf())
        assertEquals(SiteAggregateHash.compute(paths), snapshot.declaredAggregateHash())
        assertTrue(snapshot.verifyAggregate())
        assertEquals(0, snapshot.tags.count { it[0] == "A" })
    }
}
