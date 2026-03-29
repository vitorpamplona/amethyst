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
package com.vitorpamplona.quartz.nip60Cashu.token

import androidx.compose.runtime.Immutable

/**
 * Represents the decrypted content of a CashuTokenEvent (kind:7375).
 *
 * @param mint The mint URL the proofs belong to
 * @param proofs Unencoded cashu proofs
 * @param unit The base unit the proofs are denominated in (e.g., "sat", "usd", "eur"). Default: "sat"
 * @param del Token event IDs that were destroyed by the creation of this token
 */
@Immutable
data class TokenContent(
    val mint: String,
    val proofs: List<CashuProof>,
    val unit: String = "sat",
    val del: List<String> = emptyList(),
) {
    fun totalAmount(): Long = proofs.sumOf { it.amount }
}
