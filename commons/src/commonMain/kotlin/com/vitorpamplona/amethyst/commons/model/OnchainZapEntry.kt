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
package com.vitorpamplona.amethyst.commons.model

import androidx.compose.runtime.Stable

/**
 * Per-(note, txid) verified NIP-BC onchain zap state.
 *
 * @property source The OnchainZapEvent note that contributed this entry. `source.author` is the
 *                  sender shown in the reactions gallery. When pending → confirmed upgrades
 *                  happen, the upgrading event's note replaces the existing `source`.
 * @property verifiedSats Satoshis verified to have paid the recipient's derived Taproot
 *                        address on chain. NEVER the sender-claimed `amount` tag.
 * @property confirmed True when the transaction has at least one confirmation. Unconfirmed
 *                     zaps are tracked but excluded from aggregate totals per the NIP-BC
 *                     spec ("Unconfirmed transactions MAY be displayed as pending...
 *                     SHOULD either exclude them from aggregate totals or clearly label
 *                     them as pending").
 */
@Stable
data class OnchainZapEntry(
    val source: Note,
    val verifiedSats: Long,
    val confirmed: Boolean,
)
