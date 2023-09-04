package com.vitorpamplona.quartz.events

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.HexValidator
import com.vitorpamplona.quartz.encoders.Hex
import kotlinx.collections.immutable.persistentSetOf

@Immutable
class PrivateDmEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig), ChatroomKeyable {
    /**
     * This may or may not be the actual recipient's pub key. The event is intended to look like a
     * nip-04 EncryptedDmEvent but may omit the recipient, too. This value can be queried and used
     * for initial messages.
     */
    private fun recipientPubKey() = tags.firstOrNull { it.size > 1 && it[0] == "p" }?.get(1)

    fun recipientPubKeyBytes() = recipientPubKey()?.runCatching { Hex.decode(this) }?.getOrNull()

    fun verifiedRecipientPubKey(): HexKey? {
        val recipient = recipientPubKey()
        return if (HexValidator.isHex(recipient)) {
            recipient
        } else {
            null
        }
    }

    fun talkingWith(oneSideHex: String): HexKey {
        return if (pubKey == oneSideHex) verifiedRecipientPubKey() ?: pubKey else pubKey
    }

    override fun chatroomKey(toRemove: String): ChatroomKey {
        return ChatroomKey(persistentSetOf(talkingWith(toRemove)))
    }

    /**
     * To be fully compatible with nip-04, we read e-tags that are in violation to nip-18.
     *
     * Nip-18 messages should refer to other events by inline references in the content like
     * `[](e/c06f795e1234a9a1aecc731d768d4f3ca73e80031734767067c82d67ce82e506).
     */
    fun replyTo() = tags.firstOrNull { it.size > 1 && it[0] == "e" }?.get(1)

    fun with(pubkeyHex: String): Boolean {
        return pubkeyHex == pubKey ||
            tags.any { it.size > 1 && it[0] == "p" && it[1] == pubkeyHex }
    }

    fun plainContent(privKey: ByteArray, pubKey: ByteArray): String? {
        return try {
            val sharedSecret = CryptoUtils.getSharedSecretNIP04(privKey, pubKey)

            val retVal = CryptoUtils.decryptNIP04(content, sharedSecret)

            if (retVal.startsWith(nip18Advertisement)) {
                retVal.substring(16)
            } else {
                retVal
            }
        } catch (e: Exception) {
            Log.w("PrivateDM", "Error decrypting the message ${e.message}")
            null
        }
    }

    companion object {
        const val kind = 4

        const val nip18Advertisement = "[//]: # (nip18)\n"

        fun create(
            recipientPubKey: ByteArray,
            msg: String,
            replyTos: List<String>? = null,
            mentions: List<String>? = null,
            zapReceiver: String?,
            keyPair: KeyPair,
            createdAt: Long = TimeUtils.now(),
            publishedRecipientPubKey: ByteArray? = null,
            advertiseNip18: Boolean = true,
            markAsSensitive: Boolean,
            zapRaiserAmount: Long?,
            geohash: String? = null
        ): PrivateDmEvent {
            val message = if (advertiseNip18) { nip18Advertisement } else { "" } + msg
            val content = if (keyPair.privKey == null) message else CryptoUtils.encryptNIP04(
                message,
                keyPair.privKey,
                recipientPubKey
            )
            val tags = mutableListOf<List<String>>()
            publishedRecipientPubKey?.let {
                tags.add(listOf("p", publishedRecipientPubKey.toHexKey()))
            }
            replyTos?.forEach {
                tags.add(listOf("e", it))
            }
            mentions?.forEach {
                tags.add(listOf("p", it))
            }
            zapReceiver?.let {
                tags.add(listOf("zap", it))
            }
            if (markAsSensitive) {
                tags.add(listOf("content-warning", ""))
            }
            zapRaiserAmount?.let {
                tags.add(listOf("zapraiser", "$it"))
            }
            geohash?.let {
                tags.add(listOf("g", it))
            }

            val pubKey = keyPair.pubKey.toHexKey()
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = if (keyPair.privKey == null) null else CryptoUtils.sign(id, keyPair.privKey)
            return PrivateDmEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig?.toHexKey() ?: "")
        }

        fun create(
            unsignedEvent: PrivateDmEvent,
            signature: String,
        ): PrivateDmEvent {
            return PrivateDmEvent(unsignedEvent.id, unsignedEvent.pubKey, unsignedEvent.createdAt, unsignedEvent.tags, unsignedEvent.content, signature)
        }
    }
}
