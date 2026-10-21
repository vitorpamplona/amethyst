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
package com.vitorpamplona.quartz.nip01Core.core

import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.AuthMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.LimitsMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NoticeMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NotifyMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.AuthCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CloseCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CountCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip77Negentropy.NegCloseCmd
import com.vitorpamplona.quartz.nip77Negentropy.NegErrMessage
import com.vitorpamplona.quartz.nip77Negentropy.NegMsgCmd
import com.vitorpamplona.quartz.nip77Negentropy.NegMsgMessage
import com.vitorpamplona.quartz.nip77Negentropy.NegOpenCmd
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Every relay message and client command type, rumors and event lists, as they are read. */
class NostrJsonReaderFrameTest {
    private val pubkey = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"
    private val event = """{"id":"ab","pubkey":"$pubkey","created_at":1,"kind":1,"tags":[["t","x"]],"content":"hi","sig":"cd"}"""
    private val authEvent = """{"id":"ab","pubkey":"$pubkey","created_at":1,"kind":22242,"tags":[["challenge","c1"]],"content":"","sig":"cd"}"""

    private fun message(json: String) = NostrJsonReader.readMessage(json)

    private fun command(json: String) = NostrJsonReader.readCommand(json)

    @Test
    fun eose() {
        assertNull(assertIs<EoseMessage>(message("""["EOSE","s"]""")).hints)
        assertEquals(listOf("complete", "d"), assertIs<EoseMessage>(message("""["EOSE","s",["complete",1,null,["x"],{"a":1},"d"]]""")).hints)
        assertEquals(emptyList(), assertIs<EoseMessage>(message("""["EOSE","s",[]]""")).hints)
        assertNull(assertIs<EoseMessage>(message("""["EOSE","s",{"a":1}]""")).hints)
    }

    @Test
    fun ok() {
        val ok = assertIs<OkMessage>(message("""["OK","id",false,"rate-limited: slow down","1500"]"""))
        assertEquals("id", ok.eventId)
        assertFalse(ok.success)
        assertEquals("rate-limited: slow down", ok.message)
        assertEquals("1500", ok.params)
        assertEquals("1500", assertIs<OkMessage>(message("""["OK","id",false,"x",1500]""")).params)
        assertNull(assertIs<OkMessage>(message("""["OK","id",false,"x",1.5]""")).params)
        assertNull(assertIs<OkMessage>(message("""["OK","id",false,"x",{"a":1}]""")).params)
        // A missing or non-string message reads as "".
        assertEquals("", assertIs<OkMessage>(message("""["OK","id",true]""")).message)
        assertEquals("", assertIs<OkMessage>(message("""["OK","id",true,null]""")).message)
        assertFailsWith<IllegalArgumentException> { message("""["OK","id","true",""]""") }
        assertFailsWith<IllegalArgumentException> { message("""["OK","id",true,{"a":1}]""") }
    }

    @Test
    fun noticeAuthNotifyClosed() {
        assertEquals("a\"b", assertIs<NoticeMessage>(message("""["NOTICE","a\"b"]""")).message)
        assertEquals("c1", assertIs<AuthMessage>(message("""["AUTH","c1"]""")).challenge)
        assertEquals("mail", assertIs<NotifyMessage>(message("""["NOTIFY","mail"]""")).message)
        val closed = assertIs<ClosedMessage>(message("""["CLOSED","s","rate-limited: wait",5000]"""))
        assertEquals("s", closed.subId)
        assertEquals("5000", closed.params)
        assertEquals("", assertIs<ClosedMessage>(message("""["CLOSED","s"]""")).message)
        assertFailsWith<IllegalArgumentException> { message("""["NOTICE",1]""") }
        assertFailsWith<IllegalArgumentException> { message("""["AUTH"]""") }
    }

