package com.vitorpamplona.amethyst.service

import com.goterl.lazysodium.LazySodium
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.Sodium
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.utils.Base64MessageEncoder
import com.goterl.lazysodium.utils.Key
import fr.acinq.secp256k1.Hex
import fr.acinq.secp256k1.Secp256k1
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    private val secp256k1 = Secp256k1.get()
    private val random = SecureRandom()

    /**
     * Provides a 32B "private key" aka random number
     */
    fun privkeyCreate(): ByteArray {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return bytes
    }

    fun pubkeyCreate(privKey: ByteArray) =
        secp256k1.pubKeyCompress(secp256k1.pubkeyCreate(privKey)).copyOfRange(1, 33)

    fun sign(data: ByteArray, privKey: ByteArray): ByteArray =
        secp256k1.signSchnorr(data, privKey, null)

    fun encrypt(msg: String, privateKey: ByteArray, pubKey: ByteArray): String {
        val sharedSecret = getSharedSecret(privateKey, pubKey)
        return encrypt(msg, sharedSecret)
    }

    fun encrypt(msg: String, sharedSecret: ByteArray): String {
        val iv = ByteArray(16)
        random.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(sharedSecret, "AES"), IvParameterSpec(iv))
        val ivBase64 = Base64.getEncoder().encodeToString(iv)
        val encryptedMsg = cipher.doFinal(msg.toByteArray())
        val encryptedMsgBase64 = Base64.getEncoder().encodeToString(encryptedMsg)
        return "$encryptedMsgBase64?iv=$ivBase64"
    }

    fun decrypt(msg: String, privateKey: ByteArray, pubKey: ByteArray): String {
        val sharedSecret = getSharedSecret(privateKey, pubKey)
        return decrypt(msg, sharedSecret)
    }

    fun decrypt(msg: String, sharedSecret: ByteArray): String {
        val parts = msg.split("?iv=")
        val iv = parts[1].run { Base64.getDecoder().decode(this) }
        val encryptedMsg = parts.first().run { Base64.getDecoder().decode(this) }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(sharedSecret, "AES"), IvParameterSpec(iv))
        return String(cipher.doFinal(encryptedMsg))
    }

    fun encryptXChaCha(msg: String, privateKey: ByteArray, pubKey: ByteArray): EncryptedInfo {
        val sharedSecret = getSharedSecretXChaCha(privateKey, pubKey)
        return encryptXChaCha(msg, sharedSecret)
    }

    fun encryptXChaCha(msg: String, sharedSecret: ByteArray): EncryptedInfo {
        val lazySodium = LazySodiumAndroid(SodiumAndroid(), Base64MessageEncoder())

        val key = Key.fromBytes(sharedSecret)

        val nonce: ByteArray = lazySodium.nonce(24)
        val messageBytes: ByteArray = msg.toByteArray()

        val cipher = lazySodium.cryptoStreamXChaCha20Xor(
            messageBytes = messageBytes,
            nonce = nonce,
            key = key
        )

        val cipherBase64 = Base64.getEncoder().encodeToString(cipher)
        val nonceBase64 = Base64.getEncoder().encodeToString(nonce)

        return EncryptedInfo(
            ciphertext = cipherBase64,
            nonce = nonceBase64,
            v = Nip44Version.XChaCha20.versionCode
        )
    }

    fun decryptXChaCha(encryptedInfo: EncryptedInfo, privateKey: ByteArray, pubKey: ByteArray): String? {
        val sharedSecret = getSharedSecretXChaCha(privateKey, pubKey)
        return decryptXChaCha(encryptedInfo, sharedSecret)
    }

    fun decryptXChaCha(encryptedInfo: EncryptedInfo, sharedSecret: ByteArray): String? {
        val lazySodium = LazySodiumAndroid(SodiumAndroid(), Base64MessageEncoder())

        val key = Key.fromBytes(sharedSecret)
        val nonceBytes = Base64.getDecoder().decode(encryptedInfo.nonce)
        val messageBytes = Base64.getDecoder().decode(encryptedInfo.ciphertext)

        val cipher = lazySodium.cryptoStreamXChaCha20Xor(
            messageBytes = messageBytes,
            nonce = nonceBytes,
            key = key
        )

        return cipher?.decodeToString()
    }

    fun verifySignature(
        signature: ByteArray,
        hash: ByteArray,
        pubKey: ByteArray
    ): Boolean {
        return secp256k1.verifySchnorr(signature, hash, pubKey)
    }

    fun sha256(data: ByteArray): ByteArray {
        // Creates a new buffer every time
        return MessageDigest.getInstance("SHA-256").digest(data)
    }

    /**
     * @return 32B shared secret
     */
    fun getSharedSecret(privateKey: ByteArray, pubKey: ByteArray): ByteArray =
        secp256k1.pubKeyTweakMul(Hex.decode("02") + pubKey, privateKey).copyOfRange(1, 33)

    /**
     * @return 32B shared secret
     */
    fun getSharedSecretXChaCha(privateKey: ByteArray, pubKey: ByteArray): ByteArray =
        sha256(secp256k1.pubKeyTweakMul(Hex.decode("02") + pubKey, privateKey).copyOfRange(1, 33))
}

