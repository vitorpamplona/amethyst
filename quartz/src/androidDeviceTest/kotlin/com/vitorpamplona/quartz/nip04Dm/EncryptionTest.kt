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
package com.vitorpamplona.quartz.nip04Dm

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto
import com.vitorpamplona.quartz.nip04Dm.crypto.EncryptedInfo
import com.vitorpamplona.quartz.nip04Dm.crypto.Encryption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptionTest {
    private val nip04 = Encryption()

    val sk1 = "91ba716fa9e7ea2fcbad360cf4f8e0d312f73984da63d90f524ad61a6a1e7dbe".hexToByteArray()
    val sk2 = "96f6fa197aa07477ab88f6981118466ae3a982faab8ad5db9d5426870c73d220".hexToByteArray()
    val pk1 = Nip01Crypto.pubKeyCreate(sk1)
    val pk2 = Nip01Crypto.pubKeyCreate(sk2)

    val expectedShared = "7ce22696eb0e303ddaa491bdf2a56b79d249f2d861b8e012a933e01dc4beba81"

    @Test
    fun conversationKeyTest() {
        assertEquals(
            expectedShared,
            nip04.computeSharedSecret(sk2, pk1).toHexKey(),
        )

        assertEquals(
            expectedShared,
            nip04.computeSharedSecret(sk1, pk2).toHexKey(),
        )
    }

    @Test
    fun encryptDecryptTest() {
        val message = "testing"
        val cipher = nip04.encrypt(message, sk2, pk1)

        assertEquals(message, nip04.decrypt(cipher, sk2, pk1))
        assertEquals(message, nip04.decrypt(cipher, sk1, pk2))

        val cipher2 = nip04.encrypt(message, sk1, pk2)

        assertEquals(message, nip04.decrypt(cipher2, sk2, pk1))
        assertEquals(message, nip04.decrypt(cipher2, sk1, pk2))
    }

    @Test
    fun decryptTest() {
        val cipher = "zJxfaJ32rN5Dg1ODjOlEew==?iv=EV5bUjcc4OX2Km/zPp4ndQ=="

        assertEquals("nanana", nip04.decrypt(cipher, nip04.computeSharedSecret(sk2, pk1)))
        assertEquals("nanana", nip04.decrypt(cipher, nip04.computeSharedSecret(sk1, pk2)))
    }

    @Test
    fun decryptLargePayloadTest() {
        val ciphertext =
            "6f8dMstm+udOu7yipSn33orTmwQpWbtfuY95NH+eTU1kArysWJIDkYgI2D25EAGIDJsNd45jOJ2NbVOhFiL3ZP/NWsTwXokk34iyHyA/lkjzugQ1bHXoMD1fP/Ay4hB4al1NHb8HXHKZaxPrErwdRDb8qa/I6dXb/1xxyVvNQBHHvmsM5yIFaPwnCN1DZqXf2KbTA/Ekz7Hy+7R+Sy3TXLQDFpWYqykppkXc7Fs0qSuPRyxz5+anuN0dxZa9GTwTEnBrZPbthKkNRrvZMdTGJ6WumOh9aUq8OJJWy9aOgsXvs7qjN1UqcCqQqYaVnEOhCaqWNDsVtsFrVDj+SaLIBvCiomwF4C4nIgngJ5I69tx0UNI0q+ZnvOGQZ7m1PpW2NYP7Yw43HJNdeUEQAmdCPnh/PJwzLTnIxHmQU7n7SPlMdV0SFa6H8y2HHvex697GAkyE5t8c2uO24OnqIwF1tR3blIqXzTSRl0GA6QvrSj2p4UtnWjvF7xT7RiIEyTtgU/AsihTrXyXzWWZaIBJogpgw6erlZqWjCH7sZy/WoGYEiblobOAqMYxax6vRbeuGtoYksr/myX+x9rfLrYuoDRTw4woXOLmMrrj+Mf0TbAgc3SjdkqdsPU1553rlSqIEZXuFgoWmxvVQDtekgTYyS97G81TDSK9nTJT5ilku8NVq2LgtBXGwsNIw/xekcOUzJke3kpnFPutNaexR1VF3ohIuqRKYRGcd8ADJP2lfwMcaGRiplAmFoaVS1YUhQwYFNq9rMLf7YauRGV4BJg/t9srdGxf5RoKCvRo+XM/nLxxysTR9MVaEP/3lDqjwChMxs+eWfLHE5vRWV8hUEqdrWNZV29gsx5nQpzJ4PARGZVu310pQzc6JAlc2XAhhFk6RamkYJnmCSMnb/RblzIATBi2kNrCVAlaXIon188inB62rEpZGPkRIP7PUfu27S/elLQHBHeGDsxOXsBRo1gl3te+raoBHsxo6zvRnYbwdAQa5taDE63eh+fT6kFI+xYmXNAQkU8Dp0MVhEh4JQI06Ni/AKrvYpC95TXXIphZcF+/Pv/vaGkhG2X9S3uhugwWK?iv=2vWkOQQi0WynNJz/aZ4k2g=="

        val expected = "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz"

        assertEquals(expected, nip04.decrypt(ciphertext, nip04.computeSharedSecret(sk2, pk1)))
        assertEquals(expected, nip04.decrypt(ciphertext, nip04.computeSharedSecret(sk1, pk2)))
    }

    @Test
    fun isNIP04Encode() {
        assertTrue(EncryptedInfo.isNIP04("Xj/oZZolaItdyQ5v7xYFpA==?iv=+a6zagBp+mr5m1aFbHQ8lA=="))
        assertTrue(EncryptedInfo.isNIP04("zJxfaJ32rN5Dg1ODjOlEew==?iv=EV5bUjcc4OX2Km/zPp4ndQ=="))
        assertTrue(
            EncryptedInfo.isNIP04("6f8dMstm+udOu7yipSn33orTmwQpWbtfuY95NH+eTU1kArysWJIDkYgI2D25EAGIDJsNd45jOJ2NbVOhFiL3ZP/NWsTwXokk34iyHyA/lkjzugQ1bHXoMD1fP/Ay4hB4al1NHb8HXHKZaxPrErwdRDb8qa/I6dXb/1xxyVvNQBHHvmsM5yIFaPwnCN1DZqXf2KbTA/Ekz7Hy+7R+Sy3TXLQDFpWYqykppkXc7Fs0qSuPRyxz5+anuN0dxZa9GTwTEnBrZPbthKkNRrvZMdTGJ6WumOh9aUq8OJJWy9aOgsXvs7qjN1UqcCqQqYaVnEOhCaqWNDsVtsFrVDj+SaLIBvCiomwF4C4nIgngJ5I69tx0UNI0q+ZnvOGQZ7m1PpW2NYP7Yw43HJNdeUEQAmdCPnh/PJwzLTnIxHmQU7n7SPlMdV0SFa6H8y2HHvex697GAkyE5t8c2uO24OnqIwF1tR3blIqXzTSRl0GA6QvrSj2p4UtnWjvF7xT7RiIEyTtgU/AsihTrXyXzWWZaIBJogpgw6erlZqWjCH7sZy/WoGYEiblobOAqMYxax6vRbeuGtoYksr/myX+x9rfLrYuoDRTw4woXOLmMrrj+Mf0TbAgc3SjdkqdsPU1553rlSqIEZXuFgoWmxvVQDtekgTYyS97G81TDSK9nTJT5ilku8NVq2LgtBXGwsNIw/xekcOUzJke3kpnFPutNaexR1VF3ohIuqRKYRGcd8ADJP2lfwMcaGRiplAmFoaVS1YUhQwYFNq9rMLf7YauRGV4BJg/t9srdGxf5RoKCvRo+XM/nLxxysTR9MVaEP/3lDqjwChMxs+eWfLHE5vRWV8hUEqdrWNZV29gsx5nQpzJ4PARGZVu310pQzc6JAlc2XAhhFk6RamkYJnmCSMnb/RblzIATBi2kNrCVAlaXIon188inB62rEpZGPkRIP7PUfu27S/elLQHBHeGDsxOXsBRo1gl3te+raoBHsxo6zvRnYbwdAQa5taDE63eh+fT6kFI+xYmXNAQkU8Dp0MVhEh4JQI06Ni/AKrvYpC95TXXIphZcF+/Pv/vaGkhG2X9S3uhugwWK?iv=2vWkOQQi0WynNJz/aZ4k2g=="),
        )
    }

    @Test
    fun isNIP04EncodeWithBug() {
        assertTrue(
            EncryptedInfo.isNIP04(
                "QOAYBWa88ConWs2C4kSvNqAcowCtg0ZRtAl7FyLSv9VMaJH4oCiDx0h8VLBnV97HdE4lv" +
                    "TW7AYC1eEw8/t1dbe0qRc3XrOt7MrPAO8yqpy1/3lFB1+10kip0+KdgT8Quvv02wTP8Dqi" +
                    "xpr2fliAIG2ONvDn+O5V0q9aVUN9HitgL/myTyR0T42edmxWeZoMBEOKvJyO80FekSsgVL" +
                    "ASafA/T5z4xs8oG88pSe9wSbSsw0xNjJeh3xLRCLuEuA9KI8hQ1Ys9nEax2UlaB/IL3o77" +
                    "OwBL+rrdUbNHTxYifgygRhg3BaXMsXRFNJbqYeMaRaNbvHkLVAQV2jLY4P/cKHBjEcTC/f" +
                    "lrCc2NCYF34rOQUY5EJVnFzM8qYVw6xNupBHTS7WFx1r60cPjG19P/+yoiTZ6bPdHTU0X2" +
                    "t64ovF2YWUq6/iKAclMaZDhWfrKqf82e62oIff55WQw2bw8A/jtBQVCf66EtEJ2OSFxNaZ" +
                    "rO+A4oLkHDCnAV+6fYzwo89gPOvORcVvSvg55yGiBFUZx9EHS6kdH1SU80/Mbxe2oI=" +
                    "?iv=gxz9pUFJFZHuV+D+hgKEOw==-null",
            ),
        )
    }
}