    @Test
    fun count() {
        val hll = "0f".repeat(256)
        val c = assertIs<CountMessage>(message("""["COUNT","q",{"count":5,"approximate":true,"hll":"$hll"}]"""))
        assertEquals("q", c.queryId)
        assertEquals(5, c.result.count)
        assertTrue(c.result.approximate)
        assertEquals(256, c.result.hll?.size)
        // Read like Jackson's JsonNode.asInt/asBoolean/asText.
        assertEquals(7, assertIs<CountMessage>(message("""["COUNT","q",{"count":7.9}]""")).result.count)
        assertEquals(12, assertIs<CountMessage>(message("""["COUNT","q",{"count":" 12 "}]""")).result.count)
        assertEquals(1, assertIs<CountMessage>(message("""["COUNT","q",{"count":true}]""")).result.count)
        assertEquals(0, assertIs<CountMessage>(message("""["COUNT","q",{"count":null}]""")).result.count)
        assertTrue(assertIs<CountMessage>(message("""["COUNT","q",{"approximate":"true"}]""")).result.approximate)
        assertTrue(assertIs<CountMessage>(message("""["COUNT","q",{"approximate":1}]""")).result.approximate)
        assertNull(assertIs<CountMessage>(message("""["COUNT","q",{"hll":"abc"}]""")).result.hll)
        assertEquals(0, assertIs<CountMessage>(message("""["COUNT","q",5]""")).result.count)
        assertFailsWith<IllegalArgumentException> { message("""["COUNT","q",{"count":"abc"}]""") }
    }

    @Test
    fun limits() {
        val l =
            assertIs<LimitsMessage>(
                message(
                    """["LIMITS",{"can_write":true,"accepted_event_kinds":[1,"7",2.5,[3]],"max_limit":500,"created_at_msecs_ago":94608000000,"required_tags":[["t",1,true,null],"x"],"unknown":{"a":[1]}}]""",
                ),
            )
        assertEquals(true, l.canWrite)
        assertEquals(listOf(1, 2), l.acceptedEventKinds)
        assertEquals(500, l.maxLimit)
        assertEquals(94608000000L, l.createdAtMsecsAgo)
        assertEquals(listOf(listOf("t", "1", "true", "null"), emptyList()), l.requiredTags)
        // Wrong-typed fields read as null; no payload, or a payload that is not an object, is empty.
        assertEquals(LimitsMessage(), message("""["LIMITS",{"can_write":"true","max_limit":"500"}]"""))
        assertEquals(LimitsMessage(), message("""["LIMITS"]"""))
        assertEquals(LimitsMessage(), message("""["LIMITS","x"]"""))
    }

    @Test
    fun negentropyMessages() {
        val msg = assertIs<NegMsgMessage>(message("""["NEG-MSG","s","6100000200"]"""))
        assertEquals("6100000200", msg.message)
        val err = assertIs<NegErrMessage>(message("""["NEG-ERR","s","blocked: too many",500000]"""))
        assertEquals("blocked: too many", err.reason)
        assertEquals(500000L, err.cap)
        assertNull(assertIs<NegErrMessage>(message("""["NEG-ERR","s","blocked",5.5]""")).cap)
        assertEquals("", assertIs<NegErrMessage>(message("""["NEG-ERR","s"]""")).reason)
    }

