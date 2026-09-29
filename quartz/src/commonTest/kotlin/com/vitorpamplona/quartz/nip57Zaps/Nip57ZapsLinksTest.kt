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
package com.vitorpamplona.quartz.nip57Zaps

import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkTarget
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip01Core.links.props.ZapProps
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip57ZapsLinksTest {
    private val id = "0".repeat(64)
    private val me = "f".repeat(64)
    private val sig = "0".repeat(128)

    private val zapped = "e1".repeat(32)
    private val recipient = "b1".repeat(32)
    private val sender = "b2".repeat(32)
    private val address = "30023:$recipient:article"

    // 1 mBTC: 100,000 sats
    private val bolt11 =
        "lnbc1m1pjt9u0qsp553q90pj5mafzv20w45eqavned9tgwhl4q99n9s5ppcw24nzw3zeqpp5002kd3ktym67du86kj665fgaev7ka8ys7j5yz5fg686lr5e2gfkshp5dkk27nnuax05az3pk2r6ytxtvwn5j4xzsq9ajprhc7crjkmgvr3qxqyjw5qcqpjrzjqtzxvfsuxe4l92pf97tt4rcgpy2xalkmlwexh899wqxf83l8nwv4xzh0gvqq89qqqqqqqqlgqqqqq0gqvs9qxpqysgqx5mz04wd7kqu5zhhel9enr036hjrp4gga0nz084p2asjl36a0zmrk6mhqa249zsgqref2rlvhffm73u7rxgr47gden6rugup4ksvpzsqvds4pz"

    @Test
    fun aZapRequestCarriesTheRequestedAmount() {
        val event =
            ZapRequestEvent(
                id,
                me,
                1L,
                arrayOf(
                    arrayOf("relays", "wss://relay.example/"),
                    arrayOf("amount", "21000"),
                    arrayOf("lnurl", "lnurl1dp68gurn8ghj7um5v93kketj9ehx2amn9uh8wetvdskkkmn0wahz7mrww4excup0dajx2mrv92x9xp"),
                    arrayOf("p", recipient),
                    arrayOf("e", zapped),
                    arrayOf("a", address),
                    arrayOf("k", "30023"),
                ),
                "great article",
                sig,
            )
        val msats = ZapProps(21000L)

        assertEquals(
            listOf(
                Link(Relation.ZAP_RECIPIENT, LinkTarget.User(recipient), "p", msats),
                Link(Relation.ZAPPED, LinkTarget.Event(zapped), "e", msats),
                Link(Relation.ZAPPED, LinkTarget.Address(address), "a", msats),
                Link(Relation.TAG, LinkTarget.Tag("k", "30023"), "k"),
            ),
            event.links(),
        )
    }

    @Test
    fun aZapReceiptNamesItsSenderAndThePaidAmount() {
        val event =
            ZapReceiptEvent(
                id,
                "c1".repeat(32),
                1L,
                arrayOf(
                    arrayOf("p", recipient),
                    arrayOf("P", sender),
                    arrayOf("e", zapped),
                    arrayOf("k", "1"),
                    arrayOf("bolt11", bolt11),
                ),
                "",
                sig,
            )
        val msats = ZapProps(100_000_000L)

        assertEquals(
            listOf(
                Link(Relation.ZAP_RECIPIENT, LinkTarget.User(recipient), "p", msats),
                Link(Relation.ZAP_SENDER, LinkTarget.User(sender), "P"),
                Link(Relation.ZAPPED, LinkTarget.Event(zapped), "e", msats),
                Link(Relation.TAG, LinkTarget.Tag("k", "1"), "k"),
            ),
            event.links(),
        )
    }

    @Test
    fun aPrivateZapIsTheHiddenRequest() {
        val event =
            PrivateZapEvent(
                id,
                me,
                1L,
                arrayOf(
                    arrayOf("p", recipient),
                    arrayOf("e", zapped),
                    // only a receipt names a sender
                    arrayOf("P", sender),
                ),
                "psst",
                sig,
            )

        assertEquals(
            listOf(
                Link(Relation.ZAP_RECIPIENT, LinkTarget.User(recipient), "p"),
                Link(Relation.ZAPPED, LinkTarget.Event(zapped), "e"),
            ),
            event.links(),
        )
    }
}
