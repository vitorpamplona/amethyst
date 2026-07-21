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
package com.vitorpamplona.quartz.buzz.aeEngrams

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EngramEventTest {
    private val agent = NostrSignerInternal(KeyPair())
    private val owner = NostrSignerInternal(KeyPair())

    // Opaque conversation key used only to derive a well-formed d tag for the
    // round-trip tests — the event treats the d tag as opaque, so any 32 bytes work.
    private val convoKey = ByteArray(32) { it.toByte() }

    @Test
    fun dTagMatchesBuzzReferenceVectors() {
        // Pinned from buzz-core/src/engram.rs `d_tags_match_spec` (sk=1 agent, sk=2 owner).
        val kc = "c41c775356fd92eadc63ff5a0dc1da211b268cbea22316767095b2871ea1412d".hexToByteArray()
        assertEquals("bdc233238ffe52e272b44cc233c8f33a2bc510b08be04495b225964283be4a90", EngramDTag.derive(kc, "core"))
        assertEquals("72d4f9629106451505d7d341ea85bb3ebad4f654fcfd2aad100d5a35f8a85cba", EngramDTag.derive(kc, "mem/example"))
        assertEquals("31651571a312780cfdc1f0b706b682ac9f3f51a053e8dca76fe57710bae5a4d4", EngramDTag.derive(kc, "mem/notes/2026-05-12"))
    }

    @Test
    fun memoryBodyEncodesByteExact() {
        val body = EngramMemoryBody(slug = "mem/example", value = "hello, agent memory")
        assertEquals("{\"slug\":\"mem/example\",\"value\":\"hello, agent memory\"}", body.encodeToJson())
    }

    @Test
    fun tombstoneEncodesExplicitNull() {
        val body = EngramMemoryBody(slug = "mem/example", value = null)
        assertTrue(body.isTombstone())
        assertEquals("{\"slug\":\"mem/example\",\"value\":null}", body.encodeToJson())
    }

    @Test
    fun coreBodyEncodesSlugFirst() {
        val body = EngramCoreBody(profile = "test agent")
        assertEquals("{\"slug\":\"core\",\"profile\":\"test agent\"}", body.encodeToJson())
    }

    @Test
    fun memoryRoundTripsBothWays() =
        runTest {
            val d = EngramDTag.derive(convoKey, "mem/example")
            val body = EngramMemoryBody(slug = "mem/example", value = "hello")

            val event = EngramEvent.create(body, owner.pubKey, d, agent)

            assertEquals(30174, event.kind)
            assertEquals(agent.pubKey, event.pubKey)
            assertEquals(owner.pubKey, event.ownerPubKey())
            assertEquals(d, event.dTag())

            // Agent (author) and owner (p) both hold the symmetric key.
            assertEquals(body, event.decrypt(agent))
            assertEquals(body, event.decrypt(owner))
        }

    @Test
    fun tombstoneRoundTrips() =
        runTest {
            val d = EngramDTag.derive(convoKey, "mem/example")
            val body = EngramMemoryBody(slug = "mem/example", value = null)

            val event = EngramEvent.create(body, owner.pubKey, d, agent)
            val decoded = event.decrypt(owner)

            assertEquals(body, decoded)
            assertTrue((decoded as EngramMemoryBody).isTombstone())
        }

    @Test
    fun coreRoundTrips() =
        runTest {
            val d = EngramDTag.derive(convoKey, "core")
            val body = EngramCoreBody(profile = "test agent. see [[mem/example]].")

            val event = EngramEvent.create(body, owner.pubKey, d, agent)

            assertEquals(body, event.decrypt(owner))
        }
}
