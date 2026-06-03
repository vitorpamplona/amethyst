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
}
