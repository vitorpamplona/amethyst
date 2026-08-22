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
package com.vitorpamplona.amethyst.accountTransfer

import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.BuildConfig
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.commons.model.account.transfer.AccountTransferBundle
import com.vitorpamplona.amethyst.commons.model.account.transfer.AccountTransferEntry
import com.vitorpamplona.amethyst.commons.model.account.transfer.AccountTransferEnvelope
import com.vitorpamplona.amethyst.commons.model.account.transfer.AccountTransferStores
import com.vitorpamplona.amethyst.commons.model.account.transfer.isWellFormedNpub
import com.vitorpamplona.amethyst.commons.model.account.transfer.keyMatchesNpub
import com.vitorpamplona.amethyst.commons.model.account.transfer.sanitizeCounters
import com.vitorpamplona.amethyst.model.nip60Cashu.CashuPreferences
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Builds and applies the encrypted file that moves an account to a new phone.
 *
 * Covers only what relays don't: the profile, follows, relay lists, mute list
 * and the NIP-78 settings blob all come back from the user's own events on
 * login, and the settings that were device-only but portable now ride that blob
 * too. What is left is device state — wallet connections, NUT-13 counters, Tor
 * config, read markers — plus, at the user's explicit request, the secret key.
 *
 * See `amethyst/plans/2026-08-21-account-migration-new-phone.md`.
 */
object AccountTransferService {
    /**
     * Builds a transfer file for [npubs] — typically every saved account.
     *
     * Secret keys are always included. The file is meant to move an account, and
     * an account without its key is not moved: the user would land on the new
     * phone with their settings and no way to post. The password is what
     * protects it, which is why the UI insists on one and says plainly what the
     * file contains.
     *
     * Accounts that sign through an external app (NIP-55) have no key here to
     * carry. Their signer package travels so the new phone knows which app to
     * ask for, but the user has to reconnect it there — the pairing is between
     * that app and this device.
     */
    suspend fun export(
        npubs: List<String>,
        password: String,
    ): ByteArray =
        withContext(Dispatchers.IO) {
            val context = Amethyst.instance.appContext
            val accounts =
                npubs.map { npub ->
                    AccountTransferEntry(
                        npub = npub,
                        privKeyHex = LocalPreferences.exportPrivateKey(npub),
                        externalSignerPackageName = LocalPreferences.exportSignerPackageName(npub),
                        preferences = LocalPreferences.exportAccountPreferences(npub),
                        cashuKeysetCounters = CashuPreferences.forAccount(npub).exportCounters(),
                        marmotMessages = MarmotArchiveTransfer.export(context.filesDir, npub),
                    )
                }

            AccountTransferEnvelope.encrypt(
                bundle =
                    AccountTransferBundle(
                        createdAt = TimeUtils.now(),
                        appVersion = BuildConfig.VERSION_NAME,
                        accounts = accounts,
                        globalPreferences = LocalPreferences.exportGlobalPreferences(),
                        sharedPreferences = AppWideStoreTransfer.exportPreferenceFiles(context),
                        files = AppWideStoreTransfer.exportFiles(context),
                    ),
                password = password,
            )
        }

    /** Entries this device will actually import — the rest carry an npub it will not create a record for. */
    fun importableAccounts(bundle: AccountTransferBundle) = bundle.accounts.filter { isWellFormedNpub(it.npub) && it.keyMatchesNpub() }

    /** Accounts in [bundle] that will need their external signer reconnected here. */
    fun accountsNeedingReconnect(bundle: AccountTransferBundle) = importableAccounts(bundle).filter { it.privKeyHex == null && it.externalSignerPackageName != null }

    /**
     * Decrypts [bytes] without applying anything, so the UI can show what the
     * file holds — and prove the password — before the user commits to it.
     *
     * @throws AccountTransferEnvelope.InvalidTransferFile on a wrong password,
     * a damaged file, or one this build is too old to read.
     */
    suspend fun preview(
        bytes: ByteArray,
        password: String,
    ): AccountTransferBundle =
        withContext(Dispatchers.IO) {
            AccountTransferEnvelope.decrypt(bytes, password)
        }

    /**
     * Applies a previewed bundle.
     *
     * Additive: accounts already on this device keep their existing keys and any
     * preference the bundle doesn't mention, and cashu counters merge upward
     * rather than being assigned. Importing the same file twice is therefore a
     * no-op, and importing an older file cannot undo newer local state.
     *
     * @param includePermissions restore the consent records too — which apps may
     * sign, which relays to authenticate to, what napplets may do. Only safe for
     * a file the user made themselves; see [AccountTransferStores.GUARDED_STORES].
     */
    suspend fun import(
        bundle: AccountTransferBundle,
        includePermissions: Boolean,
    ) {
        withContext(Dispatchers.IO) {
            val context = Amethyst.instance.appContext

            // Gated with the rest: this file's payload is UiSettings, which decides
            // whether images, link previews and avatars are fetched automatically —
            // i.e. whether the device reaches out to hosts a bundle chose.
            if (includePermissions) LocalPreferences.importGlobalPreferences(bundle.globalPreferences)
            AppWideStoreTransfer.importPreferenceFiles(context, bundle.sharedPreferences)
            AppWideStoreTransfer.importFiles(context, bundle.files, includePermissions)

            bundle.accounts.forEach { entry ->
                // The npub becomes a preference file name and a saved-account
                // identity, and the bundle is untrusted input. See isWellFormedNpub.
                if (!isWellFormedNpub(entry.npub)) {
                    Log.w("AccountTransfer") { "Skipping a bundle entry whose npub is not canonical" }
                    return@forEach
                }

                // A key that does not belong to the npub it is filed under is not
                // something a real export produces, and honouring it would replace
                // that account's identity. See keyMatchesNpub.
                if (!entry.keyMatchesNpub()) {
                    Log.w("AccountTransfer") { "Skipping a bundle entry whose key does not match its npub" }
                    return@forEach
                }

                LocalPreferences.importAccount(entry)
                CashuPreferences.forAccount(entry.npub).importCounters(sanitizeCounters(entry.cashuKeysetCounters))

                // Guarded with the rest: MIP-03 inner events are unsigned rumors
                // whose authenticity came from the MLS credential check when they
                // were first decrypted, and that cannot be re-established from a
                // file. An archive is therefore trusted exactly as much as the
                // bundle carrying it, and a hostile one could fabricate an entire
                // conversation attributed to anyone in the group.
                if (includePermissions) {
                    MarmotArchiveTransfer.import(context.filesDir, entry.npub, entry.marmotMessages)
                }
            }
        }
    }
}
