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
package com.vitorpamplona.quartz.nip60Cashu.bdhke

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.secp256k1.ECPoint
import com.vitorpamplona.quartz.utils.secp256k1.Fe4
import com.vitorpamplona.quartz.utils.secp256k1.KeyCodec
import com.vitorpamplona.quartz.utils.secp256k1.MutablePoint
import com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test vectors taken from the cashubtc reference test suite — see
 * https://github.com/cashubtc/nutshell/blob/main/tests/test_crypto.py and
 * https://github.com/cashubtc/nuts/blob/main/tests/00-tests.md.
 */
class BdhkeTest {
    // ============================================================
    // hash_to_curve (NUT-00 vectors)
    // ============================================================

    @Test
    fun hashToCurveZeroes() {
        val x = ByteArray(32) // 32 zero bytes
        val point = Bdhke.hashToCurve(x)
        val compressed = compress(point)
        assertEquals(
            "024cce997d3b518f739663b757deaec95bcd9473c30a14ac2fd04023a739d1a725",
            compressed.toHexKey(),
        )
    }

    @Test
    fun hashToCurveTrailingOne() {
        val x = ByteArray(32).also { it[31] = 0x01 }
        val point = Bdhke.hashToCurve(x)
        val compressed = compress(point)
        assertEquals(
            "022e7158e11c9506f1aa4248bf531298daa7febd6194f003edcd9b93ade6253acf",
            compressed.toHexKey(),
        )
    }

    @Test
    fun hashToCurveTrailingTwo() {
        val x = ByteArray(32).also { it[31] = 0x02 }
        val point = Bdhke.hashToCurve(x)
        val compressed = compress(point)
        assertEquals(
            "026cdbe15362df59cd1dd3c9c11de8aedac2106eca69236ecd9fbe117af897be4f",
            compressed.toHexKey(),
        )
    }

    // ============================================================
    // BDHKE round-trip
    // ============================================================

    @Test
    fun blindUnblindRoundTrip() {
        // Mint private key a = 1 → K = G
        val a = "0000000000000000000000000000000000000000000000000000000000000001".hexToByteArray()
        val k = "0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798".hexToByteArray()

        val secret = "test_message".encodeToByteArray()
        // Blinding factor r = 1
        val r = "0000000000000000000000000000000000000000000000000000000000000001".hexToByteArray()

        // B_ = Y + r·G = Y + G
        val bTick = Bdhke.blind(secret, r)
        // C_ = a · B_ = 1 · B_ = B_
        val cTick = Bdhke.sign(bTick, a)
        assertEquals(bTick.toHexKey(), cTick.toHexKey())

        // C = C_ - r·K = B_ - G = Y
        val c = Bdhke.unblind(cTick, r, k)

        // Verify: a·Y should equal C
        assertTrue(Bdhke.verify(secret, c, a))
    }

    @Test
    fun blindUnblindRoundTripRandomKey() {
        // Use a non-trivial mint key
        val a = "7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f".hexToByteArray()
        // K = a·G — derive
        val k = Secp256k1.pubKeyCompress(Secp256k1.pubkeyCreate(a))

        val secret = "another secret".encodeToByteArray()
        val r = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef".hexToByteArray()

        val bTick = Bdhke.blind(secret, r)
        val cTick = Bdhke.sign(bTick, a)
        val c = Bdhke.unblind(cTick, r, k)

        assertTrue(Bdhke.verify(secret, c, a))
    }

    @Test
    fun blindUnblindRejectsInvalidBlindFactor() {
        try {
            Bdhke.blind("secret".encodeToByteArray(), ByteArray(31))
            error("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    // ============================================================
    // Determinism — same inputs ⇒ same outputs
    // ============================================================

    @Test
    fun hashToCurveIsDeterministic() {
        val x = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef".hexToByteArray()
        val p1 = compress(Bdhke.hashToCurve(x))
        val p2 = compress(Bdhke.hashToCurve(x))
        assertEquals(p1.toHexKey(), p2.toHexKey())
    }

    private fun compress(p: MutablePoint): ByteArray {
        val x = Fe4()
        val y = Fe4()
        require(ECPoint.toAffine(p, x, y))
        return KeyCodec.serializeCompressed(x, y)
    }
}
