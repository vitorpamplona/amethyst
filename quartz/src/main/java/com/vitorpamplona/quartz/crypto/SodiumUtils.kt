package com.vitorpamplona.quartz.crypto

import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.utils.Key

/**
 * I initially extended these methods from the Sodium and SodiumAndroid classes
 * But JNI doesn't like it. There is some native method overriding bug
 * when using Kotlin extensions
 **/

fun /*Sodium.*/crypto_stream_xchacha20_xor_ic(
    libSodium: SodiumAndroid,
    cipher: ByteArray,
    message: ByteArray,
    messageLen: Long,
    nonce: ByteArray,
    ic: Long,
    key: ByteArray
): Int {
    /**
     * C++ Code:
     *
     * unsigned char k2[crypto_core_hchacha20_OUTPUTBYTES];
     * crypto_core_hchacha20(k2, n, k, NULL);
     * return crypto_stream_chacha20_xor_ic(
     * c, m, mlen, n + crypto_core_hchacha20_INPUTBYTES, ic, k2);
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
        k2
    )
}

fun /*Sodium.*/crypto_stream_xchacha20_xor(
    libSodium: SodiumAndroid,
    cipher: ByteArray,
    message: ByteArray,
    messageLen: Long,
    nonce: ByteArray,
    key: ByteArray
): Int {
    return crypto_stream_xchacha20_xor_ic(libSodium, cipher, message, messageLen, nonce, 0, key)
}

fun /*SodiumAndroid.*/cryptoStreamXChaCha20Xor(
    libSodium: SodiumAndroid,
    cipher: ByteArray,
    message: ByteArray,
    messageLen: Long,
    nonce: ByteArray,
    key: ByteArray
): Boolean {
    require(!(messageLen < 0 || messageLen > message.size)) { "messageLen out of bounds: $messageLen" }
    return crypto_stream_xchacha20_xor(
        libSodium,
        cipher,
        message,
        messageLen,
        nonce,
        key
    ) == 0
}

fun /*SodiumAndroid.*/cryptoStreamXChaCha20Xor(
    libSodium: SodiumAndroid,
    messageBytes: ByteArray,
    nonce: ByteArray,
    key: Key
): ByteArray? {
    val mLen = messageBytes.size
    val cipher = ByteArray(mLen)
    val sucessful = cryptoStreamXChaCha20Xor(libSodium, cipher, messageBytes, mLen.toLong(), nonce, key.asBytes)
    return if (sucessful) cipher else null
}
