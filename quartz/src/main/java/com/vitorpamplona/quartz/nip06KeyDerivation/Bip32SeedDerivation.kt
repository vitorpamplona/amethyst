/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.nip06KeyDerivation

import fr.acinq.secp256k1.Secp256k1
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/*
 Simplified from: https://github.com/ACINQ/bitcoin-kmp/
 */
class Bip32SeedDerivation(
    val secp256k1: Secp256k1,
) {
    class ExtendedPrivateKey(
        val secretkeybytes: ByteArray,
        val chaincode: ByteArray,
    )

    /**
     * @param seed random seed
     * @return a "master" private key
     */
    fun generate(seed: ByteArray): ExtendedPrivateKey {
        val i = hmac512("Bitcoin seed".encodeToByteArray(), seed)
        val il = i.take(32).toByteArray()
        val ir = i.takeLast(32).toByteArray()
        return ExtendedPrivateKey(il, ir)
    }

    fun hmac512(
        key: ByteArray,
        data: ByteArray,
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(key, "HmacSHA512"))
        return mac.doFinal(data)
    }

    fun isPrivKeyValid(il: ByteArray): Boolean = secp256k1.secKeyVerify(il)

    fun pubkeyCreateBitcoin(privKey: ByteArray) = secp256k1.pubKeyCompress(secp256k1.pubkeyCreate(privKey))

    fun sum(
        first: ByteArray,
        second: ByteArray,
    ): ByteArray = secp256k1.privKeyTweakAdd(first, second)

    fun derivePrivateKey(
        parent: ExtendedPrivateKey,
        index: Long,
    ): ExtendedPrivateKey {
        val i =
            if (Hardener.isHardened(index)) {
                val data = arrayOf(0.toByte()).toByteArray() + parent.secretkeybytes + writeInt32BE(index.toInt())
                hmac512(parent.chaincode, data)
            } else {
                val data = pubkeyCreateBitcoin(parent.secretkeybytes) + writeInt32BE(index.toInt())
                hmac512(parent.chaincode, data)
            }
        val il = i.take(32).toByteArray()
        val ir = i.takeLast(32).toByteArray()

        require(isPrivKeyValid(il)) { "cannot generate child private key: IL is invalid" }

        val key = sum(il, parent.secretkeybytes)

        require(isPrivKeyValid(key)) { "cannot generate child private key: resulting private key is invalid" }

        return ExtendedPrivateKey(key, ir)
    }

    public fun writeInt32BE(n: Int): ByteArray = ByteArray(Int.SIZE_BYTES).also { writeInt32BE(n, it) }

    public fun writeInt32BE(
        n: Int,
        bs: ByteArray,
        off: Int = 0,
    ) {
        require(bs.size - off >= Int.SIZE_BYTES)
        bs[off] = (n ushr 24).toByte()
        bs[off + 1] = (n ushr 16).toByte()
        bs[off + 2] = (n ushr 8).toByte()
        bs[off + 3] = n.toByte()
    }

    fun derivePrivateKey(
        parent: ExtendedPrivateKey,
        chain: List<Long>,
    ): ExtendedPrivateKey = chain.fold(parent, this::derivePrivateKey)

    fun derivePrivateKey(
        parent: ExtendedPrivateKey,
        keyPath: KeyPath,
    ): ByteArray = derivePrivateKey(parent, keyPath.path).secretkeybytes

    fun derivePrivateKey(
        parent: ExtendedPrivateKey,
        keyPath: String,
    ): ByteArray = derivePrivateKey(parent, KeyPath.fromPath(keyPath))
}
