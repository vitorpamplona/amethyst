/**
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
package com.vitorpamplona.quartz.nip49PrivKeyEnc

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.fail
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class NIP49Test {
    companion object {
        val TEST_CASE = "ncryptsec1qgg9947rlpvqu76pj5ecreduf9jxhselq2nae2kghhvd5g7dgjtcxfqtd67p9m0w57lspw8gsq6yphnm8623nsl8xn9j4jdzz84zm3frztj3z7s35vpzmqf6ksu8r89qk5z2zxfmu5gv8th8wclt0h4p"

        val TEST_CASE_EXPECTED = "3501454135014541350145413501453fefb02227e449e57cf4d3a3ce05378683"
        val TEST_CASE_PASSWORD = "nostr"

        val MAIN_TEST_CASES =
            listOf<Nip49TestCase>(
                Nip49TestCase(".ksjabdk.aselqwe", "14c226dbdd865d5e1645e72c7470fd0a17feb42cc87b750bab6538171b3a3f8a", 1, 0x00),
                Nip49TestCase("skjdaklrnçurbç l", "f7f2f77f98890885462764afb15b68eb5f69979c8046ecb08cad7c4ae6b221ab", 2, 0x01),
                Nip49TestCase("777z7z7z7z7z7z7z", "11b25a101667dd9208db93c0827c6bdad66729a5b521156a7e9d3b22b3ae8944", 3, 0x02),
                Nip49TestCase(".ksjabdk.aselqwe", "14c226dbdd865d5e1645e72c7470fd0a17feb42cc87b750bab6538171b3a3f8a", 7, 0x00),
                Nip49TestCase("skjdaklrnçurbç l", "f7f2f77f98890885462764afb15b68eb5f69979c8046ecb08cad7c4ae6b221ab", 8, 0x01),
                Nip49TestCase("777z7z7z7z7z7z7z", "11b25a101667dd9208db93c0827c6bdad66729a5b521156a7e9d3b22b3ae8944", 9, 0x02),
                Nip49TestCase("", "f7f2f77f98890885462764afb15b68eb5f69979c8046ecb08cad7c4ae6b221ab", 4, 0x00),
                Nip49TestCase("", "11b25a101667dd9208db93c0827c6bdad66729a5b521156a7e9d3b22b3ae8944", 5, 0x01),
                Nip49TestCase("", "f7f2f77f98890885462764afb15b68eb5f69979c8046ecb08cad7c4ae6b221ab", 1, 0x00),
                Nip49TestCase("", "11b25a101667dd9208db93c0827c6bdad66729a5b521156a7e9d3b22b3ae8944", 9, 0x01),
            )
    }

    val nip49 = Nip49()

    @Test
    fun decodeBech32() {
        val data =
            Nip49.EncryptedInfo.decodePayload(
                TEST_CASE,
            )!!

        assertEquals(2.toByte(), data.version)
        assertEquals(16.toByte(), data.logn)
        assertEquals("52d7c3f8580e7b41953381e5bc49646b", data.salt.toHexKey())
        assertEquals("c33f02a7dcaac8bdd8da23cd449783240b6ebc12edeea7bf", data.nonce.toHexKey())
        assertEquals(0.toByte(), data.keySecurity)
        assertEquals("b8e8803440de7b3e9519c3e734cb2ac9a211ea2dc52312e5117a11a3022d813ab438719ca0b504a1193be510c3aee776", data.encryptedKey.toHexKey())
    }

    @Test
    fun decrypt() {
        val decrypted = nip49.decrypt(TEST_CASE, TEST_CASE_PASSWORD)
        assertEquals(TEST_CASE_EXPECTED, decrypted)
    }

    @Test
    fun encryptDecryptTestCase() {
        val encrypted = nip49.encrypt(TEST_CASE_EXPECTED, TEST_CASE_PASSWORD, 16, 0)
        val decrypted = nip49.decrypt(encrypted!!, TEST_CASE_PASSWORD)

        assertEquals(TEST_CASE_EXPECTED, decrypted)
    }

    @Test
    fun encryptDecrypt() {
        MAIN_TEST_CASES.forEach {
            val encrypted = nip49.encrypt(it.secretKey, it.password, it.logn, it.ksb)

            assertNotNull(encrypted)

            val decrypted = nip49.decrypt(encrypted!!, it.password)

            assertEquals(it.secretKey, decrypted)
        }
    }

    @Test
    fun normalization() {
        val samePassword1 = String(byteArrayOf(0xE2.toByte(), 0x84.toByte(), 0xAB.toByte(), 0xE2.toByte(), 0x84.toByte(), 0xA6.toByte(), 0xE1.toByte(), 0xBA.toByte(), 0x9B.toByte(), 0xCC.toByte(), 0xA3.toByte()))
        val samePassword2 = String(byteArrayOf(0xC3.toByte(), 0x85.toByte(), 0xCE.toByte(), 0xA9.toByte(), 0xE1.toByte(), 0xB9.toByte(), 0xA9.toByte()))

        if (samePassword1.toByteArray().contentEquals(samePassword2.toByteArray())) {
            fail("Passwords should have a different byte representation")
        }

        val encrypted = nip49.encrypt(TEST_CASE_EXPECTED, samePassword1, 8, 0)

        assertNotNull(encrypted)

        val decrypted = nip49.decrypt(encrypted!!, samePassword2)

        assertEquals(TEST_CASE_EXPECTED, decrypted)
    }

    class Nip49TestCase(
        val password: String,
        val secretKey: String,
        val logn: Int,
        val ksb: Byte,
    )
}
