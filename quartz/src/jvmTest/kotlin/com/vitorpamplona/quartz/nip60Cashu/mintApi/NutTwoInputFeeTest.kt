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

import com.vitorpamplona.quartz.nip60Cashu.token.CashuProof
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

    // --- Per-keyset variant: fees follow each input proof's OWN keyset ---

    private fun proof(
        keysetId: String,
        amount: Long,
    ) = CashuProof(id = keysetId, amount = amount, secret = "s-$keysetId-$amount", c = "c")

    @Test
    fun `proofs on a zero-fee keyset cost nothing even when another keyset charges`() {
        // Reproduces the mint.coinos.io report: the wallet's proofs sit on the
        // mint's old keyset (0 ppk) while the active keyset charges 100 ppk.
        // The fee must be priced from the proofs' own keyset, so it is 0 — not
        // ceil(N*100/1000). Pricing from the active keyset reserved a fee the
        // mint never took, leaving outputs one sat short ("inputs 84 - fees 0
        // vs output (83) are not balanced").
        val oldKeyset = "004f7adf2a04356c"
        val activeKeyset = "007311aa2fa58cc8"
        val feePpkByKeyset = mapOf(oldKeyset to 0L, activeKeyset to 100L)
        val inputs = List(20) { proof(oldKeyset, 4L) } // 80 sat across 20 proofs on the 0-fee keyset

        assertEquals(0L, CashuMintOperations.computeInputFee(inputs, feePpkByKeyset))
    }

    @Test
    fun `proofs on a fee-charging keyset are billed per spec ceiling`() {
        val activeKeyset = "007311aa2fa58cc8"
        val feePpkByKeyset = mapOf(activeKeyset to 100L)
        // 11 inputs * 100 ppk = 1100 → ceil(1100/1000) = 2
        val inputs = List(11) { proof(activeKeyset, 1L) }

        assertEquals(2L, CashuMintOperations.computeInputFee(inputs, feePpkByKeyset))
    }

    @Test
    fun `mixed keysets sum per-input ppk then ceil once`() {
        val zeroKeyset = "004f7adf2a04356c"
        val feeKeyset = "007311aa2fa58cc8"
        val feePpkByKeyset = mapOf(zeroKeyset to 0L, feeKeyset to 100L)
        // 5 fee-charging inputs (5*100=500 ppk) + 5 zero-fee inputs (0) = 500 ppk
        // ceil(500/1000) = 1. Ceiling is applied once to the SUM, not per input.
        val inputs = List(5) { proof(feeKeyset, 1L) } + List(5) { proof(zeroKeyset, 1L) }

        assertEquals(1L, CashuMintOperations.computeInputFee(inputs, feePpkByKeyset))
    }

    @Test
    fun `unknown keyset (mint dropped it) contributes zero`() {
        val feePpkByKeyset = mapOf("known" to 100L)
        val inputs = List(50) { proof("rotated-out-and-gone", 1L) }

        assertEquals(0L, CashuMintOperations.computeInputFee(inputs, feePpkByKeyset))
    }

    @Test
    fun `empty inputs cost nothing`() {
        assertEquals(0L, CashuMintOperations.computeInputFee(emptyList(), mapOf("k" to 100L)))
    }

    // --- Melt headroom: the fee the active keyset charges on the swapped-down
    //     send proofs, which meltToLightning must reserve on top of
    //     amount + fee_reserve. Mirrors CashuMintOperations.activeKeysetInputFeeFor,
    //     which is `computeInputFee(splitAmounts(amount).size, activePpk)`. ---

    private fun activeKeysetFeeFor(
        amount: Long,
        ppk: Long,
    ) = CashuMintOperations.computeInputFee(
        numInputs = CashuMintOperations.splitAmounts(amount).size,
        inputFeePpk = ppk,
    )

    @Test
    fun `melt headroom on a zero-fee active keyset is zero`() {
        assertEquals(0L, activeKeysetFeeFor(amount = 83, ppk = 0L))
    }

    @Test
    fun `melt headroom reserves the active keyset fee — coinos case`() {
        // splitAmounts(83) = [1,2,16,64] → 4 proofs → ceil(4*100/1000) = 1.
        // Without this 1-sat headroom, swapping down to exactly 83 leaves the
        // melt one sat short of amount+fee_reserve+input_fee.
        assertEquals(4, CashuMintOperations.splitAmounts(83).size)
        assertEquals(1L, activeKeysetFeeFor(amount = 83, ppk = 100L))
    }

    @Test
    fun `melt headroom grows with proof count on a fee keyset`() {
        // splitAmounts(1023) = 10 set bits → 10 proofs → ceil(10*100/1000) = 1.
        assertEquals(10, CashuMintOperations.splitAmounts(1023).size)
        assertEquals(1L, activeKeysetFeeFor(amount = 1023, ppk = 100L))
    }
}
