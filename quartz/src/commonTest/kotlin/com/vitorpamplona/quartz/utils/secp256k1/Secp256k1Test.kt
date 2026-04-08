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
package com.vitorpamplona.quartz.utils.secp256k1

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Secp256k1Test {
    // ============================================================
    // secKeyVerify
    // ============================================================

    @Test
    fun verifyValidPrivateKey() {
        assertTrue(
            Secp256k1.secKeyVerify(
                "67E56582298859DDAE725F972992A07C6C4FB9F62A8FFF58CE3CA926A1063530"
                    .hexToByteArray(),
            ),
        )
    }

    @Test
    fun verifyInvalidPrivateKeyWrongSize() {
        assertFalse(
            Secp256k1.secKeyVerify(
                "67E56582298859DDAE725F972992A07C6C4FB9F62A8FFF58CE3CA926A106353001"
                    .hexToByteArray(),
            ),
        )
    }

    @Test
    fun verifyInvalidPrivateKeyCurveOrder() {
        assertFalse(
            Secp256k1.secKeyVerify(
                "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141"
                    .hexToByteArray(),
            ),
        )
    }

    @Test
    fun verifyInvalidPrivateKeyZero() {
        assertFalse(
            Secp256k1.secKeyVerify(
                "0000000000000000000000000000000000000000000000000000000000000000"
                    .hexToByteArray(),
            ),
        )
    }

    // ============================================================
    // pubkeyCreate
    // ============================================================

    @Test
    fun createValidPublicKey() {
        val pubkey =
            Secp256k1.pubkeyCreate(
                "67E56582298859DDAE725F972992A07C6C4FB9F62A8FFF58CE3CA926A1063530"
                    .hexToByteArray(),
            )
        assertEquals(
            "04c591a8ff19ac9c4e4e5793673b83123437e975285e7b442f4ee2654dffca5e2d" +
                "2103ed494718c697ac9aebcfd19612e224db46661011863ed2fc54e71861e2a6",
            pubkey.toHexKey(),
        )
    }

    @Test
    fun createPublicKeyFromKeyOne() {
        val pubkey =
            Secp256k1.pubkeyCreate(
                "0000000000000000000000000000000000000000000000000000000000000001"
                    .hexToByteArray(),
            )
        // G itself
        assertEquals(
            "0479be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798" +
                "483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8",
            pubkey.toHexKey(),
        )
    }

    @Test
    fun createPublicKeyFromKeyThree() {
        val pubkey =
            Secp256k1.pubkeyCreate(
                "0000000000000000000000000000000000000000000000000000000000000003"
                    .hexToByteArray(),
            )
        val compressed = Secp256k1.pubKeyCompress(pubkey)
        // BIP-340 test vector 0 public key (x-only)
        assertEquals(
            "02f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9",
            compressed.toHexKey(),
        )
    }

    // ============================================================
    // pubKeyCompress
    // ============================================================

    @Test
    fun compressPublicKey() {
        val compressed =
            Secp256k1.pubKeyCompress(
                (
                    "04C591A8FF19AC9C4E4E5793673B83123437E975285E7B442F4EE2654DFFCA5E2D" +
                        "2103ED494718C697AC9AEBCFD19612E224DB46661011863ED2FC54E71861E2A6"
                ).hexToByteArray(),
            )
        assertEquals(
            "02c591a8ff19ac9c4e4e5793673b83123437e975285e7b442f4ee2654dffca5e2d",
            compressed.toHexKey(),
        )
    }

    @Test
    fun compressPublicKeyWithOddY() {
        // From existing Amethyst test: key with 03 prefix
        val privKey =
            "65f039136f8da8d3e87b4818746b53318d5481e24b2673f162815144223a0b5a"
                .hexToByteArray()
        val pubkey = Secp256k1.pubkeyCreate(privKey)
        val compressed = Secp256k1.pubKeyCompress(pubkey)
        assertEquals(
            "033dcef7585efbdb68747d919152bd481e21f5e952aaaef5a19604fbd096a93dd5",
            compressed.toHexKey(),
        )
    }

    @Test
    fun compressPublicKeyWithEvenY() {
        val privKey =
            "e6159851715b4aa6190c22b899b0c792847de0a4435ac5b678f35738351c43b0"
                .hexToByteArray()
        val pubkey = Secp256k1.pubkeyCreate(privKey)
        val compressed = Secp256k1.pubKeyCompress(pubkey)
        assertEquals(
            "029fa4ce8c87ca546b196e6518db80a6780e1bd5552b61f9f17bafee5d4e34e09b",
            compressed.toHexKey(),
        )
    }

    // ============================================================
    // privKeyTweakAdd
    // ============================================================

    @Test
    fun privateKeyTweakAdd() {
        val result =
            Secp256k1.privKeyTweakAdd(
                "67E56582298859DDAE725F972992A07C6C4FB9F62A8FFF58CE3CA926A1063530"
                    .hexToByteArray(),
                "3982F19BEF1615BCCFBB05E321C10E1D4CBA3DF0E841C2E41EEB6016347653C3"
                    .hexToByteArray(),
            )
        assertEquals(
            "a168571e189e6f9a7e2d657a4b53ae99b909f7e712d1c23ced28093cd57c88f3",
            result.toHexKey(),
        )
    }

    // ============================================================
    // pubKeyTweakMul
    // ============================================================

    @Test
    fun publicKeyTweakMul() {
        val result =
            Secp256k1.pubKeyTweakMul(
                (
                    "040A629506E1B65CD9D2E0BA9C75DF9C4FED0DB16DC9625ED14397F0AFC836FAE5" +
                        "95DC53F8B0EFE61E703075BD9B143BAC75EC0E19F82A2208CAEB32BE53414C40"
                ).hexToByteArray(),
                "3982F19BEF1615BCCFBB05E321C10E1D4CBA3DF0E841C2E41EEB6016347653C3"
                    .hexToByteArray(),
            )
        assertEquals(
            "04e0fe6fe55ebca626b98a807f6caf654139e14e5e3698f01a9a658e21dc1d2791" +
                "ec060d4f412a794d5370f672bc94b722640b5f76914151cfca6e712ca48cc589",
            result.toHexKey(),
        )
    }

    @Test
    fun publicKeyTweakMulCompressed() {
        // Test the pattern used by Secp256k1Instance.pubKeyTweakMulCompact
        val pubKey =
            "c2f9d9948dc8c7c38321e4b85c8558872eafa0641cd269db76848a6073e69133"
                .hexToByteArray()
        val privKey =
            "315e59ff51cb9209768cf7da80791ddcaae56ac9775eb25b6dee1234bc5d2268"
                .hexToByteArray()
        val h02 = byteArrayOf(0x02)
        val result = Secp256k1.pubKeyTweakMul(h02 + pubKey, privKey)
        // Should return 33-byte compressed key; take bytes 1..33 for x-only
        assertEquals(33, result.size)
    }

    @Test
    fun ecdhSymmetry() {
        // Shared secret A->B should equal B->A
        val privA =
            "315e59ff51cb9209768cf7da80791ddcaae56ac9775eb25b6dee1234bc5d2268"
                .hexToByteArray()
        val privB =
            "a1e37752c9fdc1273be53f68c5f74be7c8905728e8de75800b94262f9497c86e"
                .hexToByteArray()
        val pubA = Secp256k1.pubKeyCompress(Secp256k1.pubkeyCreate(privA))
        val pubB = Secp256k1.pubKeyCompress(Secp256k1.pubkeyCreate(privB))

        val secretAB = Secp256k1.pubKeyTweakMul(pubB, privA)
        val secretBA = Secp256k1.pubKeyTweakMul(pubA, privB)
        assertEquals(secretAB.toHexKey(), secretBA.toHexKey())
    }

    @Test
    fun verifySchnorrWrongMessage() {
        val sig =
            Secp256k1.signSchnorr(
                ByteArray(32) { 0x42 },
                "f410f88bcec6cbfda04d6a273c7b1dd8bba144cd45b71e87109cfa11dd7ed561".hexToByteArray(),
                null,
            )
        val pubkey =
            Secp256k1.pubKeyCompress(
                Secp256k1.pubkeyCreate("f410f88bcec6cbfda04d6a273c7b1dd8bba144cd45b71e87109cfa11dd7ed561".hexToByteArray()),
            )
        val xonly = pubkey.copyOfRange(1, 33)
        // Correct message verifies
        assertTrue(Secp256k1.verifySchnorr(sig, ByteArray(32) { 0x42 }, xonly))
        // Wrong message fails
        assertFalse(Secp256k1.verifySchnorr(sig, ByteArray(32) { 0x43 }, xonly))
    }

    @Test
    fun verifySchnorrCorruptedSignature() {
        val privKey = "f410f88bcec6cbfda04d6a273c7b1dd8bba144cd45b71e87109cfa11dd7ed561".hexToByteArray()
        val msg = ByteArray(32) { 0x42 }
        val sig = Secp256k1.signSchnorr(msg, privKey, null)
        val pubkey = Secp256k1.pubKeyCompress(Secp256k1.pubkeyCreate(privKey))
        val xonly = pubkey.copyOfRange(1, 33)
        // Flip a bit in the signature
        val corrupt = sig.copyOf()
        corrupt[0] = (corrupt[0].toInt() xor 1).toByte()
        assertFalse(Secp256k1.verifySchnorr(corrupt, msg, xonly))
    }

    @Test
    fun signSchnorrDeterministic() {
        // With null auxrand, signing should be deterministic
        val privKey = "f410f88bcec6cbfda04d6a273c7b1dd8bba144cd45b71e87109cfa11dd7ed561".hexToByteArray()
        val msg = ByteArray(32) { 0x42 }
        val sig1 = Secp256k1.signSchnorr(msg, privKey, null)
        val sig2 = Secp256k1.signSchnorr(msg, privKey, null)
        assertEquals(sig1.toList(), sig2.toList())
    }

    @Test
    fun ecdhXOnlyMatchesTweakMul() {
        // ecdhXOnly should produce the same x as pubKeyTweakMulCompact
        val privKey =
            "67E56582298859DDAE725F972992A07C6C4FB9F62A8FFF58CE3CA926A1063530"
                .hexToByteArray()
        val pubKeyXOnly =
            Secp256k1Instance
                .compressedPubKeyFor(
                    "3982F19BEF1615BCCFBB05E321C10E1D4CBA3DF0E841C2E41EEB6016347653C3".hexToByteArray(),
                ).copyOfRange(1, 33)
        val viaEcdh =
            com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
                .ecdhXOnly(pubKeyXOnly, privKey)
        val viaTweak = Secp256k1Instance.pubKeyTweakMulCompact(pubKeyXOnly, privKey)
        assertEquals(viaEcdh.toHexKey(), viaTweak.toHexKey())
    }

    @Test
    fun ecdhXOnlySymmetric() {
        // A→B and B→A should produce the same shared secret
        val privA =
            "67E56582298859DDAE725F972992A07C6C4FB9F62A8FFF58CE3CA926A1063530"
                .hexToByteArray()
        val privB =
            "3982F19BEF1615BCCFBB05E321C10E1D4CBA3DF0E841C2E41EEB6016347653C3"
                .hexToByteArray()
        val pubA = Secp256k1Instance.compressedPubKeyFor(privA).copyOfRange(1, 33)
        val pubB = Secp256k1Instance.compressedPubKeyFor(privB).copyOfRange(1, 33)
        val secretAB =
            com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
                .ecdhXOnly(pubB, privA)
        val secretBA =
            com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
                .ecdhXOnly(pubA, privB)
        assertEquals(secretAB.toHexKey(), secretBA.toHexKey())
    }

    @Test
    fun taggedHashConsistency() {
        // tagged_hash("BIP0340/challenge", msg) should equal SHA256(SHA256(tag) || SHA256(tag) || msg)
        val tag = "BIP0340/challenge"
        val msg = ByteArray(32) { 0x42 }
        val result =
            com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
                .taggedHash(tag, msg)
        val tagHash =
            com.vitorpamplona.quartz.utils.sha256
                .sha256(tag.encodeToByteArray())
        val expected =
            com.vitorpamplona.quartz.utils.sha256
                .sha256(tagHash + tagHash + msg)
        assertEquals(expected.toHexKey(), result.toHexKey())
    }

    // ============================================================
    // Same-pubkey batch verification
    // ============================================================

    @Test
    fun batchSamePubkeyAllValid() {
        val seckey = "67E56582298859DDAE725F972992A07C6C4FB9F62A8FFF58CE3CA926A1063530".hexToByteArray()
        val pub = Secp256k1.pubKeyCompress(Secp256k1.pubkeyCreate(seckey)).copyOfRange(1, 33)
        val sigs = mutableListOf<ByteArray>()
        val msgs = mutableListOf<ByteArray>()
        for (i in 0 until 10) {
            val msg = ByteArray(32) { (i * 7 + it).toByte() }
            val sig = Secp256k1.signSchnorr(msg, seckey, null)
            assertTrue(Secp256k1.verifySchnorr(sig, msg, pub), "Individual verify failed for event $i")
            sigs.add(sig)
            msgs.add(msg)
        }
        assertTrue(Secp256k1.verifySchnorrBatch(pub, sigs, msgs))
    }

    @Test
    fun batchSamePubkeyWithInvalid() {
        val seckey = "67E56582298859DDAE725F972992A07C6C4FB9F62A8FFF58CE3CA926A1063530".hexToByteArray()
        val pub = Secp256k1.pubKeyCompress(Secp256k1.pubkeyCreate(seckey)).copyOfRange(1, 33)
        val msg1 = ByteArray(32) { 0x01 }
        val msg2 = ByteArray(32) { 0x02 }
        val sig1 = Secp256k1.signSchnorr(msg1, seckey, null)
        val sig2 = Secp256k1.signSchnorr(msg2, seckey, null)
        // Corrupt sig2
        val badSig2 = sig2.copyOf()
        badSig2[63] = (badSig2[63].toInt() xor 0x01).toByte()
        assertFalse(Secp256k1.verifySchnorrBatch(pub, listOf(sig1, badSig2), listOf(msg1, msg2)))
    }

    @Test
    fun batchSamePubkeyEmpty() {
        val pub = "67E56582298859DDAE725F972992A07C6C4FB9F62A8FFF58CE3CA926A1063530".hexToByteArray()
        assertTrue(Secp256k1.verifySchnorrBatch(pub, emptyList(), emptyList()))
    }

    @Test
    fun batchSamePubkeySingleFallback() {
        val seckey = "67E56582298859DDAE725F972992A07C6C4FB9F62A8FFF58CE3CA926A1063530".hexToByteArray()
        val pub = Secp256k1.pubKeyCompress(Secp256k1.pubkeyCreate(seckey)).copyOfRange(1, 33)
        val msg = ByteArray(32) { 0x42 }
        val sig = Secp256k1.signSchnorr(msg, seckey, null)
        assertTrue(Secp256k1.verifySchnorrBatch(pub, listOf(sig), listOf(msg)))
    }

    @Test
    fun batchSamePubkeyLargeBatch() {
        val seckey = "3982F19BEF1615BCCFBB05E321C10E1D4CBA3DF0E841C2E41EEB6016347653C3".hexToByteArray()
        val pub = Secp256k1.pubKeyCompress(Secp256k1.pubkeyCreate(seckey)).copyOfRange(1, 33)
        val sigs = mutableListOf<ByteArray>()
        val msgs = mutableListOf<ByteArray>()
        for (i in 0 until 32) {
            val msg = ByteArray(64) { (i * 13 + it).toByte() }
            sigs.add(Secp256k1.signSchnorr(msg, seckey, null))
            msgs.add(msg)
        }
        assertTrue(Secp256k1.verifySchnorrBatch(pub, sigs, msgs))
    }
}
