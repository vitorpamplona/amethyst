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
package com.vitorpamplona.quartz.nip47WalletConnect.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcMethod
import com.vitorpamplona.quartz.utils.ensure

/**
 * NIP-47's `extensions` tag on the kind 13194 info event: the optional NWC
 * extension specs a wallet service supports, space-separated (eg. `02 03 04`).
 *
 * This is how a client learns it may use anything beyond the core command set
 * without guessing. It matters most for request fields a wallet might not
 * understand — sending one to a wallet that never advertised support risks a
 * refusal on a method that would otherwise have worked.
 */
class ExtensionsTag {
    companion object {
        const val TAG_NAME = "extensions"

        // The NWC extension specs (github.com/nostr-wallet-connect/nwc) this library
        // knows about, so a caller names a constant rather than a bare string at each gate.

        /** NWC-02: notifications (kind 23197/23196, `notifications` tag, `payment_received`, `payment_sent`). */
        const val NOTIFICATIONS = "02"

        /** NWC-03: `make_hold_invoice`, `cancel_hold_invoice`, `settle_hold_invoice`, `hold_invoice_accepted`. */
        const val HOLD_INVOICES = "03"

        /** NWC-04: `pay_keysend`. */
        const val KEYSEND = "04"

        /** NWC-05: `list_transactions`. */
        const val TRANSACTION_HISTORY = "05"

        /** NWC-06: `metadata` conventions on invoices and payments. */
        const val METADATA_CONVENTIONS = "06"

        /** NWC-07: `nostrnwc://` deep links for pairing. */
        const val DEEP_LINKS = "07"

        /** NWC-08: client-initiated connection creation. */
        const val CLIENT_INITIATED_CONNECTIONS = "08"

        /**
         * The NWC extension that defines [method], or null when the method is core
         * NIP-47 (`pay_invoice`, `make_invoice`, `lookup_invoice`, `get_balance`,
         * `get_info`) or not assigned to any published extension spec.
         */
        fun forMethod(method: String): String? =
            when (method) {
                NwcMethod.MAKE_HOLD_INVOICE,
                NwcMethod.CANCEL_HOLD_INVOICE,
                NwcMethod.SETTLE_HOLD_INVOICE,
                -> HOLD_INVOICES

                NwcMethod.PAY_KEYSEND -> KEYSEND

                NwcMethod.LIST_TRANSACTIONS -> TRANSACTION_HISTORY

                else -> null
            }

        /**
         * The extensions a wallet service implementing [methods] (and, when non-empty,
         * sending [notificationTypes]) should advertise in its info event, in spec order.
         */
        fun forCapabilities(
            methods: List<String>,
            notificationTypes: List<String>? = null,
        ): List<String> {
            val result = mutableSetOf<String>()
            if (!notificationTypes.isNullOrEmpty() || methods.contains(LEGACY_NOTIFICATIONS_CAPABILITY)) result.add(NOTIFICATIONS)
            methods.forEach { method -> forMethod(method)?.let { result.add(it) } }
            return result.sorted()
        }

        /**
         * Pre-extensions wallets listed the bare word `notifications` among the
         * methods in the info event content to say they send notifications.
         */
        const val LEGACY_NOTIFICATIONS_CAPABILITY = "notifications"

        fun parse(tag: Array<String>): List<String>? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return tag.drop(1)
        }

        // NIP-47 carries the list as ONE space-separated value (eg. ["extensions", "a b c"]);
        // [parse] still tolerates wallets that spread it across several elements.
        fun assemble(extensions: List<String>) = arrayOf(TAG_NAME, extensions.joinToString(" "))
    }
}
