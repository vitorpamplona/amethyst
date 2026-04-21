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
package com.vitorpamplona.quartz.utils

/**
 * Wrapper for the custom C secp256k1 implementation via JNI.
 *
 * This provides the same interface as [Secp256k1InstanceKotlin] but delegates
 * to our own C implementation (not ACINQ's) for benchmarking comparison.
 *
 * Three implementations can be compared:
 * - [Secp256k1Instance]: Platform default (ACINQ JNI on JVM/Android, pure Kotlin on native)
 * - [Secp256k1InstanceKotlin]: Pure Kotlin implementation (all platforms)
 * - [Secp256k1InstanceC]: Our custom C implementation via JNI (JVM/Android only)
 */
expect object Secp256k1InstanceC {
    fun init()

    fun compressedPubKeyFor(privKey: ByteArray): ByteArray

    fun isPrivateKeyValid(il: ByteArray): Boolean

    fun signSchnorr(
        data: ByteArray,
        privKey: ByteArray,
        nonce: ByteArray? = null,
    ): ByteArray

    fun signSchnorrWithXOnlyPubKey(
        data: ByteArray,
        privKey: ByteArray,
        xOnlyPubKey: ByteArray,
        nonce: ByteArray? = null,
    ): ByteArray

    fun verifySchnorr(
        signature: ByteArray,
        hash: ByteArray,
        pubKey: ByteArray,
    ): Boolean

    fun verifySchnorrFast(
        signature: ByteArray,
        hash: ByteArray,
        pubKey: ByteArray,
    ): Boolean

    fun verifySchnorrBatch(
        pubKey: ByteArray,
        signatures: List<ByteArray>,
        messages: List<ByteArray>,
    ): Boolean

    fun privateKeyAdd(
        first: ByteArray,
        second: ByteArray,
    ): ByteArray

    fun pubKeyTweakMulCompact(
        pubKey: ByteArray,
        privateKey: ByteArray,
    ): ByteArray

    fun ecdhXOnly(
        xOnlyPub: ByteArray,
        scalar: ByteArray,
    ): ByteArray
}
