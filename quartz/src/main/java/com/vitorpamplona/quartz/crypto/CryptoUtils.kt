package com.vitorpamplona.quartz.crypto

import android.util.Log
import android.util.LruCache
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.utils.Key
import com.vitorpamplona.quartz.encoders.Hex
import com.vitorpamplona.quartz.events.Event
import fr.acinq.secp256k1.Secp256k1
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec


object CryptoUtils {
    private val sharedKeyCache04 = LruCache<Int, ByteArray>(200)
    private val sharedKeyCache44 = LruCache<Int, ByteArray>(200)

    private val secp256k1 = Secp256k1.get()
    private val libSodium = SodiumAndroid()
    private val random = SecureRandom()
    private val h02 = Hex.decode("02")

    fun clearCache() {
        sharedKeyCache04.evictAll()
        sharedKeyCache44.evictAll()
    }

    fun randomInt(bound: Int): Int {
        return random.nextInt(bound)
    }

    /**
     * Provides a 32B "private key" aka random number
     */
    fun privkeyCreate() = random(32)

    fun random(size: Int): ByteArray {
        val bytes = ByteArray(size)
        random.nextBytes(bytes)
        return bytes
    }

    fun pubkeyCreate(privKey: ByteArray) =
        secp256k1.pubKeyCompress(secp256k1.pubkeyCreate(privKey)).copyOfRange(1, 33)

    fun sign(data: ByteArray, privKey: ByteArray): ByteArray =
        secp256k1.signSchnorr(data, privKey, null)

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

    fun encryptNIP04(msg: String, privateKey: ByteArray, pubKey: ByteArray): String {
        val info = encryptNIP04(msg, getSharedSecretNIP04(privateKey, pubKey))
        val encryptionInfo = EncryptedInfoString(
            v = info.v,
            nonce = Base64.getEncoder().encodeToString(info.nonce),
            ciphertext = Base64.getEncoder().encodeToString(info.ciphertext)
        )
        return "${encryptionInfo.ciphertext}?iv=${encryptionInfo.nonce}"
    }

    fun encryptNIP04Json(msg: String, privateKey: ByteArray, pubKey: ByteArray): EncryptedInfo {
        return encryptNIP04(msg, getSharedSecretNIP04(privateKey, pubKey))
    }

    fun encryptNIP04(msg: String, sharedSecret: ByteArray): EncryptedInfo {
        val iv = ByteArray(16)
        random.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(sharedSecret, "AES"), IvParameterSpec(iv))
        //val ivBase64 = Base64.getEncoder().encodeToString(iv)
        val encryptedMsg = cipher.doFinal(msg.toByteArray())
        //val encryptedMsgBase64 = Base64.getEncoder().encodeToString(encryptedMsg)
        return EncryptedInfo(encryptedMsg, iv, Nip44Version.NIP04.versionCode)
    }

    fun decryptNIP04(msg: String, privateKey: ByteArray, pubKey: ByteArray): String {
        val sharedSecret = getSharedSecretNIP04(privateKey, pubKey)
        return decryptNIP04(msg, sharedSecret)
    }

    fun decryptNIP04(encryptedInfo: EncryptedInfo, privateKey: ByteArray, pubKey: ByteArray): String {
        val sharedSecret = getSharedSecretNIP04(privateKey, pubKey)
        return decryptNIP04(encryptedInfo.ciphertext, encryptedInfo.nonce, sharedSecret)
    }

    fun decryptNIP04(msg: String, sharedSecret: ByteArray): String {
        val parts = msg.split("?iv=")
        return decryptNIP04(parts[0], parts[1], sharedSecret)
    }

    private fun decryptNIP04(cipher: String, nonce: String, sharedSecret: ByteArray): String {
        val iv = Base64.getDecoder().decode(nonce)
        val encryptedMsg = Base64.getDecoder().decode(cipher)
        return decryptNIP04(encryptedMsg, iv, sharedSecret)
    }

    private fun decryptNIP04(encryptedMsg: ByteArray, iv: ByteArray, sharedSecret: ByteArray): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(sharedSecret, "AES"), IvParameterSpec(iv))
        return String(cipher.doFinal(encryptedMsg))
    }

    fun encryptNIP44(msg: String, privateKey: ByteArray, pubKey: ByteArray): EncryptedInfo {
        val sharedSecret = getSharedSecretNIP44(privateKey, pubKey)
        return encryptNIP44(msg, sharedSecret)
    }

    fun encryptNIP44(msg: String, sharedSecret: ByteArray): EncryptedInfo {
        val nonce = ByteArray(24)
        random.nextBytes(nonce)

        val cipher = cryptoStreamXChaCha20Xor(
            libSodium = libSodium,
            messageBytes = msg.toByteArray(),//compress(msg),
            nonce = nonce,
            key = Key.fromBytes(sharedSecret)
        )

        return EncryptedInfo(
            ciphertext = cipher ?: ByteArray(0),
            nonce = nonce,
            v = Nip44Version.NIP44.versionCode
        )
    }

    fun decryptNIP44(encryptedInfo: EncryptedInfo, privateKey: ByteArray, pubKey: ByteArray): String? {
        val sharedSecret = getSharedSecretNIP44(privateKey, pubKey)
        return decryptNIP44(encryptedInfo, sharedSecret)
    }

    fun decryptNIP44(encryptedInfo: EncryptedInfo, sharedSecret: ByteArray): String? {
        return cryptoStreamXChaCha20Xor(
            libSodium = libSodium,
            messageBytes = encryptedInfo.ciphertext,
            nonce = encryptedInfo.nonce,
            key = Key.fromBytes(sharedSecret)
        )?.decodeToString() //?.let { decompress(it) }
    }

    /**
     * @return 32B shared secret
     */
    fun getSharedSecretNIP04(privateKey: ByteArray, pubKey: ByteArray): ByteArray {
        val hash = combinedHashCode(privateKey, pubKey)
        val preComputed = sharedKeyCache04[hash]
        if (preComputed != null) return preComputed

        val computed = computeSharedSecretNIP04(privateKey, pubKey)
        sharedKeyCache04.put(hash, computed)
        return computed
    }

    /**
     * @return 32B shared secret
     */
    fun computeSharedSecretNIP04(privateKey: ByteArray, pubKey: ByteArray): ByteArray =
        secp256k1.pubKeyTweakMul(h02 + pubKey, privateKey).copyOfRange(1, 33)

    /**
     * @return 32B shared secret
     */
    fun getSharedSecretNIP44(privateKey: ByteArray, pubKey: ByteArray): ByteArray {
        val hash = combinedHashCode(privateKey, pubKey)
        val preComputed = sharedKeyCache44[hash]
        if (preComputed != null) return preComputed

        val computed = computeSharedSecretNIP44(privateKey, pubKey)
        sharedKeyCache44.put(hash, computed)
        return computed
    }

    /**
     * @return 32B shared secret
     */
    fun computeSharedSecretNIP44(privateKey: ByteArray, pubKey: ByteArray): ByteArray =
        sha256(secp256k1.pubKeyTweakMul(h02 + pubKey, privateKey).copyOfRange(1, 33))
}

