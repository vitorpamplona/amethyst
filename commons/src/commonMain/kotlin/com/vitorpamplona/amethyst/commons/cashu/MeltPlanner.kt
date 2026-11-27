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
package com.vitorpamplona.amethyst.commons.cashu

/**
 * Assigns invoices to the mints that will melt them — the funding planner behind paying
 * lightning invoices from the cashu wallet.
 *
 * The plan is optimistic: it works from the per-mint balances plus a fee slack, with no
 * network round-trip, because the exact melt cost is only known after quoting. The melt
 * is authoritative: `CashuWalletState.meltInvoiceAt` re-checks under the mint's lock and
 * fails loudly if the proofs fall short meanwhile.
 *
 * A `null` plan means the wallet cannot fund every invoice, and the payment falls through
 * to the next source. Never a partial plan: for a split zap the user saw one amount and
 * tapped once, so paying only some recipients is worse than paying none.
 */
object MeltPlanner {
    /**
     * Head-room reserved per invoice for the melt's LN `fee_reserve` plus the mint's NUT-02
     * input fee: ~2%, with a 10-sat floor. A flat constant would take small zaps and refuse
     * large ones the wallet can pay. The melt checks the real numbers at quote time
     * (`CashuWalletState.validateMeltQuote`).
     */
    fun feeSlackFor(amountSats: Long): Long = maxOf(10L, amountSats / 50L)

    /**
     * Plans which mint melts each invoice.
     *
     * Greedy largest-first packing: the biggest invoice takes the smallest mint that can
     * pay it, which leaves the larger balances for the invoices after it. Each invoice is
     * independent at its mint (its own quote, its own proofs), so a split can draw from
     * several mints with no rebalance.
     *
     * @param amountsSats invoice amounts in whole sats, indexed as the caller's list
     * @param balances spendable sats per mint URL
     * @return mint URL to the indexes of the invoices it should melt, or null
     *   when no assignment covers every invoice with its fee slack
     */
    fun plan(
        amountsSats: List<Long>,
        balances: Map<String, Long>,
    ): Map<String, List<Int>>? {
        if (amountsSats.isEmpty() || balances.isEmpty()) return null

        val remaining = HashMap(balances)
        val assignment = HashMap<String, MutableList<Int>>()

        for ((index, amount) in amountsSats.withIndex().sortedByDescending { it.value }) {
            val need = amount + feeSlackFor(amount)
            if (need <= 0) continue

            val mint =
                remaining.entries
                    .filter { it.value >= need }
                    .minByOrNull { it.value }
                    ?.key
                    ?: return null

            remaining[mint] = remaining.getValue(mint) - need
            assignment.getOrPut(mint) { mutableListOf() }.add(index)
        }

        return assignment
    }
}
