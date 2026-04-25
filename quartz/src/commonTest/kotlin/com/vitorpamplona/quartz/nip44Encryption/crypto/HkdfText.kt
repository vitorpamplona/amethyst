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

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlin.test.Test
import kotlin.test.assertEquals

class HkdfText {
    companion object {
        val hkdf = Hkdf()
    }

    @Test
    fun testExpand1() {
        /*
        Removed in the conversion to KMP
        val result =
            hkdf.expand(
                "b0a6232d3e5444dbf2291f3290504989716f1dc78ac2da812230720fd76c8f06".hexToByteArray(),
                "c9300041754c97f030ec9c35db607cb642db0f4305f178692954f3a883ea3209".hexToByteArray(),
                76,
            )

        assertEquals("9077071b92dfbd725f5a1dd2dbbf349615f1d0ce22b436e2f505cc05984ef588", result.hmacKey.toHexKey())
        assertEquals("3256a46b762158a6e08338daf2c5b3eefd2bed343cb22df8b6315567b9207cef", result.chachaKey.toHexKey())
        assertEquals("670bb239ac07dbb801653aa5", result.chachaNonce.toHexKey())
*/
        val result2 =
            hkdf.fastExpand(
                "b0a6232d3e5444dbf2291f3290504989716f1dc78ac2da812230720fd76c8f06".hexToByteArray(),
                "c9300041754c97f030ec9c35db607cb642db0f4305f178692954f3a883ea3209".hexToByteArray(),
            )

        assertEquals("9077071b92dfbd725f5a1dd2dbbf349615f1d0ce22b436e2f505cc05984ef588", result2.hmacKey.toHexKey())
        assertEquals("3256a46b762158a6e08338daf2c5b3eefd2bed343cb22df8b6315567b9207cef", result2.chachaKey.toHexKey())
        assertEquals("670bb239ac07dbb801653aa5", result2.chachaNonce.toHexKey())
    }

    @Test
    fun testExpand2() {
        /*
        Removed in the conversion to KMP
        val result =
            hkdf.expand(
                "bc5d4e032696ef107ef1c7b6fc5f00c6e7b31ae4f86ee486ce24aa0d84847d83".hexToByteArray(),
                "830426c471c0870afb0208cabcaa0e4d66fe51af7163336b7b9ec1846c31e900".hexToByteArray(),
                76,
            )

        assertEquals("26ccd9471681fc42459dbf7b14fc54d3f5276ddad482f7c9f81dc021ccbe5592", result.hmacKey.toHexKey())
        assertEquals("a13a519db107cf9ecbca6ba79ab2428fcd624286025b7ee452f153ae770f07a8", result.chachaKey.toHexKey())
        assertEquals("4c8218640c43de928f4c52e1", result.chachaNonce.toHexKey())
         */
        val result2 =
            hkdf.fastExpand(
                "bc5d4e032696ef107ef1c7b6fc5f00c6e7b31ae4f86ee486ce24aa0d84847d83".hexToByteArray(),
                "830426c471c0870afb0208cabcaa0e4d66fe51af7163336b7b9ec1846c31e900".hexToByteArray(),
            )

        assertEquals("26ccd9471681fc42459dbf7b14fc54d3f5276ddad482f7c9f81dc021ccbe5592", result2.hmacKey.toHexKey())
        assertEquals("a13a519db107cf9ecbca6ba79ab2428fcd624286025b7ee452f153ae770f07a8", result2.chachaKey.toHexKey())
        assertEquals("4c8218640c43de928f4c52e1", result2.chachaNonce.toHexKey())
    }

    @Test
    fun testExpand3() {
        /*
        Removed in the conversion to KMP
        val result =
            hkdf.expand(
                "0000000000000000000000000000000000000000000000000000000000000000".hexToByteArray(),
                "0000000000000000000000000000000000000000000000000000000000000000".hexToByteArray(),
                76,
            )

        assertEquals("a30b80c908d01aa28eae9dcdec55ac104f9c889b989ee563919985a84f66360d", result.hmacKey.toHexKey())
        assertEquals("3769af12ff4dbf44e516a22d1d0512e8bc42516d59e8bf401ea346a4d60dccf7", result.chachaKey.toHexKey())
        assertEquals("77938d29bb13ea73f677ac27", result.chachaNonce.toHexKey())
         */
        val result2 =
            hkdf.fastExpand(
                "0000000000000000000000000000000000000000000000000000000000000000".hexToByteArray(),
                "0000000000000000000000000000000000000000000000000000000000000000".hexToByteArray(),
            )

        assertEquals("a30b80c908d01aa28eae9dcdec55ac104f9c889b989ee563919985a84f66360d", result2.hmacKey.toHexKey())
        assertEquals("3769af12ff4dbf44e516a22d1d0512e8bc42516d59e8bf401ea346a4d60dccf7", result2.chachaKey.toHexKey())
        assertEquals("77938d29bb13ea73f677ac27", result2.chachaNonce.toHexKey())
    }

