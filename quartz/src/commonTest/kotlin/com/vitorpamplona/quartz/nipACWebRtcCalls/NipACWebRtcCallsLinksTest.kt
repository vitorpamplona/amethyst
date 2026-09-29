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
package com.vitorpamplona.quartz.nipACWebRtcCalls

import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkTarget
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallHangupEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallIceCandidateEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallOfferEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class NipACWebRtcCallsLinksTest {
    private val me = "0".repeat(64)
    private val alice = "1".repeat(64)
    private val bob = "2".repeat(64)

    @Test
    fun groupOfferNamesEveryCallee() {
        val tags =
            arrayOf(
                arrayOf("p", alice),
                arrayOf("p", bob),
                arrayOf("call-id", "call-1"),
                arrayOf("call-type", "voice"),
                arrayOf("p", "not-a-key"),
            )
        assertEquals(
            listOf(
                Link(Relation.RECIPIENT, LinkTarget.User(alice), "p"),
                Link(Relation.RECIPIENT, LinkTarget.User(bob), "p"),
            ),
            CallOfferEvent(me, me, 0, tags, "sdp", me).links(),
        )
    }

    @Test
    fun everySignallingKindLinksItsPeer() {
        val tags = arrayOf(arrayOf("p", alice), arrayOf("call-id", "call-1"))
        val expected = listOf(Link(Relation.RECIPIENT, LinkTarget.User(alice), "p"))
        assertEquals(expected, CallIceCandidateEvent(me, me, 0, tags, "{}", me).links())
        assertEquals(expected, CallHangupEvent(me, me, 0, tags, "", me).links())
    }
}
