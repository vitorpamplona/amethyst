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
package com.vitorpamplona.quartz.utils.ciphers.aes

import com.vitorpamplona.quartz.utils.ciphers.AESCBC
import com.vitorpamplona.quartz.utils.ciphers.AESGCM
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Known answers for the pure-Kotlin AES the native targets use. The GCM and CBC
 * vectors were produced with OpenSSL (Python's `cryptography`) from the inputs
 * [pattern] builds. They also go through the public [AESGCM] and [AESCBC], which are
 * JCA on the JVM, so every platform is held to the same bytes.
 */
class AesTest {
    private fun ByteArray.hex() = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    private fun String.unhex() = ByteArray(length / 2) { substring(2 * it, 2 * it + 2).toInt(16).toByte() }

    private fun pattern(
        size: Int,
        mul: Int,
        add: Int,
    ) = ByteArray(size) { ((it * mul + add) and 0xFF).toByte() }

    private class GcmCase(
        val keySize: Int,
        val nonceSize: Int,
        val plaintextSize: Int,
        val aadSize: Int,
        val expected: String,
    )

    private class CbcCase(
        val keySize: Int,
        val plaintextSize: Int,
        val expected: String,
    )

    private val gcmCases =
        listOf(
            GcmCase(16, 12, 0, 0, "0ec52f1d77032e793e18692d5b13cf90"),
            GcmCase(16, 12, 16, 0, "1e90635456aeb043b5a520f9d8142eca4232ca9f27d04bcbc2131ee67752f7da"),
            GcmCase(16, 12, 17, 20, "1e90635456aeb043b5a520f9d8142eca4c660ade4ab7088749960ea9754bcb5cae"),
            GcmCase(24, 12, 65, 13, "206d8766423da4cbb3292ffd68a1b700320f8bba414758015d604d71d671e4273a32b44a3c2e72da1ce278692996c056aa96ca7c4d980404bc5a69dd699d3270bab3e970bdad1ee02a74d53cd81422cb72"),
            GcmCase(32, 12, 100, 0, "f71a467fdddd4aa95ea757ff293010a257cf8ff6905d29a1e1fa8e71c36f0efc88026031f22a0db2fdf5d8bea2b1d0d7282c5ab1cfa6f8ce47da46742ff16646402a814340be9e529810cacc8eda6060c55d01c440b780bd5d4e92a95fcbe163f02be8090a87bf046f46c8c0b940fdaf6dab7d4f"),
            GcmCase(32, 16, 0, 0, "4a000a1457fdd8bc63bda5f06b1eff82"),
            GcmCase(32, 16, 33, 7, "8953a3d8271245b62f52eda23656f34a7d3e03ac1656439ce19f542c80992bf14bb99f323ce98330bb3ed6a67e88a33813"),
            GcmCase(16, 8, 5, 0, "a28133671ab0ac8996a0ea73bae5d7ee92ca40f0b9"),
            GcmCase(32, 60, 64, 16, "33b5bddc35828cb3a2a6115fb38396ae64c0bbee0980ff1883e881f20f66361ae07d212ae66dd652be64f182361179e501626d643b7dba6f5b8cb7a52ee201b0a511d2f3710649b38a1166d05f17137d"),
            GcmCase(16, 16, 255, 3, "b2e5f6ba6b4de27431a20531e4b4c8b847266cac5f1de2882408f8717d538a637670f59eb2f834cbc99a3edb5e29058d8f017b4491fd4e18036240f8c77df4c62f9e95d77cdb1fcb25c5955dc6b85b73d5522d01f8e22293b2cad6602df2511f7cdc2521bf1008eaf08d88771bf6001920cdba4a929f1f57900ab9c9fa0d3692a5e0806213b480639c384fa5fc96ffb8845fcc940759cdff22062c3447c2c12380b098250701d00fe38e422ad6f5e23557d894dc83fc046a3fc89e8c07c85dfb975d2ee2f7d2b40aef28f04c952642eba85025a01586df76ac919efb2ff7227916fab36f4dfb6f679b5bf3204764db8a755f5627f0995c581a4a95586abc95396ec6896acda1d8eb186cf07d5c3321"),
        )

    private val cbcCases =
        listOf(
            CbcCase(16, 0, "bbf293057225748007152d34e9e41765"),
            CbcCase(16, 15, "510b005e0454ed8f14d74a4eba5e14d1"),
            CbcCase(16, 16, "82a32de8a3becd3223f4b018ed3ebfae2b49f10978ddb2b6cdc4ff3e2e0a7aaa"),
            CbcCase(24, 31, "c7ce6aed9a857220cc41269b61757bc38337c837b7dad1c33b707bcbea9db271"),
            CbcCase(32, 32, "3cb885d105d4b9d0806935d5af7f6f9b44ff7ebc8c174ee4b17783e5a2fb00487f231d5c2352e4d065a78bc6f0ea406c"),
            CbcCase(32, 100, "3cb885d105d4b9d0806935d5af7f6f9b44ff7ebc8c174ee4b17783e5a2fb0048c8270939b17ebf05ccb765fd8548523ba64cd8512894b7d940e2e9a4542a3ecb1a877b329a87b6ba5788b1aa5c9f6f82ee0a65c47ab4655619acd8ff96ae8a68f79766183962e21565d94e0b91ce9def"),
            CbcCase(32, 257, "3cb885d105d4b9d0806935d5af7f6f9b44ff7ebc8c174ee4b17783e5a2fb0048c8270939b17ebf05ccb765fd8548523ba64cd8512894b7d940e2e9a4542a3ecb1a877b329a87b6ba5788b1aa5c9f6f82ee0a65c47ab4655619acd8ff96ae8a6852194964f00c319c37891ed4977f2f70fde0df9154070c37d77bab02774ef017a8eaddb0df9925c5200b24f398762e780da30110e36f9c4a469cd12c24e2a1833f5d4cc3686ff5b496c0f85a5a4543f027146a5d003c9da62723c0fb7c2a896f4efefe216587e1572fd8861851b46045a30b1d3fc919e41eef4e7cf77826a20469cebd92ce14033397ed43d82b72aef5a37896dbcd406863514eaf1213c80f655129b8e4611b63a276e53afc37f0b796"),
        )

    @Test
    fun fips197Blocks() {
        val plaintext = "00112233445566778899aabbccddeeff".unhex()
        val cases =
            listOf(
                "000102030405060708090a0b0c0d0e0f" to "69c4e0d86a7b0430d8cdb78070b4c55a",
                "000102030405060708090a0b0c0d0e0f1011121314151617" to "dda97ca4864cdfe06eaf70a0ec0d7191",
                "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f" to "8ea2b7ca516745bfeafc49904b496089",
            )
        for ((key, ciphertext) in cases) {
            val aes = AesBitsliced(key.unhex())
            val scratch = AesBitsliced.Scratch()
            val block = plaintext.copyOf()
            aes.encryptBlocks(block, 0, block, 0, 1, scratch)
            assertEquals(ciphertext, block.hex())
            aes.decryptBlocks(block, 0, block, 0, 1, scratch)
            assertContentEquals(plaintext, block)
        }
    }

    @Test
    fun fourBlocksAtOnceMatchOneAtATime() {
        val aes = AesBitsliced(pattern(32, 7, 1))
        val scratch = AesBitsliced.Scratch()
        val input = pattern(64, 13, 5)
        val together = input.copyOf()
        aes.encryptBlocks(together, 0, together, 0, 4, scratch)
        for (b in 0 until 4) {
            val single = input.copyOfRange(16 * b, 16 * b + 16)
            aes.encryptBlocks(single, 0, single, 0, 1, scratch)
            assertContentEquals(single, together.copyOfRange(16 * b, 16 * b + 16), "block $b")
        }
        aes.decryptBlocks(together, 0, together, 0, 4, scratch)
        assertContentEquals(input, together)
    }

    @Test
    fun gcmVectors() {
        for (case in gcmCases) {
            val key = pattern(case.keySize, 7, 1)
            val nonce = pattern(case.nonceSize, 11, 3)
            val plaintext = pattern(case.plaintextSize, 13, 5)
            val aad = pattern(case.aadSize, 17, 9)
            val label = "key ${case.keySize}, nonce ${case.nonceSize}, data ${case.plaintextSize}, aad ${case.aadSize}"

            assertEquals(case.expected, AesGcm.encrypt(key, nonce, plaintext, aad).hex(), label)
            assertContentEquals(plaintext, AesGcm.decrypt(key, nonce, case.expected.unhex(), aad), label)

            val cipher = AESGCM(key, nonce)
            assertEquals(case.expected, cipher.encrypt(plaintext, aad).hex(), label)
            assertContentEquals(plaintext, cipher.decrypt(case.expected.unhex(), aad), label)
        }
    }

    @Test
    fun reusedGcmKeyMatchesOneShot() {
        // In both orders, so H is computed both in the first counter batch (12-byte
        // nonce first) and up front for a J0 GHASH (other lengths first), then reused.
        for (order in listOf(gcmCases, gcmCases.reversed())) {
            val keys = mutableMapOf<Int, AesGcmKey>()
            val ciphers = mutableMapOf<Pair<Int, Int>, AESGCM>()
            repeat(2) { pass ->
                for (case in order) {
                    val key = pattern(case.keySize, 7, 1)
                    val nonce = pattern(case.nonceSize, 11, 3)
                    val plaintext = pattern(case.plaintextSize, 13, 5)
                    val aad = pattern(case.aadSize, 17, 9)
                    val label = "pass $pass, key ${case.keySize}, nonce ${case.nonceSize}, data ${case.plaintextSize}"

                    val gcm = keys.getOrPut(case.keySize) { AesGcmKey(key) }
                    assertEquals(case.expected, gcm.encrypt(nonce, plaintext, aad).hex(), label)
                    assertContentEquals(plaintext, gcm.decrypt(nonce, case.expected.unhex(), aad), label)

                    val cipher = ciphers.getOrPut(case.keySize to case.nonceSize) { AESGCM(key, nonce) }
                    assertEquals(case.expected, cipher.encrypt(plaintext, aad).hex(), label)
                    assertContentEquals(plaintext, cipher.decrypt(case.expected.unhex(), aad), label)
                }
            }
        }
    }

    @Test
    fun reusedCbcKeyMatchesOneShot() {
        val iv = pattern(16, 11, 3)
        val keys = mutableMapOf<Int, AesCbcKey>()
        val ciphers = mutableMapOf<Int, AESCBC>()
        repeat(2) {
            for (case in cbcCases) {
                val key = pattern(case.keySize, 7, 1)
                val plaintext = pattern(case.plaintextSize, 13, 5)
                val cbc = keys.getOrPut(case.keySize) { AesCbcKey(key) }
                assertEquals(case.expected, cbc.encrypt(iv, plaintext).hex())
                assertContentEquals(plaintext, cbc.decrypt(iv, case.expected.unhex()))
                val cipher = ciphers.getOrPut(case.keySize) { AESCBC(key, iv) }
                assertEquals(case.expected, cipher.encrypt(plaintext).hex())
                assertContentEquals(plaintext, cipher.decrypt(case.expected.unhex()))
            }
        }
    }

    @Test
    fun gcmRejectsAnyTamperedByte() {
        val case = gcmCases.first { it.aadSize > 0 && it.plaintextSize > 0 }
        val key = pattern(case.keySize, 7, 1)
        val nonce = pattern(case.nonceSize, 11, 3)
        val aad = pattern(case.aadSize, 17, 9)
        val sealed = case.expected.unhex()
        for (i in sealed.indices) {
            val tampered = sealed.copyOf()
            tampered[i] = (tampered[i].toInt() xor 0x01).toByte()
            assertFailsWith<IllegalArgumentException>("byte $i") { AesGcm.decrypt(key, nonce, tampered, aad) }
        }
        val otherAad = aad.copyOf().also { it[0] = (it[0].toInt() xor 0x80).toByte() }
        assertFailsWith<IllegalArgumentException> { AesGcm.decrypt(key, nonce, sealed, otherAad) }
        assertFailsWith<IllegalArgumentException> { AesGcm.decrypt(key, nonce, sealed.copyOf(15), aad) }
        assertNull(AESGCM(key, nonce).decryptOrNull(sealed))
    }

    @Test
    fun cbcVectors() {
        val iv = pattern(16, 11, 3)
        for (case in cbcCases) {
            val key = pattern(case.keySize, 7, 1)
            val plaintext = pattern(case.plaintextSize, 13, 5)
            val label = "key ${case.keySize}, data ${case.plaintextSize}"

            assertEquals(case.expected, AesCbc.encrypt(key, iv, plaintext).hex(), label)
            assertContentEquals(plaintext, AesCbc.decrypt(key, iv, case.expected.unhex()), label)

            val cipher = AESCBC(key, iv)
            assertEquals(case.expected, cipher.encrypt(plaintext).hex(), label)
            assertContentEquals(plaintext, cipher.decrypt(case.expected.unhex()), label)
        }
    }

    @Test
    fun cbcRejectsBadPaddingAndLength() {
        val key = pattern(32, 7, 1)
        val iv = pattern(16, 11, 3)
        val sealed = AesCbc.encrypt(key, iv, pattern(20, 13, 5))
        // Flipping the second-to-last block's last byte changes the last plaintext
        // byte, which is the padding length.
        val tampered = sealed.copyOf().also { it[it.size - 17] = (it[it.size - 17].toInt() xor 0x40).toByte() }
        assertFailsWith<IllegalArgumentException> { AesCbc.decrypt(key, iv, tampered) }
        assertFailsWith<IllegalArgumentException> { AesCbc.decrypt(key, iv, sealed.copyOf(sealed.size - 1)) }
        assertFailsWith<IllegalArgumentException> { AesCbc.decrypt(key, iv, ByteArray(0)) }
        assertNull(AESCBC(key, iv).decryptOrNull(tampered))
    }

    @Test
    fun rejectsBadKeySizes() {
        assertFailsWith<IllegalArgumentException> { AesBitsliced(ByteArray(15)) }
        assertFailsWith<IllegalArgumentException> { AesBitsliced(ByteArray(33)) }
    }
}
