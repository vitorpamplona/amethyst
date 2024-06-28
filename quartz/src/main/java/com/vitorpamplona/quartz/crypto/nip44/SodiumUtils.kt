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
package com.vitorpamplona.quartz.crypto.nip44

import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.utils.Key

/**
 * I initially extended these methods from the Sodium and SodiumAndroid classes But JNI doesn't like
 * it. There is some native method overriding bug when using Kotlin extensions
 */
fun cryptoStreamXchacha20XorIc(
    libSodium: SodiumAndroid,
    cipher: ByteArray,
    message: ByteArray,
    messageLen: Long,
    nonce: ByteArray,
    ic: Long,
    key: ByteArray,
): Int {
    /**
     * C++ Code:
     *
     * unsigned char k2[crypto_core_hchacha20_OUTPUTBYTES]; crypto_core_hchacha20(k2, n, k, NULL);
     * return crypto_stream_chacha20_xor_ic( c, m, mlen, n + crypto_core_hchacha20_INPUTBYTES, ic,
     * k2);
     */
    val k2 = ByteArray(32)

    val nonceChaCha = nonce.drop(16).toByteArray()
    assert(nonceChaCha.size == 8)

    libSodium.crypto_core_hchacha20(k2, nonce, key, null)
    return libSodium.crypto_stream_chacha20_xor_ic(
        cipher,
        message,
        messageLen,
        nonceChaCha,
        ic,
        k2,
    )
}

fun cryptoStreamXchacha20Xor(
    libSodium: SodiumAndroid,
    cipher: ByteArray,
    message: ByteArray,
    messageLen: Long,
    nonce: ByteArray,
    key: ByteArray,
): Int = cryptoStreamXchacha20XorIc(libSodium, cipher, message, messageLen, nonce, 0, key)

fun cryptoStreamXChaCha20Xor(
    libSodium: SodiumAndroid,
    cipher: ByteArray,
    message: ByteArray,
    messageLen: Long,
    nonce: ByteArray,
    key: ByteArray,
): Boolean {
    require(!(messageLen < 0 || messageLen > message.size)) {
        "messageLen out of bounds: $messageLen"
    }
    return cryptoStreamXchacha20Xor(
        libSodium,
        cipher,
        message,
        messageLen,
        nonce,
        key,
    ) == 0
}

fun cryptoStreamXChaCha20Xor(
    libSodium: SodiumAndroid,
    messageBytes: ByteArray,
    nonce: ByteArray,
    key: Key,
): ByteArray? {
    val mLen = messageBytes.size
    val cipher = ByteArray(mLen)
    val successful =
        cryptoStreamXChaCha20Xor(libSodium, cipher, messageBytes, mLen.toLong(), nonce, key.asBytes)
    return if (successful) cipher else null
}