data class EncryptedInfo(val ciphertext: ByteArray, val nonce: ByteArray, val v: Int)
data class EncryptedInfoString(val ciphertext: String, val nonce: String, val v: Int)

enum class Nip44Version(val versionCode: Int) {
    NIP04(0),
    NIP44(1)
}


fun encodeNIP44(info: EncryptedInfo): String {
    return encodeByteArray(info)
}

fun decodeNIP44(str: String): EncryptedInfo? {
    if (str.isEmpty()) return null
    return if (str[0] == '{') {
        decodeJackson(str)
    } else {
        decodeByteArray(str)
    }
}

fun encodeByteArray(info: EncryptedInfo): String {
    return Base64.getEncoder().encodeToString(byteArrayOf(info.v.toByte()) + info.nonce + info.ciphertext)
}

fun decodeByteArray(base64: String): EncryptedInfo? {
    return try {
        val byteArray = Base64.getDecoder().decode(base64)
        return EncryptedInfo(
            v = byteArray[0].toInt(),
            nonce = byteArray.copyOfRange(1, 25),
            ciphertext = byteArray.copyOfRange(25, byteArray.size)
        )
    } catch (e: Exception) {
        Log.w("CryptoUtils", "Unable to Parse encrypted payload: ${base64}")
        null
    }
}

fun encodeJackson(info: EncryptedInfo): String {
    return Event.mapper.writeValueAsString(
        EncryptedInfoString(
            v = info.v,
            nonce = Base64.getEncoder().encodeToString(info.nonce),
            ciphertext = Base64.getEncoder().encodeToString(info.ciphertext)
        )
    )
}

fun decodeJackson(json: String): EncryptedInfo {
    val info = Event.mapper.readValue(json, EncryptedInfoString::class.java)
    return EncryptedInfo(
        v = info.v,
        nonce = Base64.getDecoder().decode(info.nonce),
        ciphertext = Base64.getDecoder().decode(info.ciphertext)
    )
}

fun combinedHashCode(a: ByteArray, b: ByteArray): Int {
    var result = 1
    for (element in a) result = 31 * result + element
    for (element in b) result = 31 * result + element
    return result
}

/*
OLD Versions used for the Benchmark

fun encodeKotlin(info: EncryptedInfo): String {
    return Json.encodeToString(
        EncryptedInfoString(
        v = info.v,
        nonce = Base64.getEncoder().encodeToString(info.nonce),
        ciphertext = Base64.getEncoder().encodeToString(info.ciphertext)
    )
    )
}

fun decodeKotlin(json: String): EncryptedInfo {
    val info = Json.decodeFromString<EncryptedInfoString>(json)
    return EncryptedInfo(
        v = info.v,
        nonce = Base64.getDecoder().decode(info.nonce),
        ciphertext = Base64.getDecoder().decode(info.ciphertext)
    )
}

fun encodeCSV(info: EncryptedInfo): String {
    return "${info.v},${Base64.getEncoder().encodeToString(info.nonce)},${Base64.getEncoder().encodeToString(info.ciphertext)}"
}

fun decodeCSV(base64: String): EncryptedInfo {
    val parts = base64.split(",")
    return EncryptedInfo(
        v = parts[0].toInt(),
        nonce = Base64.getDecoder().decode(parts[1]),
        ciphertext = Base64.getDecoder().decode(parts[2])
    )
}


fun compress(input: String): ByteArray {
    return DeflaterInputStream(input.toByteArray().inputStream()).readBytes()
}

fun decompress(inputBytes: ByteArray): String {
    return InflaterInputStream(inputBytes.inputStream()).bufferedReader().use { it.readText() }
}

*/