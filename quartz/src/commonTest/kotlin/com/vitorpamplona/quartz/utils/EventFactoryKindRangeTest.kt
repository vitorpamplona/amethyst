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
package com.vitorpamplona.quartz.utils

import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards the kind-range vs class-hierarchy invariant across the whole
 * EventFactory: parameterized replaceables (30000..39999) must derive their
 * address from the `d` tag, while plain replaceables (10000..19999, plus
 * kinds 0 and 3) must ignore stray `d` tags. A typed class extending the
 * wrong base (the kind-34235/34236 bug, fixed in this branch) splits the
 * cache between the event's own address and the address `a` tags reference.
 *
 * Known offenders that predate this guard are allowlisted below so the test
 * catches NEW mismatches; shrink the lists as they get fixed.
 */
class EventFactoryKindRangeTest {
    /**
     * NIP-87 ecash kinds that are addressable per spec but currently extend
     * plain Event, so they have no address at all and never replace older
     * versions in the cache.
     */
    private val knownNonAddressable = setOf(38000, 38172, 38173)

    /**
     * NIP-51-style list kinds whose shared PrivateTagArrayEvent hierarchy
     * reads `d` tags even though plain replaceables must ignore them; a
     * stray `d` tag on a malformed event fragments their cache address.
     */
    private val knownDTagReaders =
        setOf(
            10004,
            10005,
            10006,
            10007,
            10009,
            10012,
            10013,
            10015,
            10017,
            10018,
            10020,
            10023,
            10040,
            10054,
            10081,
            10086,
            10087,
            10088,
            10089,
            10090,
            10101,
            10102,
        )

    private val probeDTag = "probe-d-tag"

    private fun probe(kind: Int) = EventFactory.create<Event>("", "", 0L, kind, arrayOf(arrayOf("d", probeDTag)), "", "")

    @Test
    fun addressableKindsReadTheirDTag() {
        val violations =
            (30000 until 40000).mapNotNull { kind ->
                val event = probe(kind)
                when {
                    // Unknown kinds parse as a bare Event; only typed classes are checked.
                    event::class == Event::class -> null
                    kind in knownNonAddressable -> null
                    event !is AddressableEvent -> "kind $kind (${event::class.simpleName}) does not implement AddressableEvent"
                    event.dTag() != probeDTag -> "kind $kind (${event::class.simpleName}) ignores its d tag"
                    else -> null
                }
            }

        assertEquals(emptyList(), violations)
    }

    @Test
    fun plainReplaceableKindsIgnoreStrayDTags() {
        val violations =
            (listOf(0, 3) + (10000 until 20000)).mapNotNull { kind ->
                val event = probe(kind)
                if (kind !in knownDTagReaders && event::class != Event::class && event is AddressableEvent && event.dTag() != "") {
                    "kind $kind (${event::class.simpleName}) addresses itself by a stray d tag"
                } else {
                    null
                }
            }

        assertEquals(emptyList(), violations)
    }
}
