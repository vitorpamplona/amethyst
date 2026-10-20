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

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * The pure-Kotlin AES-GCM and AES-CBC the native targets run, against JCA on random
 * keys of every size, nonces from 1 to 64 bytes and lengths across the block and
 * four-block batch boundaries.
 */
class AesJcaTest {
    private val random = Random(2026)
    private val keySizes = intArrayOf(16, 24, 32)

    @Test
    fun gcmMatchesJca() {
        repeat(400) { round ->
            val key = random.nextBytes(keySizes[round % 3])
            val nonce = random.nextBytes(if (round % 2 == 0) 12 else random.nextInt(1, 65))
            val plaintext = random.nextBytes(random.nextInt(0, 300))
            val aad = random.nextBytes(random.nextInt(0, 40))
            val jca =
                Cipher.getInstance("AES/GCM/NoPadding").run {
                    init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
                    updateAAD(aad)
                    doFinal(plaintext)
                }
            val label = "round $round"
            assertContentEquals(jca, AesGcm.encrypt(key, nonce, plaintext, aad), label)
            assertContentEquals(plaintext, AesGcm.decrypt(key, nonce, jca, aad), label)
        }
    }

    @Test
    fun cbcMatchesJca() {
        repeat(400) { round ->
            val key = random.nextBytes(keySizes[round % 3])
            val iv = random.nextBytes(16)
            val plaintext = random.nextBytes(random.nextInt(0, 300))
            val jca =
                Cipher.getInstance("AES/CBC/PKCS5Padding").run {
                    init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
                    doFinal(plaintext)
                }
            val label = "round $round"
            assertContentEquals(jca, AesCbc.encrypt(key, iv, plaintext), label)
            assertContentEquals(plaintext, AesCbc.decrypt(key, iv, jca), label)
        }
    }
}
