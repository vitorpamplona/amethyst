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
package com.vitorpamplona.quartz.nip90Dvms.dvmHeartbeat

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DvmHeartbeatEventTest {
    private val pubKey = "11".repeat(32)
    private val dTag = "my-dvm"
    private val beatTime = 1_760_000_000L

    private fun heartbeat(createdAt: Long = beatTime) =
        DvmHeartbeatEvent(
            id = "00".repeat(32),
            pubKey = pubKey,
            createdAt = createdAt,
            tags =
                arrayOf(
                    arrayOf("d", dTag),
                    arrayOf("status", "My heart keeps beating like a hammer"),
                    arrayOf("expiration", (createdAt + 300).toString()),
                ),
            content = "Alive and kicking",
            sig = "22".repeat(64),
        )

    @Test
    fun addressIncludesTheDTag() {
        val event = heartbeat()
        assertEquals(dTag, event.dTag())
        assertEquals(Address(11998, pubKey, dTag), event.address())
        assertEquals("11998:$pubKey:$dTag", event.addressTag())
    }

    @Test
    fun missingDTagFallsBackToEmptyAddress() {
        val event =
            DvmHeartbeatEvent(
                id = "00".repeat(32),
                pubKey = pubKey,
                createdAt = beatTime,
                tags = emptyArray(),
                content = "Alive and kicking",
                sig = "22".repeat(64),
            )
        assertEquals("", event.dTag())
        assertEquals(Address(11998, pubKey, ""), event.address())
    }

    @Test
    fun readsStatusAndExpiration() {
        val event = heartbeat()
        assertEquals("My heart keeps beating like a hammer", event.status())
        assertEquals(beatTime + 300, event.expiration())
    }

    @Test
    fun statusIsOptional() {
        val event = DvmHeartbeatEvent("00".repeat(32), pubKey, beatTime, arrayOf(arrayOf("d", dTag)), "", "22".repeat(64))
        assertNull(event.status())
        assertNull(event.expiration())
    }

    @Test
    fun freshnessBoundary() {
        val event = heartbeat()
        assertTrue(event.isFreshAt(beatTime + 900))
        assertFalse(event.isFreshAt(beatTime + 901))
    }

    @Test
    fun buildWritesAllTags() {
        val template =
            DvmHeartbeatEvent.build(
                dTag = dTag,
                status = "My heart keeps beating like a hammer",
                expiration = beatTime + 300,
                createdAt = beatTime,
            )
        assertEquals(11998, template.kind)
        assertEquals("Alive and kicking", template.content)
        assertEquals(
            listOf(
                listOf("d", dTag),
                listOf("status", "My heart keeps beating like a hammer"),
                listOf("expiration", (beatTime + 300).toString()),
            ),
            template.tags.map { it.toList() },
        )
    }

    @Test
    fun factoryBuildsDvmHeartbeatForKind11998() {
        val event: Event =
            EventFactory.create(
                id = "00".repeat(32),
                pubKey = pubKey,
                createdAt = beatTime,
                kind = DvmHeartbeatEvent.KIND,
                tags = arrayOf(arrayOf("d", dTag)),
                content = "",
                sig = "22".repeat(64),
            )
        assertIs<DvmHeartbeatEvent>(event)
        assertTrue(EventFactory.isKnownKind(DvmHeartbeatEvent.KIND), "kind 11998 should be a known kind")
    }
}
