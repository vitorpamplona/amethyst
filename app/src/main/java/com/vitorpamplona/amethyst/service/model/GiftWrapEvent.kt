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
    private var cachedInnerEvent: Map<HexKey, Event?> = mapOf()

    fun cachedGossip(privKey: ByteArray): Event? {
        val hex = privKey.toHexKey()
        if (cachedInnerEvent.contains(hex)) return cachedInnerEvent[hex]

        val myInnerEvent = unwrap(privKey = privKey)
        cachedInnerEvent = cachedInnerEvent + Pair(hex, myInnerEvent)
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
            val sharedSecret = CryptoUtils.getSharedSecretNIP24(privKey, pubKey.hexToByteArray())

            val toDecrypt = gson.fromJson<EncryptedInfo>(
                content,
                EncryptedInfo::class.java
            )

            return CryptoUtils.decryptNIP24(toDecrypt, sharedSecret)
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
            val sharedSecret = CryptoUtils.getSharedSecretNIP24(privateKey, recipientPubKey.hexToByteArray())

            val content = gson.toJson(
                CryptoUtils.encryptNIP24(
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
