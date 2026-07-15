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
package com.vitorpamplona.amethyst.service.geohash

import androidx.core.content.edit
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.quartz.experimental.bitchat.identity.GeohashKeyDerivation
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.utils.RandomInstance

/**
 * Resolves the anonymous, per-geohash chat identity for the current user.
 *
 * Geohash channels are location-tagged, so posting under the account's real npub
 * would publish the user's movements tied to their public identity. Instead each
 * cell gets a throwaway key that is unlinkable to the npub (and to the user's key
 * in other cells).
 *
 * The seed those keys derive from is chosen per signer:
 * - **Local key account** → derived from the account private key
 *   ([GeohashKeyDerivation.accountSeed]). The identity is then stable across all
 *   of the user's devices and recoverable from the account, while staying
 *   publicly unlinkable.
 * - **Remote (NIP-46) / external (NIP-55) signer** → we can't reach the raw key,
 *   so we fall back to [DeviceSeed]: a random 32-byte seed kept in the app's
 *   global encrypted storage (per-device, generated once).
 */
object GeohashChatIdentity {
    /** The Nostr key pair to use inside [geohash] for [account]. Call off the main thread. */
    fun keyPair(
        account: Account,
        geohash: String,
    ): KeyPair {
        val seed =
            accountPrivKey(account)?.let { GeohashKeyDerivation.accountSeed(it) }
                ?: DeviceSeed.seed()
        return GeohashKeyDerivation.deriveKeyPair(seed, geohash)
    }

    private fun accountPrivKey(account: Account): ByteArray? = (account.signer as? NostrSignerInternal)?.keyPair?.privKey

    /**
     * Fallback random device seed, used only when the account key is unreachable
     * (bunker / external signer).
     */
    object DeviceSeed {
        private const val PREF_KEY = "geohash_chat_device_seed"

        @Volatile private var cached: ByteArray? = null

        fun seed(): ByteArray {
            cached?.let { return it }
            synchronized(this) {
                cached?.let { return it }
                val prefs = Amethyst.instance.encryptedStorage()
                val existing = prefs.getString(PREF_KEY, null)
                val seed =
                    if (existing != null && existing.length == GeohashKeyDerivation.SEED_SIZE * 2) {
                        existing.hexToByteArray()
                    } else {
                        val fresh = RandomInstance.bytes(GeohashKeyDerivation.SEED_SIZE)
                        prefs.edit { putString(PREF_KEY, fresh.toHexKey()) }
                        fresh
                    }
                cached = seed
                return seed
            }
        }
    }
}
