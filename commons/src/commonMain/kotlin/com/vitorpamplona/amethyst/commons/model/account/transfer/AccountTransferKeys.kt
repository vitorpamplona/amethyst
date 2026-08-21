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
package com.vitorpamplona.amethyst.commons.model.account.transfer

/**
 * Which preference keys are allowed to travel to another device.
 *
 * The transfer file copies the account's preference map wholesale (see
 * [AccountTransferBundle]), so this is the one place that says "not that one".
 * Keep the list short and justify every entry: anything excluded here is
 * something the user has to set up again by hand on the new phone.
 *
 * The names below are the ACTUAL preference keys, not copies of them: Amethyst's
 * `PrefKeys` declares its constants as aliases of these. A copy would let the
 * two drift apart on a rename, and the failure mode of that drift is a secret
 * key quietly ending up in an exported file.
 */
object AccountTransferKeys {
    /**
     * The account's secret key. Handled out of band as
     * [AccountTransferEntry.privKeyHex] so that including it stays an explicit,
     * separately-consented choice rather than a side effect of exporting
     * settings — a transfer file the user meant as "my settings" must not turn
     * out to carry their identity.
     */
    const val NOSTR_PRIVKEY = "nostr_privkey"

    /**
     * Keys that identify THIS device as a NIP-46 bunker to apps that paired
     * with it. Copying them would leave two phones answering as the same
     * bunker with divergent request-id histories, so the new phone re-pairs
     * instead. The user-facing "act as a signer" toggle is not in this set and
     * does travel.
     */
    const val NIP46_BUNKER_SECRET = "nip46BunkerSecret"
    const val NIP46_TRANSPORT_KEY = "nip46TransportKey"
    const val NIP46_SEEN_IDS = "nip46SeenRequestIds"

    private val NIP46_DEVICE_IDENTITY =
        setOf(
            NIP46_BUNKER_SECRET,
            NIP46_TRANSPORT_KEY,
            NIP46_SEEN_IDS,
        )

    /**
     * Keys the importer must never overwrite from a bundle. Excluded on export
     * as well, so the file never contains them in the first place.
     */
    val EXCLUDED: Set<String> = NIP46_DEVICE_IDENTITY + NOSTR_PRIVKEY

    /** True when [key] may be copied to another device. */
    fun isTransferable(key: String): Boolean = key !in EXCLUDED
}
