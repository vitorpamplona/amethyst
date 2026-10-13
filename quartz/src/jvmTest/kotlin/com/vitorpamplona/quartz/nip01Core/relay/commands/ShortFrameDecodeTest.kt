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
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip77Negentropy.NegErrMessage
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

/**
 * Frames cut short before their optional trailing elements must decode, not hang. Reading an
 * optional element past the frame's closing `]` leaves the decoder's drain loop spinning at the
 * end of input forever — on the relay's reader thread, so one malformed frame from one relay
 * would stall that socket for good.
 */
class ShortFrameDecodeTest {
    private val pool = Executors.newCachedThreadPool { Thread(it).apply { isDaemon = true } }

    private fun decode(json: String): Message {
        val future = pool.submit<Message> { Message.fromJson(json) }
        return try {
            future.get(5, TimeUnit.SECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            fail("decoding $json did not return")
        }
    }

    @Test
    fun okWithoutAMessage() {
        val ok = decode("""["OK","${"a".repeat(64)}",true]""") as OkMessage
        assertEquals("", ok.message)
        assertNull(ok.params)
    }

    @Test
    fun closedWithoutAMessage() {
        val closed = decode("""["CLOSED","sub1"]""") as ClosedMessage
        assertEquals("sub1", closed.subId)
        assertEquals("", closed.message)
        assertNull(closed.params)
    }

    @Test
    fun negErrWithoutAReason() {
        val err = decode("""["NEG-ERR","sub1"]""") as NegErrMessage
        assertEquals("sub1", err.subId)
        assertNull(err.cap)
    }
}
