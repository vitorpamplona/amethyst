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
package com.vitorpamplona.quartz.experimental.bitchat

import com.vitorpamplona.quartz.experimental.bitchat.identity.GeohashKeyDerivation
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GeohashKeyDerivationTest {
    private val seedA = ByteArray(32) { it.toByte() }
    private val seedB = ByteArray(32) { (it + 1).toByte() }

    @Test
    fun derivationIsDeterministicPerSeedAndGeohash() {
        val a = GeohashKeyDerivation.derivePrivateKey(seedA, "u4pruyd")
        val b = GeohashKeyDerivation.derivePrivateKey(seedA, "u4pruyd")
        assertEquals(a.toHexKey(), b.toHexKey())
    }

    @Test
    fun differentGeohashesYieldDifferentKeys() {
        val a = GeohashKeyDerivation.derivePrivateKey(seedA, "u4pruyd")
        val b = GeohashKeyDerivation.derivePrivateKey(seedA, "u4pruye")
        assertNotEquals(a.toHexKey(), b.toHexKey())
    }

    @Test
    fun differentSeedsYieldDifferentKeys() {
        val a = GeohashKeyDerivation.derivePrivateKey(seedA, "u4pruyd")
        val b = GeohashKeyDerivation.derivePrivateKey(seedB, "u4pruyd")
        assertNotEquals(a.toHexKey(), b.toHexKey())
    }

    @Test
    fun derivedKeyIsValidSecp256k1Scalar() {
        val priv = GeohashKeyDerivation.derivePrivateKey(seedA, "u4pruyd")
        assertEquals(32, priv.size)
        assertTrue(Secp256k1Instance.isPrivateKeyValid(priv))
    }

    @Test
    fun deriveKeyPairProducesMatchingPubKey() {
        val pair = GeohashKeyDerivation.deriveKeyPair(seedA, "u4pruyd")
        val expectedPriv = GeohashKeyDerivation.derivePrivateKey(seedA, "u4pruyd")
        assertEquals(expectedPriv.toHexKey(), pair.privKey!!.toHexKey())
        assertEquals(32, pair.pubKey.size)
    }

    @Test
    fun accountSeedIsDeterministicAndAccountSpecific() {
        val accountKey1 = ByteArray(32) { (it * 7 + 1).toByte() }
        val accountKey2 = ByteArray(32) { (it * 7 + 2).toByte() }

        assertEquals(GeohashKeyDerivation.accountSeed(accountKey1).toHexKey(), GeohashKeyDerivation.accountSeed(accountKey1).toHexKey())
        assertNotEquals(GeohashKeyDerivation.accountSeed(accountKey1).toHexKey(), GeohashKeyDerivation.accountSeed(accountKey2).toHexKey())
        assertEquals(32, GeohashKeyDerivation.accountSeed(accountKey1).size)
    }

    @Test
    fun accountDerivedGeohashKeyIsUnlinkableAcrossCellsButStablePerCell() {
        val accountKey = ByteArray(32) { (it + 3).toByte() }
        val seed = GeohashKeyDerivation.accountSeed(accountKey)

        val cellA1 = GeohashKeyDerivation.deriveKeyPair(seed, "u4pruyd")
        val cellA2 = GeohashKeyDerivation.deriveKeyPair(seed, "u4pruyd")
        val cellB = GeohashKeyDerivation.deriveKeyPair(seed, "gcpvj0")

        // Same account + same cell → same identity (stable across devices).
        assertEquals(cellA1.pubKey.toHexKey(), cellA2.pubKey.toHexKey())
        // Same account, different cells → unrelated identities.
        assertNotEquals(cellA1.pubKey.toHexKey(), cellB.pubKey.toHexKey())
        // And none of them is the account key itself.
        assertNotEquals(accountKey.toHexKey(), cellA1.privKey!!.toHexKey())
    }
}
