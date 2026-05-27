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
import com.vitorpamplona.quartz.nip60Cashu.mintApi.DleqProofDto

/**
 * Represents an unencoded cashu proof.
 *
 * [witness] is the NUT-11 unlock witness (a JSON string `{"signatures":[…]}`)
 * required when spending a P2PK-locked proof. Always null on freshly minted
 * unlocked proofs; populated by the wallet right before submitting a swap that
 * spends locked proofs.
 *
 * [dleq] is the NUT-12 DLEQ proof + blinding factor `(e, s, r)` we retain
 * after verifying the mint's signature. Populated by [CashuMintOperations]
 * on every freshly-minted / freshly-swapped proof when the mint emits the
 * dleq field. Forwarded onward when this proof gets serialised into a
 * cashuB token or kind:9321 nutzap so the recipient ("Carol") can verify
 * the proof against the mint's keyset key BEFORE attempting to spend it.
 * Null on legacy mints that don't emit NUT-12 or on imported proofs that
 * were stripped of their DLEQ in transit.
 */
@Immutable
data class CashuProof(
    val id: String,
    val amount: Long,
    val secret: String,
    val c: String,
    val witness: String? = null,
    val dleq: DleqProofDto? = null,
)
