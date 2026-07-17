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
package com.vitorpamplona.amethyst.model

import androidx.core.content.edit
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.quartz.experimental.bitchat.identity.GeohashKeyDerivation
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.utils.RandomInstance

/**
 * The account's anonymous, per-geohash chat identities.
 *
 * Geohash channels are location-tagged, so posting under the account's real npub
 * would publish the user's movements tied to their public identity. Instead each
 * cell gets a throwaway key that is unlinkable to the npub (and to the user's key
 * in every other cell). This state object caches the derived keys and owns the
 * seed they come from, keyed to a single account — so switching accounts (or
 * logging out) switches identities with it.
 *
 * The seed is chosen per signer:
 * - **Local key account** → derived from the account private key
 *   ([GeohashKeyDerivation.accountSeed]). Stable across all of the user's devices
 *   and recoverable from the account, while staying publicly unlinkable.
 * - **Remote (NIP-46) / external (NIP-55) signer** → the raw key is unreachable,
 *   so a random 32-byte seed is kept in this account's encrypted storage. Because
 *   the store is scoped to the account's pubkey, two accounts on one device get
 *   different seeds (a global seed would have made their throwaway identities
 *   collide, linking the accounts in every cell).
 */
class GeohashChatIdentityState(
    private val signer: NostrSigner,
) {
    private val lock = Any()
    private val cache = HashMap<String, KeyPair>()

    @Volatile private var cachedDeviceSeed: ByteArray? = null

    @Volatile private var cachedNickname: String? = null

    /**
     * The user's display handle for location chats: a single global nickname, persisted per account.
     * Bitchat carries this as the per-message `["n", …]` tag rather than a kind-0 profile, and kind-20000
     * messages are ephemeral (relays needn't store them), so the only durable home for it is the device.
     * Kept in this account's encrypted storage, so it survives restarts and switches with the account.
     * Empty string means "no nickname set". Reads touch disk on first call — invoke off the main thread.
     */
    fun nickname(): String {
        cachedNickname?.let { return it }
        synchronized(lock) {
            cachedNickname?.let { return it }
            val value = Amethyst.instance.encryptedStorage(signer.pubKey).getString(PREF_NICKNAME, "") ?: ""
            cachedNickname = value
            return value
        }
    }

    /** Persists the global location-chat nickname (trimmed) for this account. */
    fun setNickname(value: String) {
        val trimmed = value.trim()
        synchronized(lock) {
            cachedNickname = trimmed
            Amethyst.instance.encryptedStorage(signer.pubKey).edit { putString(PREF_NICKNAME, trimmed) }
        }
    }

    /** The Nostr key pair to use inside [geohash]. Derivation is cheap but cached; call off the main thread. */
    fun keyPair(geohash: String): KeyPair =
        synchronized(lock) {
            cache.getOrPut(geohash) { GeohashKeyDerivation.deriveKeyPair(seed(), geohash) }
        }

    private fun seed(): ByteArray = accountPrivKey()?.let { GeohashKeyDerivation.accountSeed(it) } ?: deviceSeed()

    private fun accountPrivKey(): ByteArray? = (signer as? NostrSignerInternal)?.keyPair?.privKey

    /** Random per-account seed, used only when the account key is unreachable (bunker / external signer). */
    private fun deviceSeed(): ByteArray {
        cachedDeviceSeed?.let { return it }
        synchronized(lock) {
            cachedDeviceSeed?.let { return it }
            val prefs = Amethyst.instance.encryptedStorage(signer.pubKey)
            val existing = prefs.getString(PREF_KEY, null)
            val seed =
                if (existing != null && existing.length == GeohashKeyDerivation.SEED_SIZE * 2) {
                    existing.hexToByteArray()
                } else {
                    val fresh = RandomInstance.bytes(GeohashKeyDerivation.SEED_SIZE)
                    prefs.edit { putString(PREF_KEY, fresh.toHexKey()) }
                    fresh
                }
            cachedDeviceSeed = seed
            return seed
        }
    }

    companion object {
        private const val PREF_KEY = "geohash_chat_device_seed"
        private const val PREF_NICKNAME = "geohash_chat_nickname"
    }
}
