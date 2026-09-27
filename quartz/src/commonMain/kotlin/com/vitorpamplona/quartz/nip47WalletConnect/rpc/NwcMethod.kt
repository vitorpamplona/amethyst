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
package com.vitorpamplona.quartz.nip47WalletConnect.rpc

/**
 * NWC method names. NIP-47 core defines `pay_invoice`, `make_invoice`, `lookup_invoice`,
 * `get_balance` and `get_info`; the rest come from optional NWC extension specs
 * (github.com/nostr-wallet-connect/nwc) — see [com.vitorpamplona.quartz.nip47WalletConnect.tags.ExtensionsTag.forMethod].
 */
object NwcMethod {
    // NIP-47 core
    const val PAY_INVOICE = "pay_invoice"
    const val MAKE_INVOICE = "make_invoice"
    const val LOOKUP_INVOICE = "lookup_invoice"
    const val GET_BALANCE = "get_balance"
    const val GET_INFO = "get_info"

    // NWC-04 keysend payments
    const val PAY_KEYSEND = "pay_keysend"

    // NWC-05 transaction history
    const val LIST_TRANSACTIONS = "list_transactions"

    // Not (yet) in a published NWC spec
    const val GET_BUDGET = "get_budget"
    const val SIGN_MESSAGE = "sign_message"
    const val CREATE_CONNECTION = "create_connection"

    // NWC-03 hold invoices
    const val MAKE_HOLD_INVOICE = "make_hold_invoice"
    const val CANCEL_HOLD_INVOICE = "cancel_hold_invoice"
    const val SETTLE_HOLD_INVOICE = "settle_hold_invoice"

    // nostr-wallet-connect/nwc#2 — generalized payment instructions (BOLT12/BIP321).
    const val PAY = "pay"
    const val RECEIVE = "receive"
}
