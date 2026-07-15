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
package com.vitorpamplona.quartz.experimental.bitchat.identity

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import com.vitorpamplona.quartz.utils.mac.MacInstance
import com.vitorpamplona.quartz.utils.sha256.sha256

/**
 * Derives a stable-but-unlinkable Nostr identity per geohash channel, mirroring
 * Bitchat's location-channel identity scheme.
 *
 * privKey = HMAC-SHA256(deviceSeed, geohashBytes || counterBE), incrementing the
 * counter until the 32-byte output is a valid secp256k1 scalar; on the (astro-
 * nomically unlikely) exhaustion of [MAX_ITERATIONS] it falls back to
 * SHA-256(deviceSeed || geohashBytes).
 *
 * Properties this gives us:
 * - Deterministic per (device seed, geohash), so a user keeps one identity per
 *   area across app restarts without persisting a key per channel.
 * - Unlinkable: the same person in two different geohashes has two unrelated
 *   pubkeys, and neither is derivable from — or linkable to — their main npub.
 *
 * Interop note: the [seed] is a per-install random secret, so the derived keys do
 * NOT match any Bitchat device's keys (nor need to). Each participant only ever
 * publishes under, and is recognised by, their own pubkey — there is no shared
 * key requirement between clients.
 */
object GeohashKeyDerivation {
    const val ALGORITHM = "HmacSHA256"
    const val MAX_ITERATIONS = 10
    const val SEED_SIZE = 32

    fun derivePrivateKey(
        seed: ByteArray,
        geohash: String,
    ): ByteArray {
        val geoBytes = geohash.encodeToByteArray()
        for (counter in 0 until MAX_ITERATIONS) {
            val mac = MacInstance(ALGORITHM, seed)
            mac.update(geoBytes)
            mac.update(counterBytesBE(counter))
            val candidate = mac.doFinal()
            if (Secp256k1Instance.isPrivateKeyValid(candidate)) return candidate
        }
        return sha256(seed + geoBytes)
    }

    fun deriveKeyPair(
        seed: ByteArray,
        geohash: String,
    ) = KeyPair(privKey = derivePrivateKey(seed, geohash))

    private fun counterBytesBE(counter: Int) =
        byteArrayOf(
            (counter ushr 24).toByte(),
            (counter ushr 16).toByte(),
            (counter ushr 8).toByte(),
            counter.toByte(),
        )
}