    /**
     * RFC 5869 Test Case 1 — basic HKDF-SHA256 with non-empty info.
     */
    @Test
    fun rfc5869_test_case_1() {
        val ikm = "0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b".hexToByteArray()
        val salt = "000102030405060708090a0b0c".hexToByteArray()
        val info = "f0f1f2f3f4f5f6f7f8f9".hexToByteArray()
        val prk = hkdf.extract(ikm, salt)
        assertEquals(
            "077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5",
            prk.toHexKey(),
        )
        val okm = hkdf.expand(prk, info, 42)
        assertEquals(
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
            okm.toHexKey(),
        )
    }

    /**
     * RFC 5869 Test Case 2 — longer inputs, 82-byte output (spans multiple HMAC rounds).
     */
    @Test
    fun rfc5869_test_case_2() {
        val ikm =
            (
                "000102030405060708090a0b0c0d0e0f" +
                    "101112131415161718191a1b1c1d1e1f" +
                    "202122232425262728292a2b2c2d2e2f" +
                    "303132333435363738393a3b3c3d3e3f" +
                    "404142434445464748494a4b4c4d4e4f"
            ).hexToByteArray()
        val salt =
            (
                "606162636465666768696a6b6c6d6e6f" +
                    "707172737475767778797a7b7c7d7e7f" +
                    "808182838485868788898a8b8c8d8e8f" +
                    "909192939495969798999a9b9c9d9e9f" +
                    "a0a1a2a3a4a5a6a7a8a9aaabacadaeaf"
            ).hexToByteArray()
        val info =
            (
                "b0b1b2b3b4b5b6b7b8b9babbbcbdbebf" +
                    "c0c1c2c3c4c5c6c7c8c9cacbcccdcecf" +
                    "d0d1d2d3d4d5d6d7d8d9dadbdcdddedf" +
                    "e0e1e2e3e4e5e6e7e8e9eaebecedeeef" +
                    "f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff"
            ).hexToByteArray()
        val prk = hkdf.extract(ikm, salt)
        assertEquals(
            "06a6b88c5853361a06104c9ceb35b45cef760014904671014a193f40c15fc244",
            prk.toHexKey(),
        )
        val okm = hkdf.expand(prk, info, 82)
        assertEquals(
            "b11e398dc80327a1c8e7f78c596a4934" +
                "4f012eda2d4efad8a050cc4c19afa97c" +
                "59045a99cac7827271cb41c65e590e09" +
                "da3275600c2f09b8367793a9aca3db71" +
                "cc30c58179ec3e87c14c01d5c1f3434f1d87",
            okm.toHexKey(),
        )
    }

    /**
     * RFC 8448 §3 — TLS 1.3 ClientHello derived "early secret" expand-label vectors.
     *
     * Verifies our expandLabel implementation matches the canonical TLS 1.3 derivation.
     */
    @Test
    fun rfc8448_early_secret_derived() {
        // PSK = all zeros, salt = all zeros → standard early-secret PRK
        val earlySecret = hkdf.extract(ByteArray(32), ByteArray(32))
        assertEquals(
            "33ad0a1c607ec03b09e6cd9893680ce210adf300aa1f2660e1b22e10f170f92a",
            earlySecret.toHexKey(),
        )
        // SHA-256 of empty string
        val emptyHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855".hexToByteArray()
        val derived = hkdf.expandLabel(earlySecret, "derived", emptyHash, 32)
        assertEquals(
            "6f2615a108c702c5678f54fc9dbab69716c076189c48250cebeac3576c3611ba",
            derived.toHexKey(),
        )
    }
}
