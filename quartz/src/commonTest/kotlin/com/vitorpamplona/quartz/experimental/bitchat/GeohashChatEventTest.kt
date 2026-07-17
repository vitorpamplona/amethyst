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
package com.vitorpamplona.quartz.experimental.bitchat

import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashChatEvent
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashPresenceEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeohashChatEventTest {
    private fun sampleChat(): Event =
        EventFactory.create(
            id = "a099d4db563041bb289d3704f983fc148fc805860303a4f479a8264dc6a2d7cc",
            pubKey = "932614571afcbad4d17a191ee281e39eebbb41b93fac8fd87829622aeb112f4d",
            createdAt = 1_700_000_000L,
            kind = GeohashChatEvent.KIND,
            tags =
                arrayOf(
                    arrayOf("g", "u4pruyd"),
                    arrayOf("n", "satoshi"),
                    arrayOf("t", "teleport"),
                    arrayOf("nonce", "000000000000abcd", "8"),
                ),
            content = "gm from the block",
            sig = "00".repeat(64),
        )

    @Test
    fun factoryParsesChatAndPresence() {
        assertIs<GeohashChatEvent>(sampleChat())
        val presence =
            EventFactory.create<Event>(
                id = "b199d4db563041bb289d3704f983fc148fc805860303a4f479a8264dc6a2d7cc",
                pubKey = "932614571afcbad4d17a191ee281e39eebbb41b93fac8fd87829622aeb112f4d",
                createdAt = 1_700_000_000L,
                kind = GeohashPresenceEvent.KIND,
                tags = arrayOf(arrayOf("g", "u4pruyd")),
                content = "",
                sig = "00".repeat(64),
            )
        assertIs<GeohashPresenceEvent>(presence)
    }

    @Test
    fun kindsAreKnown() {
        assertEquals(20000, GeohashChatEvent.KIND)
        assertEquals(20001, GeohashPresenceEvent.KIND)
        assertTrue(EventFactory.isKnownKind(GeohashChatEvent.KIND))
        assertTrue(EventFactory.isKnownKind(GeohashPresenceEvent.KIND))
    }

    @Test
    fun parsesChatFields() {
        val event = sampleChat()
        assertIs<GeohashChatEvent>(event)

        assertEquals("u4pruyd", event.geohash())
        assertEquals("satoshi", event.nickname())
        assertTrue(event.isTeleported())
        assertEquals("gm from the block", event.content)
    }

    @Test
    fun buildProducesSingleExactGeohashTag() {
        val template = GeohashChatEvent.build("hello", "u4pruyd", nickname = "satoshi", createdAt = 1_700_000_000L)

        assertEquals(GeohashChatEvent.KIND, template.kind)
        assertEquals("hello", template.content)

        // A single, exact g tag — NOT the mip-mapped prefix hierarchy.
        val geohashes = template.tags.filter { it[0] == "g" }.map { it[1] }
        assertEquals(listOf("u4pruyd"), geohashes)

        assertEquals("satoshi", template.tags.first { it[0] == "n" }[1])
        assertTrue(template.tags.none { it[0] == "t" })
    }

    @Test
    fun buildOmitsOptionalTagsWhenAbsent() {
        val template = GeohashChatEvent.build("hi", "u4pru", createdAt = 1_700_000_000L)
        assertTrue(template.tags.none { it[0] == "n" })
        assertTrue(template.tags.none { it[0] == "t" })
        assertEquals(listOf("u4pru"), template.tags.filter { it[0] == "g" }.map { it[1] })
    }

    @Test
    fun buildAddsTeleportMarkerWhenRequested() {
        val template = GeohashChatEvent.build("hi", "u4pru", teleported = true, createdAt = 1_700_000_000L)
        assertEquals("teleport", template.tags.first { it[0] == "t" }[1])
    }

    @Test
    fun presenceHasOnlyGeohashByDefault() {
        val template = GeohashPresenceEvent.build("u4pruyd", createdAt = 1_700_000_000L)
        assertEquals(GeohashPresenceEvent.KIND, template.kind)
        assertEquals("", template.content)
        assertEquals(listOf("u4pruyd"), template.tags.filter { it[0] == "g" }.map { it[1] })
        assertTrue(template.tags.none { it[0] == "n" })
    }

    @Test
    fun chatWithoutTeleportParsesFalse() {
        val event =
            EventFactory.create<Event>(
                id = "00".repeat(32),
                pubKey = "00".repeat(32),
                createdAt = 1_700_000_000L,
                kind = GeohashChatEvent.KIND,
                tags = arrayOf(arrayOf("g", "u4pru")),
                content = "no name",
                sig = "00".repeat(64),
            )
        assertIs<GeohashChatEvent>(event)
        assertEquals("u4pru", event.geohash())
        assertNull(event.nickname())
        assertFalse(event.isTeleported())
    }
}
