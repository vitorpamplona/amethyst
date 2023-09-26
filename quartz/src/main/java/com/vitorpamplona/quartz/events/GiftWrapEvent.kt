package com.vitorpamplona.quartz.events

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.hexToByteArray
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.crypto.Nip44Version
import com.vitorpamplona.quartz.crypto.decodeNIP44
import com.vitorpamplona.quartz.crypto.encodeNIP44
import com.vitorpamplona.quartz.encoders.HexKey

@Immutable
class GiftWrapEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {
    @Transient
    private var cachedInnerEvent: Map<HexKey, Event?> = mapOf()

    fun cachedGift(privKey: ByteArray): Event? {
        val hex = privKey.toHexKey()
        if (cachedInnerEvent.contains(hex)) return cachedInnerEvent[hex]

        val myInnerEvent = unwrap(privKey = privKey)
        if (myInnerEvent is WrappedEvent) {
            myInnerEvent.host = this
        }

        cachedInnerEvent = cachedInnerEvent + Pair(hex, myInnerEvent)
        return myInnerEvent
    }

    fun cachedGift(pubKey: ByteArray, decryptedContent: String): Event? {
        val hex = pubKey.toHexKey()
        if (cachedInnerEvent.contains(hex)) return cachedInnerEvent[hex]

        val myInnerEvent = unwrap(decryptedContent)
        if (myInnerEvent is WrappedEvent) {
            myInnerEvent.host = this
        }

        cachedInnerEvent = cachedInnerEvent + Pair(hex, myInnerEvent)
        return myInnerEvent
    }

    fun unwrap(privKey: ByteArray) = try {
        plainContent(privKey)?.let { fromJson(it) }
    } catch (e: Exception) {
        // Log.e("UnwrapError", "Couldn't Decrypt the content", e)
        null
    }

    fun unwrap(decryptedContent: String) = try {
        plainContent(decryptedContent)?.let { fromJson(it) }
    } catch (e: Exception) {
        // Log.e("UnwrapError", "Couldn't Decrypt the content", e)
        null
    }

    private fun plainContent(privKey: ByteArray): String? {
        if (content.isEmpty()) return null

        return try {
            val toDecrypt = decodeNIP44(content) ?: return null

            return when (toDecrypt.v) {
                Nip44Version.NIP04.versionCode -> CryptoUtils.decryptNIP04(toDecrypt, privKey, pubKey.hexToByteArray())
                Nip44Version.NIP44.versionCode -> CryptoUtils.decryptNIP44(toDecrypt, privKey, pubKey.hexToByteArray())
                else -> null
            }
        } catch (e: Exception) {
            Log.w("GeneralList", "Error decrypting the message ${e.message}")
            null
        }
    }

    private fun plainContent(decryptedContent: String): String? {
        if (decryptedContent.isEmpty()) return null
        return decryptedContent
    }

    fun recipientPubKey() = tags.firstOrNull { it.size > 1 && it[0] == "p" }?.get(1)

    companion object {
        const val kind = 1059

        fun create(
            event: Event,
            recipientPubKey: HexKey,
            createdAt: Long = TimeUtils.randomWithinAWeek()
        ): GiftWrapEvent {
            val privateKey = CryptoUtils.privkeyCreate() // GiftWrap is always a random key
            val sharedSecret = CryptoUtils.getSharedSecretNIP44(privateKey, recipientPubKey.hexToByteArray())

            val content = encodeNIP44(
                CryptoUtils.encryptNIP44(
                    toJson(event),
                    sharedSecret
                )
            )
            val pubKey = CryptoUtils.pubkeyCreate(privateKey).toHexKey()
            val tags = listOf(listOf("p", recipientPubKey))
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = CryptoUtils.sign(id, privateKey)
            return GiftWrapEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }
    }
}
