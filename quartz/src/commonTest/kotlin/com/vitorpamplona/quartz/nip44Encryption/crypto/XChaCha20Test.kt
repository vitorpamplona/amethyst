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
package com.vitorpamplona.quartz.nip44Encryption.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals

/** Test vectors from libsodium xchacha20.c */
class XChaCha20Test {
    private fun hex(s: String): ByteArray =
        s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    // HChaCha20 test vectors from libsodium
    data class HChaCha20TV(val key: String, val input: String, val expected: String)

    private val hChaCha20Vectors = listOf(
        HChaCha20TV(
            "24f11cce8a1b3d61e441561a696c1c1b7e173d084fd4812425435a8896a013dc",
            "d9660c5900ae19ddad28d6e06e45fe5e",
            "5966b3eec3bff1189f831f06afe4d4e3be97fa9235ec8c20d08acfbbb4e851e3",
        ),
        HChaCha20TV(
            "80a5f6272031e18bb9bcd84f3385da65e7731b7039f13f5e3d475364cd4d42f7",
            "c0eccc384b44c88e92c57eb2d5ca4dfa",
            "6ed11741f724009a640a44fce7320954c46e18e0d7ae063bdbc8d7cf372709df",
        ),
        HChaCha20TV(
            "cb1fc686c0eec11a89438b6f4013bf110e7171dace3297f3a657a309b3199629",
            "fcd49b93e5f8f299227e64d40dc864a3",
            "84b7e96937a1a0a406bb7162eeaad34308d49de60fd2f7ec9dc6a79cbab2ca34",
        ),
        HChaCha20TV(
            "6640f4d80af5496ca1bc2cfff1fefbe99638dbceaabd7d0ade118999d45f053d",
            "31f59ceeeafdbfe8cae7914caeba90d6",
            "9af4697d2f5574a44834a2c2ae1a0505af9f5d869dbe381a994a18eb374c36a0",
        ),
        HChaCha20TV(
            "0693ff36d971225a44ac92c092c60b399e672e4cc5aafd5e31426f123787ac27",
            "3a6293da061da405db45be1731d5fc4d",
            "f87b38609142c01095bfc425573bb3c698f9ae866b7e4216840b9c4caf3b0865",
        ),
    )

    @Test
    fun testHChaCha20_LibsodiumVectors() {
        for ((i, tv) in hChaCha20Vectors.withIndex()) {
            val result = ChaCha20Core.hChaCha20(hex(tv.key), hex(tv.input))
            assertContentEquals(hex(tv.expected), result, "HChaCha20 vector $i failed")
        }
    }

    // XChaCha20 stream test vectors from libsodium
    data class XChaCha20TV(val key: String, val nonce: String, val expected: String)

    private val xChaCha20Vectors = listOf(
        XChaCha20TV(
            "79c99798ac67300bbb2704c95c341e3245f3dcb21761b98e52ff45b24f304fc4",
            "b33ffd3096479bcfbc9aee49417688a0a2554f8d95389419",
            "c6e9758160083ac604ef90e712ce6e75d7797590744e0cf060f013739c",
        ),
        XChaCha20TV(
            "ddf7784fee099612c40700862189d0397fcc4cc4b3cc02b5456b3a97d1186173",
            "a9a04491e7bf00c3ca91ac7c2d38a777d88993a7047dfcc4",
            "2f289d371f6f0abc3cb60d11d9b7b29adf6bc5ad843e8493e928448d",
        ),
        XChaCha20TV(
            "3d12800e7b014e88d68a73f0a95b04b435719936feba60473f02a9e61ae60682",
            "56bed2599eac99fb27ebf4ffcb770a64772dec4d5849ea2d",
            "a2c3c1406f33c054a92760a8e0666b84f84fa3a618f0",
        ),
        XChaCha20TV(
            "eadc0e27f77113b5241f8ca9d6f9a5e7f09eee68d8a5cf30700563bf01060b4e",
            "a171a4ef3fde7c4794c5b86170dc5a099b478f1b852f7b64",
            "23839f61795c3cdbcee2c749a92543baeeea3cbb721402aa42e6cae140447575f2916c5d71108e3b13357eaf86f060cb",
        ),
        XChaCha20TV(
            "91319c9545c7c804ba6b712e22294c386fe31c4ff3d278827637b959d3dbaab2",
            "410e854b2a911f174aaf1a56540fc3855851f41c65967a4e",
            "cbe7d24177119b7fdfa8b06ee04dade4256ba7d35ffda6b89f014e479faef6",
        ),
    )

    @Test
    fun testXChaCha20Xor_LibsodiumVectors() {
        for ((i, tv) in xChaCha20Vectors.withIndex()) {
            // XOR zeros with keystream to get the expected keystream output
            val zeros = ByteArray(hex(tv.expected).size)
            val result = ChaCha20Core.xChaCha20Xor(zeros, hex(tv.nonce), hex(tv.key))
            assertContentEquals(hex(tv.expected), result, "XChaCha20 vector $i failed")
        }
    }
}
