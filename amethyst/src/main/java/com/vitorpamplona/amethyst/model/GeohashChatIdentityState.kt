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
import com.vitorpamplona.amethyst.LegacySharedPreferences
import com.vitorpamplona.amethyst.accountSecretsStore
import com.vitorpamplona.amethyst.commons.model.preferences.GeohashIdentitySecrets
import com.vitorpamplona.amethyst.commons.model.preferences.readLegacyGeohashIdentity
import com.vitorpamplona.quartz.experimental.bitchat.identity.GeohashKeyDerivation
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.utils.RandomInstance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

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
    private val scope: CoroutineScope,
) {
    /**
     * Guards seed creation as well as the key cache: two callers racing into
     * [deviceSeed] must not mint two different seeds, or the loser's cells get
     * identities the next launch cannot reproduce. A [Mutex] rather than
     * `synchronized`, because the store reads it protects are suspending.
     */
    private val mutex = Mutex()
    private val cache = ConcurrentHashMap<String, KeyPair>()

    /**
     * The npub the current store is keyed by.
     *
     * The legacy file is keyed by the pubkey *hex* — the old code passed
     * `signer.pubKey` where every other caller passes an npub, so the identity
     * lived in `secret_keeper_<hex>`, a different file from the account's own
     * `secret_keeper_<npub>`. The copy below reads that file and writes the
     * npub-keyed store, which is what folds this orphan back in with the rest.
     */
    private val npub by lazy { signer.pubKey.hexToByteArray().toNpub() }

    @Volatile private var loaded: GeohashIdentitySecrets? = null

    /** What `secret_keeper_<pubkey hex>` holds. Touches disk; callers are off the main thread. */
    private fun legacy(): GeohashIdentitySecrets = readLegacyGeohashIdentity(LegacySharedPreferences(Amethyst.instance.encryptedStorage(signer.pubKey)))

    /** The stored identity, copying it out of the legacy file the first time. */
    private suspend fun current(): GeohashIdentitySecrets {
        loaded?.let { return it }
        return accountSecretsStore.readGeohashIdentity(npub, legacy()).also { loaded = it }
    }

    private suspend fun persist(value: GeohashIdentitySecrets) {
        loaded = value
        accountSecretsStore.mirrorGeohashIdentity(npub, value)
    }

    /**
     * The user's display handle for location chats: a single global nickname, persisted per account.
     * Bitchat carries this as the per-message `["n", …]` tag rather than a kind-0 profile, and kind-20000
     * messages are ephemeral (relays needn't store them), so the only durable home for it is the device.
     * Empty string means "no nickname set".
     */
    suspend fun nickname(): String = current().nickname ?: ""

    /**
     * Persists the global location-chat nickname (trimmed) for this account.
     *
     * Fire-and-forget on the account scope, which is what the SharedPreferences
     * `edit {}` this replaced already did — the caller is a click handler on the
     * main thread and the write is not something it waits for.
     */
    fun setNickname(value: String) {
        val trimmed = value.trim()
        scope.launch {
            persist(current().copy(nickname = trimmed))
            // Mirrored, not moved: the legacy file stays readable until the
            // legacy writes are retired app-wide, so a rollback keeps the handle.
            Amethyst.instance.encryptedStorage(signer.pubKey).edit { putString(PREF_NICKNAME, trimmed) }
        }
    }

    /** The Nostr key pair to use inside [geohash]. Derivation is cheap but cached. */
    suspend fun keyPair(geohash: String): KeyPair {
        cache[geohash]?.let { return it }

        return mutex.withLock {
            cache[geohash] ?: GeohashKeyDerivation.deriveKeyPair(seed(), geohash).also { cache[geohash] = it }
        }
    }

    /** Call under [mutex]. */
    private suspend fun seed(): ByteArray = accountPrivKey()?.let { GeohashKeyDerivation.accountSeed(it) } ?: deviceSeed()

    private fun accountPrivKey(): ByteArray? = (signer as? NostrSignerInternal)?.keyPair?.privKey

    /**
     * Random per-account seed, used only when the account key is unreachable (bunker / external signer).
     *
     * Call under [mutex]: minting a second seed for an account that already has
     * one would change every throwaway identity it has ever used.
     */
    private suspend fun deviceSeed(): ByteArray {
        val stored = current().deviceSeed
        if (stored != null && stored.length == GeohashKeyDerivation.SEED_SIZE * 2) return stored.hexToByteArray()

        val fresh = RandomInstance.bytes(GeohashKeyDerivation.SEED_SIZE)
        persist(current().copy(deviceSeed = fresh.toHexKey()))
        Amethyst.instance.encryptedStorage(signer.pubKey).edit { putString(PREF_KEY, fresh.toHexKey()) }
        return fresh
    }

    companion object {
        private const val PREF_KEY = "geohash_chat_device_seed"
        private const val PREF_NICKNAME = "geohash_chat_nickname"
    }
}
