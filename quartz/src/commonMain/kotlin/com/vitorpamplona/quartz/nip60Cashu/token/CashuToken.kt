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
 * A NUT-00 out-of-band cashu token (`cashuA` v3 / `cashuB` v4) decoded from its
 * string form, ready to display or redeem.
 *
 * Distinct from [TokenContent], which models the NIP-60 kind:7375 *wallet*
 * token stored inside an encrypted event. This type is what a user receives
 * pasted into a note or DM. Produced by [CashuTokenB64Parser] and serialised
 * back out by [V4Encoder].
 */
@Immutable
data class CashuToken(
    val token: String,
    val mint: String,
    val totalAmount: Long,
    val proofs: List<CashuProof>,
    /** NUT-00 currency unit ("sat" when absent). Non-sat units are denominated in minor units (e.g. usd = cents). */
    val unit: String? = null,
    /** Free-text memo the sender attached to the token. */
    val memo: String? = null,
)
