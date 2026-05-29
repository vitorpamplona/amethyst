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

/**
 * NUT-00 amount split: decompose [amount] into a sorted list of distinct
 * power-of-2 denominations summing to amount.
 *
 *   13  → [1, 4, 8]
 *   100 → [4, 32, 64]
 *   0   → []
 *
 * Cashu mints maintain one mint-key per denomination so amounts are always
 * stored as the user's binary decomposition. This keeps proofs anonymous
 * across users (same denominations) and bounds the keyset size at ~64 keys.
 */
fun splitAmountIntoDenominations(amount: Long): List<Long> {
    require(amount >= 0) { "Amount must be non-negative" }
    if (amount == 0L) return emptyList()
    val out = mutableListOf<Long>()
    var n = amount
    var d = 1L
    while (n > 0) {
        if (n and 1L != 0L) out += d
        n = n shr 1
        d = d shl 1
    }
    return out
}
