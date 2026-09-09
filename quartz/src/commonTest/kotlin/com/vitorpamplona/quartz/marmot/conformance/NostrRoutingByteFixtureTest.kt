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
package com.vitorpamplona.quartz.marmot.conformance

import com.vitorpamplona.quartz.TestResourceLoader
import com.vitorpamplona.quartz.marmot.appComponents.AppComponentIds
import com.vitorpamplona.quartz.marmot.appComponents.NostrRoutingV1
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The reference implementation's byte-level fixtures for
 * `marmot.transport.nostr.routing.v1` (`0x8004`), run against our codec.
 *
 * These come from `mdk/crates/cgka-conformance-simulator/vectors/byte-fixtures/`,
 * copied verbatim, and they are the only tests we have that pin the WIRE BYTES
 * of a Marmot app component against another implementation rather than against
 * our own encoder. A round-trip test proves we can read what we wrote; these
 * prove we can read what MDK wrote, which is a different claim and the one that
 * matters for a group with members on both.
 *
 * The invalid fixture is the sharper one. Its note says implementations "MUST
 * reject this update during component validation" — a decoder that quietly
 * deduplicated the relay list instead would hold different canonical bytes than
 * the peer that sent them, and the two would disagree about the group's state
 * forever after.
 */
class NostrRoutingByteFixtureTest {
    private fun fixture(name: String) = Json.parseToJsonElement(TestResourceLoader().loadString("marmot/conformance/$name")).jsonObject

    private fun bytesOf(fixture: kotlinx.serialization.json.JsonObject) = Hex.decode(fixture["bytes"]!!.jsonObject["hex"]!!.jsonPrimitive.content)

    private fun expectedRelays(fixture: kotlinx.serialization.json.JsonObject) =
        fixture["expected"]!!
            .jsonObject["fields"]!!
            .jsonObject["relays"]!!
            .jsonArray
            .map { it.jsonPrimitive.content }

    private fun expectedGroupId(fixture: kotlinx.serialization.json.JsonObject) =
        fixture["expected"]!!
            .jsonObject["fields"]!!
            .jsonObject["nostr_group_id_hex"]!!
            .jsonPrimitive.content

    /** Every fixture names the component it belongs to; check we read the right ones. */
    private fun assertIsRoutingFixture(fixture: kotlinx.serialization.json.JsonObject) {
        assertEquals("1", fixture["fixture_version"]!!.jsonPrimitive.content)
        assertEquals(
            AppComponentIds.NOSTR_ROUTING_V1,
            fixture["component"]!!
                .jsonObject["id"]!!
                .jsonPrimitive.content
                .removePrefix("0x")
                .toInt(16),
        )
    }

    @Test
    fun theValidStateFixtureDecodesToItsDeclaredFields() {
        val fixture = fixture("nostr-routing-v1-valid-state.v1.json")
        assertIsRoutingFixture(fixture)
        assertTrue(fixture["expected"]!!.jsonObject["valid"]!!.jsonPrimitive.content == "true")

        val decoded = NostrRoutingV1.decode(bytesOf(fixture))
        assertEquals(expectedGroupId(fixture), decoded.nostrGroupId.toHexKey())
        assertEquals(expectedRelays(fixture), decoded.relays)
    }

    @Test
    fun weReEncodeTheValidStateToTheSameBytes() {
        // Canonical encoding is a two-way claim: reading their bytes is half of
        // it, and writing bytes they would read is the other. A component whose
        // re-encode differs by one byte is state a conformant decoder rejects
        // outright, because it compares against its own serialization.
        val fixture = fixture("nostr-routing-v1-valid-state.v1.json")
        val bytes = bytesOf(fixture)
        assertContentEquals(bytes, NostrRoutingV1.decode(bytes).encode())
    }

    @Test
    fun theValidUpdateFixtureDecodesWithTheSameCodecAsTheState() {
        // "The update is a full replacement state and begins with
        // nostr_group_id[32]; it is decoded by the same codec as the state
        // vector." An implementation that gave the update its own shape would
        // read a relay rotation as garbage.
        val fixture = fixture("nostr-routing-v1-valid-update.v1.json")
        assertIsRoutingFixture(fixture)

        val bytes = bytesOf(fixture)
        val decoded = NostrRoutingV1.decode(bytes)
        assertEquals(expectedGroupId(fixture), decoded.nostrGroupId.toHexKey())
        assertEquals(expectedRelays(fixture), decoded.relays)
        assertContentEquals(bytes, decoded.encode())
    }

    @Test
    fun theDuplicateRelayFixtureIsRejected() {
        val fixture = fixture("nostr-routing-v1-invalid-duplicate-relay.v1.json")
        assertIsRoutingFixture(fixture)
        assertTrue(fixture["expected"]!!.jsonObject["valid"]!!.jsonPrimitive.content == "false")
        assertEquals(
            listOf("duplicate_relay"),
            fixture["expected"]!!.jsonObject["errors"]!!.jsonArray.map { it.jsonPrimitive.content },
        )

        // Rejected, not repaired. Silently dropping the duplicate would leave
        // us holding bytes no peer agrees with.
        assertFailsWith<IllegalArgumentException>("a duplicate relay must be refused, not deduplicated") {
            NostrRoutingV1.decode(bytesOf(fixture))
        }
    }

    @Test
    fun theComponentDataWrapperCarriesTheSameStateBytes() {
        // `component_data_hex` is the OpenMLS ComponentData entry: the uint16
        // component id, then the state as a variable-length vector. Checking
        // the inner bytes against `hex` is what catches a framing mistake in
        // the dictionary layer rather than in the component codec.
        val fixture = fixture("nostr-routing-v1-valid-state.v1.json")
        val wrapper = Hex.decode(fixture["bytes"]!!.jsonObject["component_data_hex"]!!.jsonPrimitive.content)
        val state = bytesOf(fixture)

        val id = ((wrapper[0].toInt() and 0xff) shl 8) or (wrapper[1].toInt() and 0xff)
        assertEquals(AppComponentIds.NOSTR_ROUTING_V1, id, "the wrapper names 0x8004")
        if (!wrapper.copyOfRange(wrapper.size - state.size, wrapper.size).contentEquals(state)) {
            fail("the ComponentData wrapper does not end with the state bytes it declares")
        }
    }
}
