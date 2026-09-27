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
package com.vitorpamplona.quartz.nipFERelayOverHttp

import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** NIP-FE, client side: an answer's lines back into frames, and a whole answer told from a cut one. */
class HttpRelayAnswerReaderTest {
    private val event =
        """{"id":"a8e0b2f2c1e7e4f5b0a1f9c1b3d2e6a7c8b9d0e1f2a3b4c5d6e7f8091a2b3c4d","pubkey":"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",""" +
            """"created_at":1700000000,"kind":1,"tags":[],"content":"hi","sig":"${"0".repeat(128)}"}"""

    private fun read(
        command: HttpRelayCommand,
        status: Int,
        vararg lines: String,
    ) = HttpRelayAnswerReader(command, status).also { reader -> lines.forEach { reader.read(it) } }

    @Test
    fun theSubscriptionIdGoesBackWhereItWasTakenOut() {
        for (frame in listOf("""["EVENT","http",$event]""", """["EOSE","http"]""", """["CLOSED","http","error: x"]""", """["COUNT","http",{"count":3}]""")) {
            assertEquals(frame, withSubId(withoutSubId(frame)))
        }
        assertEquals("""["OK","id",true,""]""", withSubId("""["OK","id",true,""]"""))
        assertEquals("""["NOTICE","hi"]""", withSubId("""["NOTICE","hi"]"""))
        assertEquals("""[ "EOSE","http"]""", withSubId("""[ "EOSE"]"""))
    }

    @Test
    fun aReqThatEndsOnEoseIsComplete() {
        val reader = read(HttpRelayCommand.REQ, 200, """["EVENT",$event]""", """["EOSE"]""")
        assertTrue(reader.complete)
        assertIs<EoseMessage>(reader.last)
    }

    @Test
    fun aReqWithItsTailMissingIsIncomplete() {
        val reader = HttpRelayAnswerReader(HttpRelayCommand.REQ, 200)
        val message = reader.read("""["EVENT",$event]""")
        assertIs<EventMessage>(message)
        assertEquals("hi", message.event.content)
        assertEquals(HttpRelayCommand.SUB_ID, message.subId)
        assertFalse(reader.complete)
        assertFalse(HttpRelayAnswerReader(HttpRelayCommand.REQ, 200).complete, "an empty body is no answer")
    }

    @Test
    fun aClosedEndsAReqAndACountButNotAnEvent() {
        assertTrue(read(HttpRelayCommand.REQ, 200, """["EVENT",$event]""", """["CLOSED","error: the answer ran past 30s"]""").complete)
        assertTrue(read(HttpRelayCommand.COUNT, 200, """["CLOSED","error: x"]""").complete)
        assertFalse(read(HttpRelayCommand.EVENT, 200, """["CLOSED","error: x"]""").complete)
    }

    @Test
    fun aCountAndAnOkAreWholeAnswers() {
        val count = read(HttpRelayCommand.COUNT, 200, """["COUNT",{"count":7}]""")
        assertTrue(count.complete)
        assertEquals(7, assertIs<CountMessage>(count.last).result.count)
        val ok = read(HttpRelayCommand.EVENT, 200, """["OK","abc",true,""]""")
        assertTrue(ok.complete)
        assertTrue(assertIs<OkMessage>(ok.last).success)
    }

    @Test
    fun aRefusalIsOneLineWhateverItsFrame() {
        val refused = read(HttpRelayCommand.EVENT, 401, """["CLOSED","auth-required: sign in"]""")
        assertTrue(refused.complete)
        assertEquals("auth-required: sign in", assertIs<ClosedMessage>(refused.last).message)
        assertFalse(read(HttpRelayCommand.REQ, 403, """["CLOSED","blocked: no"]""", """["EOSE"]""").complete)
    }

    @Test
    fun anythingAfterTheEndOrALineThatIsNoFrameBreaksTheAnswer() {
        assertFalse(read(HttpRelayCommand.REQ, 200, """["EOSE"]""", """["EVENT",$event]""").complete)
        assertFalse(read(HttpRelayCommand.REQ, 200, """["EVENT",$event]""", """["EOSE""").complete)
        assertTrue(read(HttpRelayCommand.REQ, 200, """["EOSE"]""", "", "  ").complete, "blank lines are not frames")
    }

    @Test
    fun blankLinesAreSkipped() {
        assertNull(HttpRelayAnswerReader(HttpRelayCommand.REQ, 200).read(""))
    }

    @Test
    fun theEndpointsHangOffTheRelayUrl() {
        assertEquals("https://relay.example/req", HttpRelayCommand.REQ.url(NormalizedRelayUrl("wss://relay.example/")))
        assertEquals("http://127.0.0.1:7447/nostr/count", HttpRelayCommand.COUNT.url(NormalizedRelayUrl("ws://127.0.0.1:7447/nostr")))
        assertEquals("http://127.0.0.1:7447/nostr/event", HttpRelayCommand.EVENT.url(NormalizedRelayUrl("ws://127.0.0.1:7447/nostr/")))
    }

    @Test
    fun aFilterBodyIsTheArrayAfterTheSubscriptionId() {
        val body = HttpRelayCommand.body(listOf(Filter(kinds = listOf(1), limit = 2), Filter(kinds = listOf(0))))
        assertEquals("""[{"kinds":[1],"limit":2},{"kinds":[0]}]""", body)
        assertEquals("""["REQ","http",{"kinds":[1],"limit":2},{"kinds":[0]}]""", HttpRelayCommand.REQ.frameOf(body))
    }
}
