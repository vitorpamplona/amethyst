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
package com.vitorpamplona.quartz.nip60Cashu.mintApi

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * NUT-02 input-fee math — `ceil(numInputs * inputFeePpk / 1000)`.
 */
class NutTwoInputFeeTest {
    @Test
    fun `null ppk means no fee — older mints`() {
        assertEquals(0L, CashuMintOperations.computeInputFee(numInputs = 10, inputFeePpk = null))
    }

    @Test
    fun `zero ppk means no fee — fee-free mints`() {
        assertEquals(0L, CashuMintOperations.computeInputFee(numInputs = 10, inputFeePpk = 0L))
    }

    @Test
    fun `zero inputs means no fee — degenerate case`() {
        assertEquals(0L, CashuMintOperations.computeInputFee(numInputs = 0, inputFeePpk = 100L))
    }

    @Test
    fun `single input at 1000 ppk rounds to 1 sat`() {
        // 1 * 1000 / 1000 = 1
        assertEquals(1L, CashuMintOperations.computeInputFee(numInputs = 1, inputFeePpk = 1000L))
    }

    @Test
    fun `single input at 1 ppk rounds up to 1 sat — ceiling not floor`() {
        // 1 * 1 / 1000 = 0.001 → ceil → 1
        // The whole point of ceiling division: undercharging by one sat is what
        // mints actually reject as "amount mismatch".
        assertEquals(1L, CashuMintOperations.computeInputFee(numInputs = 1, inputFeePpk = 1L))
    }

    @Test
    fun `999 inputs at 1 ppk rounds up to 1 sat`() {
        // 999 * 1 / 1000 = 0.999 → ceil → 1
        assertEquals(1L, CashuMintOperations.computeInputFee(numInputs = 999, inputFeePpk = 1L))
    }

    @Test
    fun `1000 inputs at 1 ppk equals 1 sat — exact division`() {
        // 1000 * 1 / 1000 = 1
        assertEquals(1L, CashuMintOperations.computeInputFee(numInputs = 1000, inputFeePpk = 1L))
    }

    @Test
    fun `1001 inputs at 1 ppk rounds up to 2 sats`() {
        // 1001 * 1 / 1000 = 1.001 → ceil → 2
        assertEquals(2L, CashuMintOperations.computeInputFee(numInputs = 1001, inputFeePpk = 1L))
    }

    @Test
    fun `typical case — 10 inputs at 100 ppk equals 1 sat`() {
        // 10 * 100 / 1000 = 1.0 → 1
        assertEquals(1L, CashuMintOperations.computeInputFee(numInputs = 10, inputFeePpk = 100L))
    }

    @Test
    fun `large case — 20 inputs at 2500 ppk equals 50 sats`() {
        // 20 * 2500 / 1000 = 50
        assertEquals(50L, CashuMintOperations.computeInputFee(numInputs = 20, inputFeePpk = 2500L))
    }

    @Test
    fun `negative ppk treated as zero — defensive against bad mint payloads`() {
        assertEquals(0L, CashuMintOperations.computeInputFee(numInputs = 10, inputFeePpk = -5L))
    }
}
