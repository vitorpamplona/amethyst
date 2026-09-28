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
package com.vitorpamplona.quartz.nipA3PaymentTargets

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PaymentTargetTagTest {
    @Test
    fun parsesTheSpecExample() {
        val tags =
            arrayOf(
                arrayOf("payto", "bitcoin", "bc1qxq66e0t8d7ugdecwnmv58e90tpry23nc84pg9k"),
                arrayOf("payto", "nano", "nano_1dctqbmqxfppo9pswbm6kg9d4s4mbraqn8i4m7ob9gnzz91aurmuho48jx3c"),
                arrayOf("payto", "unknowntype", "l7tbta5b9xze6ckkfc99uohzxd009b0r"),
            )
        val event = PaymentTargetsEvent("0".repeat(64), "1".repeat(64), 1L, tags, "", "")
        assertEquals(
            listOf(
                PaymentTarget("bitcoin", "bc1qxq66e0t8d7ugdecwnmv58e90tpry23nc84pg9k"),
                PaymentTarget("nano", "nano_1dctqbmqxfppo9pswbm6kg9d4s4mbraqn8i4m7ob9gnzz91aurmuho48jx3c"),
                PaymentTarget("unknowntype", "l7tbta5b9xze6ckkfc99uohzxd009b0r"),
            ),
            event.paymentTargets(),
        )
    }

    @Test
    fun typeIsAlwaysLowercase() {
        assertEquals(PaymentTarget("bitcoin", "bc1q"), PaymentTargetTag.parse(arrayOf("payto", "Bitcoin", "bc1q")))
        assertContentEquals(arrayOf("payto", "cashme", "\$Vitor"), PaymentTargetTag.assemble(PaymentTarget("CashMe", "\$Vitor")))
    }

    @Test
    fun rejectsIncompleteTags() {
        assertNull(PaymentTargetTag.parse(arrayOf("payto", "bitcoin")))
        assertNull(PaymentTargetTag.parse(arrayOf("payto", "", "bc1q")))
        assertNull(PaymentTargetTag.parse(arrayOf("payto", "bitcoin", "")))
        assertNull(PaymentTargetTag.parse(arrayOf("r", "bitcoin", "bc1q")))
    }
}
