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
package com.vitorpamplona.amethyst.commons.model.preferences

import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * The per-account values that used to live in the encrypted
 * `secret_keeper_<npub>` file, other than the private key itself.
 *
 * Everything here stays encrypted, including the parts that are not obviously
 * secret. `defaultPaymentSourceId` is only an identifier and
 * `nip46SeenRequestIds` only request ids, but both were encrypted before and
 * both say something about what the account does — moving them to a plain
 * store would be a quiet downgrade, so they keep the protection they had.
 *
 * The two `legacy` fields are read-only leftovers the app still migrates from;
 * they are carried across so an upgrade does not strand a wallet that only
 * exists in the old shape.
 */
data class AccountSecrets(
    val nip46SignerEnabled: Boolean = false,
    val nip46BunkerSecret: String = "",
    val nip46TransportKey: String = "",
    val nip46SeenRequestIds: Set<String> = emptySet(),
    val nwcWalletsJson: String? = null,
    val clinkDebitWalletsJson: String? = null,
    val defaultPaymentSourceId: String? = null,
    val legacyDefaultNwcWalletId: String? = null,
    val legacyZapPaymentRequestServer: String? = null,
)

/**
 * Keys for [AccountSecrets] inside an [EncryptedDataStore].
 *
 * All of them are string keys: the store encrypts strings, so a boolean and a
 * set are encoded here rather than stored in typed keys that would sit in
 * cleartext beside the encrypted values.
 */
internal object AccountSecretKeys {
    val nip46SignerEnabled = stringPreferencesKey("nip46SignerEnabled")
    val nip46BunkerSecret = stringPreferencesKey("nip46BunkerSecret")
    val nip46TransportKey = stringPreferencesKey("nip46TransportKey")
    val nip46SeenRequestIds = stringPreferencesKey("nip46SeenRequestIds")
    val nwcWallets = stringPreferencesKey("nwcWallets")
    val clinkDebitWallets = stringPreferencesKey("clinkDebitWallets")
    val defaultPaymentSourceId = stringPreferencesKey("defaultPaymentSourceId")
    val legacyDefaultNwcWalletId = stringPreferencesKey("defaultNwcWalletId")
    val legacyZapPaymentRequestServer = stringPreferencesKey("zapPaymentServer")

    /** Records that the one-off copy out of the legacy file has run for this account. */
    val migrated = stringPreferencesKey("migrated.accountSecrets")

    /**
     * Sets are stored newline-joined rather than as JSON.
     *
     * The members are nostr event ids — hex, so they cannot contain a newline —
     * which makes the round trip exact without pulling a serializer into the
     * encryption path.
     */
    const val SET_SEPARATOR = "\n"
}
