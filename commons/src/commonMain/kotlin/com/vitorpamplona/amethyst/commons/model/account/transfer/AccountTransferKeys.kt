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
     * The account's secret key. Excluded from the verbatim preference copy
     * because it is carried deliberately as [AccountTransferEntry.privKeyHex] —
     * withheld here so that exactly one code path decides how keys travel, and
     * the UI can state plainly what the file contains.
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

    /** Route -> last-read timestamp. */
    const val LAST_READ_PER_ROUTE = "last_read_route_per_route"

    const val DISMISSED_POLL_NOTE_IDS = "dismissed_poll_note_ids"

    const val VIEWED_POLL_RESULT_NOTE_IDS = "viewed_poll_result_note_ids"

    /**
     * OTS attestations still awaiting confirmation. The pending request belongs
     * to the device that made it.
     */
    const val PENDING_ATTESTATIONS = "pending_attestations"

    /**
     * Per-device reading history rather than settings. These grow without bound,
     * change on nearly every interaction, and describe what happened on the old
     * phone — carrying them would bloat the file and re-hide things on a device
     * where the user never saw them.
     */
    private val LOCAL_HISTORY =
        setOf(
            LAST_READ_PER_ROUTE,
            DISMISSED_POLL_NOTE_IDS,
            VIEWED_POLL_RESULT_NOTE_IDS,
            PENDING_ATTESTATIONS,
        )

    /**
     * Keys the importer must never overwrite from a bundle. Excluded on export
     * as well, so the file never contains them in the first place.
     *
     * NOSTR_PRIVKEY is here because it is carried out of band as
     * [AccountTransferEntry.privKeyHex], not because it is withheld.
     */
    val EXCLUDED: Set<String> = NIP46_DEVICE_IDENTITY + NOSTR_PRIVKEY + LOCAL_HISTORY

    /**
     * App-wide SharedPreferences files that must NOT travel, keyed by file name
     * (no `.xml`).
     *
     * `amethyst_secure_keys` is EncryptedSharedPreferences behind an Android
     * Keystore master key. That key is hardware-bound and cannot leave the
     * device, so a copy is undecryptable on the target — importing it would only
     * install files the new phone can never open.
     */
    val EXCLUDED_PREFERENCE_FILES: Set<String> = setOf("amethyst_secure_keys")

    /**
     * Keys held back from the app-wide ENCRYPTED preference file (which is read
     * through its decrypting accessor, not copied as bytes).
     *
     * This is the account registry — which accounts exist and which is current.
     * The importer rebuilds it as it adds each account, so importing these
     * verbatim would either resurrect accounts the bundle doesn't carry or
     * point "current account" at one this device doesn't have.
     */
    val EXCLUDED_GLOBAL_KEYS: Set<String> =
        setOf(
            "currently_logged_in_account",
            "all_saved_accounts_info",
            "all_saved_accounts",
        )

    /** True when [key] may be copied to another device. */
    fun isTransferable(key: String): Boolean = key !in EXCLUDED
}