    @Test
    fun unknownAndMalformedMessages() {
        for (bad in listOf("""["UNKNOWN","x"]""", """[5,"x"]""", "[]", "{}", """["NOTICE","x"""", """["NOTICE","x",]""", """["EOSE"]""")) {
            assertFailsWith<IllegalArgumentException>(bad) { message(bad) }
        }
        // Data after the frame's closing bracket is ignored, as in Jackson.
        assertIs<NoticeMessage>(message("""["NOTICE","x"] trailing"""))
    }

    @Test
    fun req() {
        val req =
            assertIs<ReqCmd>(
                command(
                    """["REQ","s",{"ids":["a"],"authors":["b",1,null],"kinds":[1,1.9,"x"],"#e":["c"],"&t":["d"],"since":10,"until":20.5,"limit":50,"search":"q","other":{"x":1}},{"kinds":[7]}]""",
                ),
            )
        assertEquals("s", req.subId)
        assertEquals(2, req.filters.size)
        val f = req.filters[0]
        assertEquals(listOf("a"), f.ids)
        assertEquals(listOf("b"), f.authors)
        assertEquals(listOf(1, 1), f.kinds)
        assertEquals(mapOf("e" to listOf("c")), f.tags)
        assertEquals(mapOf("t" to listOf("d")), f.tagsAll)
        assertEquals(10L, f.since)
        assertEquals(20L, f.until)
        assertEquals(50, f.limit)
        assertEquals("q", f.search)
        assertEquals(listOf(7), req.filters[1].kinds)
    }

    @Test
    fun filterLeniency() {
        val f = assertIs<ReqCmd>(command("""["REQ","s",{"ids":null,"authors":"x","kinds":{"a":1},"since":null,"search":5}]""")).filters[0]
        // Like ManualFilterDeserializer: a list that is not an array is empty, null leaves a scalar unset.
        assertEquals(emptyList(), f.ids)
        assertEquals(emptyList(), f.authors)
        assertEquals(emptyList(), f.kinds)
        assertNull(f.since)
        assertEquals("5", f.search)
        for (bad in listOf("""{"since":"5"}""", """{"limit":3000000000}""", """{"kinds":[3000000000]}""", """{"search":["a"]}""")) {
            assertFailsWith<IllegalArgumentException>(bad) { command("""["REQ","s",$bad]""") }
        }
    }

    @Test
    fun otherCommands() {
        assertEquals("ab", assertIs<EventCmd>(command("""["EVENT",$event]""")).event.id)
        assertEquals("s", assertIs<CloseCmd>(command("""["CLOSE","s"]""")).subId)
        assertEquals(22242, assertIs<AuthCmd>(command("""["AUTH",$authEvent]""")).event.kind)
        assertFailsWith<IllegalArgumentException> { command("""["AUTH",$event]""") }
        val count = assertIs<CountCmd>(command("""["COUNT","q",{"kinds":[1]}]"""))
        assertEquals("q", count.queryId)
        val open = assertIs<NegOpenCmd>(command("""["NEG-OPEN","n",{"kinds":[1]},"6100"]"""))
        assertEquals(listOf(1), open.filter.kinds)
        assertEquals("6100", open.initialMessage)
        assertEquals("6100", assertIs<NegMsgCmd>(command("""["NEG-MSG","n","6100"]""")).message)
        assertEquals("n", assertIs<NegCloseCmd>(command("""["NEG-CLOSE","n"]""")).subId)
        for (bad in listOf("""["REQ","s","x"]""", """["REQ",5]""", """["CLOSE"]""", """["NOTREAL"]""", """["CLOSE","s"""", """["EVENT"]""")) {
            assertFailsWith<IllegalArgumentException>(bad) { command(bad) }
        }
    }

    @Test
    fun rumors() {
        val r = NostrJsonReader.readRumor("""{"pubkey":"$pubkey","kind":14,"tags":[["p","x"]],"content":"hi","sig":"ignored"}""")
        assertNull(r.id)
        assertEquals(pubkey, r.pubKey)
        assertNull(r.createdAt)
        assertEquals(14, r.kind)
        assertContentEquals(arrayOf("p", "x"), r.tags!![0])
        assertEquals("hi", r.content)
        assertNull(NostrJsonReader.readRumor("{}").pubKey)
    }

    @Test
    fun eventLists() {
        assertEquals(2, NostrJsonReader.readEventList("[$event,$event]").size)
        assertEquals(0, NostrJsonReader.readEventList("[]").size)
        assertEquals(1, NostrJsonReader.readEventList(event).size)
        assertFailsWith<IllegalArgumentException> { NostrJsonReader.readEventList("[null]") }
    }

    @Test
    fun goesThroughOptimizedJsonMapper() {
        assertIs<NoticeMessage>(OptimizedJsonMapper.fromJsonToMessage("""["NOTICE","x"]"""))
        assertIs<ReqCmd>(OptimizedJsonMapper.fromJsonToCommand("""["REQ","s",{}]"""))
        assertEquals(14, OptimizedJsonMapper.fromJsonToRumor("""{"kind":14}""").kind)
        assertEquals(1, OptimizedJsonMapper.fromJsonToEventList("[$event]").size)
    }
}
