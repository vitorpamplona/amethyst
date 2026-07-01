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
package com.vitorpamplona.quartz.nip5dNapplets

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NappletEventTest {
    private val zero = "00".repeat(32)
    private val h1 = "11".repeat(32)
    private val h2 = "22".repeat(32)
    private val paths = listOf(PathTag("/index.html", h1), PathTag("/app.js", h2))
    private val servers = listOf("https://cdn.example.com")
    private val requires = listOf("relay", "identity")

    private fun <T : Event> materialize(
        template: EventTemplate<T>,
        factory: (String, String, Long, Array<Array<String>>, String, String) -> T,
    ): T = factory(zero, zero, template.createdAt, template.tags, template.content, zero)

    @Test
    fun namedNappletRoundTrips() {
        val event =
            materialize(
                NamedNappletEvent.build(
                    identifier = "calculator",
                    paths = paths,
                    servers = servers,
                    requires = requires,
                    title = "Calc",
                    description = "a calculator",
                    source = "https://github.com/x/calc",
                    icon = "https://example.com/calc.png",
                ),
                ::NamedNappletEvent,
            )

        assertEquals(35129, event.kind)
        assertEquals("calculator", event.identifier())
        assertEquals(paths.map { it.path }, event.paths().map { it.path })
        assertEquals(paths.map { it.hash }, event.paths().map { it.hash })
        assertEquals(servers, event.servers())
        assertEquals(requires, event.requires())
        assertEquals("Calc", event.title())
        assertEquals("a calculator", event.description())
        assertEquals("https://github.com/x/calc", event.source())
        assertEquals("https://example.com/calc.png", event.icon())

        // build() stamps the x aggregate, and it verifies against the path tags.
        assertNotNull(event.declaredAggregateHash())
        assertEquals(event.computeAggregateHash(), event.declaredAggregateHash())
        assertTrue(event.verifyAggregate())
    }

    @Test
    fun rootNappletHasKind15129AndNoIdentifierNeeded() {
        val event = materialize(RootNappletEvent.build(paths = paths), ::RootNappletEvent)
        assertEquals(15129, event.kind)
        assertTrue(event.verifyAggregate())
    }

    @Test
    fun snapshotHasKind5129AndAlwaysCarriesAggregate() {
        val event = materialize(NappletSnapshotEvent.build(paths = paths), ::NappletSnapshotEvent)
        assertEquals(5129, event.kind)
        assertNotNull(event.declaredAggregateHash())
        assertTrue(event.verifyAggregate())
    }

    @Test
    fun tamperedPathBreaksAggregateVerification() {
        // Build a valid manifest, then rewrite one path hash in the tags without
        // touching the x tag — the recomputed aggregate no longer matches.
        val template = NamedNappletEvent.build(identifier = "x", paths = paths)
        val tamperedTags =
            template.tags
                .map { tag ->
                    if (tag.getOrNull(0) == PathTag.TAG_NAME && tag.getOrNull(1) == "/app.js") {
                        arrayOf(PathTag.TAG_NAME, "/app.js", h1)
                    } else {
                        tag
                    }
                }.toTypedArray()

        val tampered = NamedNappletEvent(zero, zero, template.createdAt, tamperedTags, template.content, zero)
        assertFalse(tampered.verifyAggregate())
    }

    @Test
    fun eventFactoryRoutesAllThreeNappletKinds() {
        val named = NamedNappletEvent.build(identifier = "x", paths = paths)
        val root = RootNappletEvent.build(paths = paths)
        val snapshot = NappletSnapshotEvent.build(paths = paths)

        assertIs<NamedNappletEvent>(
            EventFactory.create<Event>(zero, zero, named.createdAt, NamedNappletEvent.KIND, named.tags, named.content, zero),
        )
        assertIs<RootNappletEvent>(
            EventFactory.create<Event>(zero, zero, root.createdAt, RootNappletEvent.KIND, root.tags, root.content, zero),
        )
        assertIs<NappletSnapshotEvent>(
            EventFactory.create<Event>(zero, zero, snapshot.createdAt, NappletSnapshotEvent.KIND, snapshot.tags, snapshot.content, zero),
        )
    }

    @Test
    fun emptyOptionalsAreOmitted() {
        val event = materialize(RootNappletEvent.build(paths = paths), ::RootNappletEvent)
        assertTrue(event.servers().isEmpty())
        assertTrue(event.requires().isEmpty())
        assertEquals(null, event.title())
        // Only path + x (+ alt) tags expected; the manifest still has its paths.
        assertContentEquals(paths.map { it.hash }, event.paths().map { it.hash })
    }
}
