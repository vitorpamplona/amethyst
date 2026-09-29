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
package com.vitorpamplona.quartz.nipB1Bolt12Zaps

import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkTarget
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.intent.Bolt12ZapIntentEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.zap.Bolt12ZapEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class NipB1Bolt12ZapsLinksTest {
    private val id = "0".repeat(64)
    private val author = "1".repeat(64)
    private val sig = "0".repeat(128)
    private val recipient = "a".repeat(64)
    private val payer = "b".repeat(64)

    @Test
    fun zapNamesRecipientPayerAndTarget() {
        val zapped = "2".repeat(64)
        val event =
            Bolt12ZapEvent(
                id,
                author,
                1,
                arrayOf(
                    arrayOf("description", "{}"),
                    arrayOf("p", recipient),
                    arrayOf("amount", "21000"),
                    arrayOf("P", payer),
                    arrayOf("e", zapped),
                    arrayOf("k", "1"),
                ),
                "",
                sig,
            )
        val msats = mapOf("msats" to 21_000L)
        assertEquals(
            listOf(
                Link(Relation.ZAP_RECIPIENT, LinkTarget.User(recipient), "p", msats),
                Link(Relation.ZAP_SENDER, LinkTarget.User(payer), "P"),
                Link(Relation.ZAPPED, LinkTarget.Event(zapped), "e", msats),
                Link(Relation.TAG, LinkTarget.Tag("k", "1"), "k"),
            ),
            event.links(),
        )
    }

    @Test
    fun intentNamesTheSameTargets() {
        val article = "30023:$recipient:post"
        val event =
            Bolt12ZapIntentEvent(id, payer, 1, arrayOf(arrayOf("p", recipient), arrayOf("amount", "5000"), arrayOf("a", article), arrayOf("k", "30023")), "", sig)
        val msats = mapOf("msats" to 5_000L)
        assertEquals(
            listOf(
                Link(Relation.ZAP_RECIPIENT, LinkTarget.User(recipient), "p", msats),
                Link(Relation.ZAPPED, LinkTarget.Address(article), "a", msats),
                Link(Relation.TAG, LinkTarget.Tag("k", "30023"), "k"),
            ),
            event.links(),
        )
    }
}
