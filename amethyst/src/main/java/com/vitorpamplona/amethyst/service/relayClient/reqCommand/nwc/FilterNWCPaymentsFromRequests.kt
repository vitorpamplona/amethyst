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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.nwc

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip47WalletConnect.events.LnZapPaymentResponseEvent

// The request event id (#e) is a unique 32-byte identifier — sufficient on
// its own to match the response. Adding `authors` or `#p` makes the filter
// strictly stricter at the relay, which causes silent timeouts on services
// or relays that don't set / index those fields the way NIP-47 expects
// (e.g. wallets that omit the `p` tag, relays that don't index single-letter
// tags on ephemeral kinds). Primal's client uses the same minimal filter
// (kinds + #e), and the wallet's identity is still authenticated end-to-end
// by NIP-04 decryption against the per-connection shared secret.
fun filterNWCPaymentsFromRequests(paymentRequests: Set<HexKey>): Filter =
    Filter(
        kinds = listOf(LnZapPaymentResponseEvent.KIND),
        tags = mapOf("e" to paymentRequests.sorted()),
    )
