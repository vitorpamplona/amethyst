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
import com.vitorpamplona.quartz.experimental.bitchat.identity.GeohashKeyDerivation
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.RandomInstance

/**
 * Device-level random seed that all per-geohash chat identities are derived
 * from (see [GeohashKeyDerivation]). Kept out of any account: geohash channel
 * identities are anonymous and must not be linkable to the user's npub, and one
 * seed per device (shared across accounts) matches Bitchat's model.
 *
 * Stored in the app's global encrypted storage, generated once on first use.
 * Callers must be off the main thread (encrypted storage enforces this).
 */
object GeohashChatDeviceSeed {
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

    /** The Nostr key pair this device uses inside [geohash]. */
    fun keyPair(geohash: String) = GeohashKeyDerivation.deriveKeyPair(seed(), geohash)
}
