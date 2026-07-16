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
package com.vitorpamplona.quartz.nip01Core.relay.commands

import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.LimitsMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MachineReadablePrefix
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RelayWireErgonomicsTest {
    @Test
    fun commandFromJsonParsesReq() {
        val cmd = Command.fromJson("""["REQ","sub1",{"kinds":[1]}]""")
        assertTrue(cmd is ReqCmd)
        assertEquals("sub1", cmd.subId)
        assertEquals(listOf(1), cmd.filters.single().kinds)
    }

    @Test
    fun commandToJsonRoundTrips() {
        val json = ReqCmd("sub1", listOf()).toJson()
        assertTrue(json.contains("\"REQ\""))
        val parsed = Command.fromJson(json) as ReqCmd
        assertEquals("sub1", parsed.subId)
    }

    @Test
    fun messageToJsonAndBack() {
        val json = EoseMessage("sub1").toJson()
        assertEquals("""["EOSE","sub1"]""", json)
        val parsed = Message.fromJson(json)
        assertTrue(parsed is EoseMessage)
        assertEquals("sub1", parsed.subId)
    }

    @Test
    fun machineReadablePrefixFormats() {
        assertEquals(
            "auth-required: please log in",
            MachineReadablePrefix.AUTH_REQUIRED.format("please log in"),
        )
        assertEquals("error: boom", MachineReadablePrefix.ERROR.format("boom"))
    }

    @Test
    fun machineReadablePrefixParses() {
        assertEquals(
            MachineReadablePrefix.RESTRICTED,
            MachineReadablePrefix.parse("restricted: not allowed yet"),
        )
        assertEquals(
            MachineReadablePrefix.RATE_LIMITED,
            MachineReadablePrefix.parse("rate-limited: slow down"),
        )
        assertNull(MachineReadablePrefix.parse("no prefix here"))
        assertNull(MachineReadablePrefix.parse("unknownword: hello"))
        assertNull(MachineReadablePrefix.parse(":leading colon"))
    }

    @Test
    fun okMessageFactories() {
        val ok = OkMessage.accepted("a".repeat(64))
        assertTrue(ok.success)

        val bad = OkMessage.rejected("a".repeat(64), MachineReadablePrefix.AUTH_REQUIRED, "log in")
        assertTrue(!bad.success)
        assertEquals("auth-required: log in", bad.message)
    }

    @Test
    fun closedMessageFactory() {
        val closed = ClosedMessage.of("sub1", MachineReadablePrefix.RESTRICTED, "nope")
        assertEquals("sub1", closed.subId)
        assertEquals("restricted: nope", closed.message)
    }

    @Test
    fun limitsMessageParsesProductionPayload() {
        // Real payload from wss://pipe.imwald.eu/ (NIP-22 LIMITS).
        val json =
            """["LIMITS",{"can_read":true,"can_write":true,"auth_for_read":true,"auth_for_write":false,""" +
                """"max_message_length":262144,"max_subscriptions":10,"max_filters":10,"max_limit":200,""" +
                """"max_event_tags":2000,"max_content_length":131072}]"""
        val parsed = Message.fromJson(json)
        assertTrue(parsed is LimitsMessage)
        assertEquals(true, parsed.canRead)
        assertEquals(true, parsed.canWrite)
        assertEquals(true, parsed.authForRead)
        assertEquals(false, parsed.authForWrite)
        assertEquals(262144, parsed.maxMessageLength)
        assertEquals(10, parsed.maxSubscriptions)
        assertEquals(10, parsed.maxFilters)
        assertEquals(200, parsed.maxLimit)
        assertEquals(2000, parsed.maxEventTags)
        assertEquals(131072, parsed.maxContentLength)
        // Fields the relay didn't send stay null.
        assertNull(parsed.minPowDifficulty)
        assertNull(parsed.acceptedEventKinds)
        assertNull(parsed.requiredTags)
    }

    @Test
    fun limitsMessageParsesArrayFields() {
        val json =
            """["LIMITS",{"accepted_event_kinds":[0,1,3],"blocked_event_kinds":[4],""" +
                """"min_pow_difficulty":16,"created_at_msecs_ago":3600000,"created_at_msecs_ahead":60000,""" +
                """"required_tags":[["t","nostr"],["p"]]}]"""
        val parsed = Message.fromJson(json)
        assertTrue(parsed is LimitsMessage)
        assertEquals(listOf(0, 1, 3), parsed.acceptedEventKinds)
        assertEquals(listOf(4), parsed.blockedEventKinds)
        assertEquals(16, parsed.minPowDifficulty)
        assertEquals(3600000L, parsed.createdAtMsecsAgo)
        assertEquals(60000L, parsed.createdAtMsecsAhead)
        assertEquals(listOf(listOf("t", "nostr"), listOf("p")), parsed.requiredTags)
    }

    @Test
    fun limitsMessageRoundTrips() {
        val original =
            LimitsMessage(
                canRead = true,
                canWrite = false,
                authForWrite = true,
                maxLimit = 500,
                acceptedEventKinds = listOf(1, 30023),
                requiredTags = listOf(listOf("t", "nostr")),
            )
        val json = original.toJson()
        assertTrue(json.startsWith("""["LIMITS",{"""))
        val parsed = Message.fromJson(json)
        assertTrue(parsed is LimitsMessage)
        assertEquals(true, parsed.canRead)
        assertEquals(false, parsed.canWrite)
        assertEquals(true, parsed.authForWrite)
        assertNull(parsed.authForRead)
        assertEquals(500, parsed.maxLimit)
        assertEquals(listOf(1, 30023), parsed.acceptedEventKinds)
        assertEquals(listOf(listOf("t", "nostr")), parsed.requiredTags)
    }

    @Test
    fun limitsMessageParsesEmptyObject() {
        val parsed = Message.fromJson("""["LIMITS",{}]""")
        assertTrue(parsed is LimitsMessage)
        assertNull(parsed.canRead)
        assertNull(parsed.maxLimit)
    }

    @Test
    fun limitsMessageToleratesPayloadlessFrame() {
        // A malformed ["LIMITS"] with no object must not throw; it yields an empty message.
        val parsed = Message.fromJson("""["LIMITS"]""")
        assertTrue(parsed is LimitsMessage)
        assertNull(parsed.canRead)
        assertNull(parsed.maxLimit)
    }

    @Test
    fun limitsMessageToleratesMistypedAndNullFields() {
        // Wrong-typed and explicitly-null fields degrade to null ("unspecified"),
        // never to false/0, and never throw.
        val json =
            """["LIMITS",{"can_write":null,"max_limit":"lots","max_filters":true,""" +
                """"accepted_event_kinds":[1,"x",3],"required_tags":["oops",["t","nostr"]]}]"""
        val parsed = Message.fromJson(json)
        assertTrue(parsed is LimitsMessage)
        assertNull(parsed.canWrite, "explicit null stays null, not false")
        assertNull(parsed.maxLimit, "a string is not an int -> null, not 0")
        assertNull(parsed.maxFilters, "a boolean is not an int -> null")
        assertEquals(listOf(1, 3), parsed.acceptedEventKinds, "non-int array elements are skipped")
        // The bare "oops" string is not a tag array -> empty; the real pair survives.
        assertEquals(listOf(emptyList(), listOf("t", "nostr")), parsed.requiredTags)
    }

    @Test
    fun limitsMessageHasValueEquality() {
        // data class equality lets StateFlow.distinctUntilChanged suppress no-op re-advertisements.
        assertEquals(
            LimitsMessage(canWrite = true, maxLimit = 200, acceptedEventKinds = listOf(1, 2)),
            LimitsMessage(canWrite = true, maxLimit = 200, acceptedEventKinds = listOf(1, 2)),
        )
    }
}
