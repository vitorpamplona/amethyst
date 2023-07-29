package com.vitorpamplona.amethyst.service.model

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.TimeUtils
import com.vitorpamplona.amethyst.model.hexToByteArray
import com.vitorpamplona.amethyst.model.toHexKey
import com.vitorpamplona.amethyst.service.CryptoUtils
import com.vitorpamplona.amethyst.service.EncryptedInfo
import com.vitorpamplona.amethyst.service.relays.Client

@Immutable
class GiftWrapEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {
    private var innerEvent: Event? = null

    fun cachedInnerEvent(privKey: ByteArray): Event? {
        if (innerEvent != null) return innerEvent

        val myInnerEvent = unwrap(privKey = privKey)
        innerEvent = myInnerEvent
        return myInnerEvent
    }

    fun unwrap(privKey: ByteArray) = try {
        plainContent(privKey)?.let { fromJson(it, Client.lenient) }
    } catch (e: Exception) {
        // Log.e("UnwrapError", "Couldn't Decrypt the content", e)
        null
    }

    private fun plainContent(privKey: ByteArray): String? {
        if (content.isBlank()) return null

        return try {
            val sharedSecret = CryptoUtils.getSharedSecretXChaCha(privKey, pubKey.hexToByteArray())

            val toDecrypt = gson.fromJson<EncryptedInfo>(
                content,
                EncryptedInfo::class.java
            )

            return CryptoUtils.decryptXChaCha(toDecrypt, sharedSecret)
        } catch (e: Exception) {
            Log.w("GeneralList", "Error decrypting the message ${e.message}")
            null
        }
    }

    fun recipientPubKey() = tags.firstOrNull { it.size > 1 && it[0] == "p" }?.get(1)

    companion object {
        const val kind = 1059

        fun create(
            event: Event,
            recipientPubKey: HexKey,
            createdAt: Long = TimeUtils.now()
        ): GiftWrapEvent {
            val privateKey = CryptoUtils.privkeyCreate() // GiftWrap is always a random key
            val sharedSecret = CryptoUtils.getSharedSecretXChaCha(privateKey, recipientPubKey.hexToByteArray())

            val content = gson.toJson(
                CryptoUtils.encryptXChaCha(
                    gson.toJson(event),
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