fun Int.toByteArray(): ByteArray {
    val bytes = ByteArray(4)
    (0..3).forEach {
        bytes[3 - it] = ((this ushr (8 * it)) and 0xFFFF).toByte()
    }
    return bytes
}

data class EncryptedInfo(val ciphertext: String, val nonce: String, val v: Int)

enum class Nip44Version(val versionCode: Int) {
    Reserved(0),
    XChaCha20(1)
}

fun Sodium.crypto_stream_xchacha20_xor_ic(
    cipher: ByteArray,
    message: ByteArray,
    messageLen: Long,
    nonce: ByteArray,
    ic: Long,
    key: ByteArray
): Int {
    /**
     *
     *     unsigned char k2[crypto_core_hchacha20_OUTPUTBYTES];

     crypto_core_hchacha20(k2, n, k, NULL);
     return crypto_stream_chacha20_xor_ic(
     c, m, mlen, n + crypto_core_hchacha20_INPUTBYTES, ic, k2);
     */

    val k2 = ByteArray(32)

    val nonceChaCha = nonce.drop(16).toByteArray()
    assert(nonceChaCha.size == 8)

    crypto_core_hchacha20(k2, nonce, key, null)
    return crypto_stream_chacha20_xor_ic(
        cipher,
        message,
        messageLen,
        nonceChaCha,
        ic,
        k2
    )
}

fun Sodium.crypto_stream_xchacha20_xor(
    cipher: ByteArray,
    message: ByteArray,
    messageLen: Long,
    nonce: ByteArray,
    key: ByteArray
): Int {
    return crypto_stream_xchacha20_xor_ic(cipher, message, messageLen, nonce, 0, key)
}

fun LazySodium.cryptoStreamXChaCha20Xor(
    cipher: ByteArray,
    message: ByteArray,
    messageLen: Long,
    nonce: ByteArray,
    key: ByteArray
): Boolean {
    require(!(messageLen < 0 || messageLen > message.size)) { "messageLen out of bounds: $messageLen" }
    return successful(
        getSodium().crypto_stream_xchacha20_xor(
            cipher,
            message,
            messageLen,
            nonce,
            key
        )
    )
}

private fun LazySodium.cryptoStreamXChaCha20Xor(
    messageBytes: ByteArray,
    nonce: ByteArray,
    key: Key
): ByteArray? {
    val mLen = messageBytes.size
    val cipher = ByteArray(mLen)
    val sucessful = cryptoStreamXChaCha20Xor(cipher, messageBytes, mLen.toLong(), nonce, key.asBytes)
    return if (sucessful) cipher else null
}
