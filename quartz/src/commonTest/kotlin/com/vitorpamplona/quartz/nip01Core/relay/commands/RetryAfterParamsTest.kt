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

import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.MessageKSerializer
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.RetryAfter
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The optional `params` of NIP-01 `OK` / `CLOSED` (nostr-protocol/nips#2498) and the
 * `rate-limited` back-off it carries, on both codecs: the platform one behind
 * [Message.fromJson] / [Message.toJson] and kotlinx's [MessageKSerializer].
 */
class RetryAfterParamsTest {
    private val id = "b1a649ebe8b435ec71d3784793f3bbf4b93e64e17568a741aecd4c7ddeafce30"

    private fun parseBoth(json: String): List<Message> = listOf(Message.fromJson(json), Json.decodeFromString(MessageKSerializer, json))

    private fun writeBoth(msg: Message): List<String> = listOf(msg.toJson(), Json.encodeToString(MessageKSerializer, msg))

    @Test
    fun okCarriesTheSpecExample() {
        parseBoth("""["OK","$id",false,"rate-limited: slow down there chief","1000"]""").forEach {
            val ok = it as OkMessage
            assertEquals(false, ok.success)
            assertEquals("rate-limited: slow down there chief", ok.message)
            assertEquals("1000", ok.params)
            assertEquals(1000L, ok.retryAfterMs)
        }
    }

    @Test
    fun closedCarriesTheSpecExample() {
        parseBoth("""["CLOSED","sub1","rate-limited: slow down there chief","2500"]""").forEach {
            val closed = it as ClosedMessage
            assertEquals("sub1", closed.subId)
            assertEquals("2500", closed.params)
            assertEquals(2500L, closed.retryAfterMs)
        }
    }

    @Test
    fun theShortFormsHaveNoParams() {
        parseBoth("""["OK","$id",false,"rate-limited: slow down there chief"]""").forEach {
            assertNull((it as OkMessage).params)
            assertNull(it.retryAfterMs)
        }
        parseBoth("""["CLOSED","sub1","rate-limited: slow down"]""").forEach {
            assertNull((it as ClosedMessage).params)
            assertNull(it.retryAfterMs)
        }
    }

    @Test
    fun paramsOnlyMeanABackOffUnderRateLimited() {
        val ok = OkMessage(id, false, "blocked: you are banned", "1000")
        assertEquals("1000", ok.params)
        assertNull(ok.retryAfterMs)
        assertNull(ClosedMessage("s", "error: shutting down", "2500").retryAfterMs)
        assertNull(ClosedMessage("s", "slow down", "2500").retryAfterMs)
    }

    @Test
    fun anUnreadableBackOffIsNoBackOff() {
        listOf("0", "-5", "", "1.5", "1e3", "soon", " 100", "999999999999999999999").forEach {
            assertNull(RetryAfter.parse("rate-limited: slow down", it), "read a back-off from \"$it\"")
        }
        assertEquals(100L, RetryAfter.parse("rate-limited: slow down", "0100"))
    }

    @Test
    fun aNumericOrOddParamDoesNotBreakTheFrame() {
        parseBoth("""["OK","$id",false,"rate-limited: slow down",1000]""").forEach {
            assertEquals(1000L, (it as OkMessage).retryAfterMs)
        }
        parseBoth("""["CLOSED","sub1","rate-limited: slow down",{"after":1000}]""").forEach {
            val closed = it as ClosedMessage
            assertEquals("rate-limited: slow down", closed.message)
            assertNull(closed.params)
        }
        parseBoth("""["OK","$id",false,"rate-limited: slow down",null]""").forEach {
            assertNull((it as OkMessage).params)
        }
    }

    @Test
    fun writesTheTrailingElementOnlyWhenPresent() {
        writeBoth(OkMessage.rateLimited(id, "slow down there chief", 1000)).forEach {
            assertEquals("""["OK","$id",false,"rate-limited: slow down there chief","1000"]""", it)
        }
        writeBoth(ClosedMessage.rateLimited("sub1", "slow down there chief", 2500)).forEach {
            assertEquals("""["CLOSED","sub1","rate-limited: slow down there chief","2500"]""", it)
        }
        writeBoth(OkMessage.rateLimited(id, "slow down")).forEach {
            assertEquals("""["OK","$id",false,"rate-limited: slow down"]""", it)
        }
        writeBoth(ClosedMessage("sub1", "error: shutting down")).forEach {
            assertEquals("""["CLOSED","sub1","error: shutting down"]""", it)
        }
    }

    @Test
    fun paramsThatNeedEscapingRoundTrip() {
        val ok = OkMessage(id, false, "rate-limited: \"slow\"", "ünïcode \"x\"")
        val back = Message.fromJson(ok.toJson()) as OkMessage
        assertEquals(ok.message, back.message)
        assertEquals(ok.params, back.params)
        assertTrue(ok.toJson().endsWith("\"]"))
    }

    @Test
    fun formatNeverWritesAZeroWait() {
        assertEquals("1", RetryAfter.format(0))
        assertEquals("1", RetryAfter.format(-10))
        assertEquals("2500", RetryAfter.format(2500))
    }
}
