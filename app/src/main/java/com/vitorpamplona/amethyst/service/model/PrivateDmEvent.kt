package com.vitorpamplona.amethyst.service.model

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.TimeUtils
import com.vitorpamplona.amethyst.model.toHexKey
import com.vitorpamplona.amethyst.service.CryptoUtils
import com.vitorpamplona.amethyst.service.HexValidator
import fr.acinq.secp256k1.Hex

@Immutable
class PrivateDmEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {
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
            val sharedSecret = CryptoUtils.getSharedSecret(privKey, pubKey)

            val retVal = CryptoUtils.decrypt(content, sharedSecret)

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
            privateKey: ByteArray,
            createdAt: Long = TimeUtils.now(),
            publishedRecipientPubKey: ByteArray? = null,
            advertiseNip18: Boolean = true,
            markAsSensitive: Boolean,
            zapRaiserAmount: Long?,
            geohash: String? = null
        ): PrivateDmEvent {
            val content = CryptoUtils.encrypt(
                if (advertiseNip18) { nip18Advertisement } else { "" } + msg,
                privateKey,
                recipientPubKey
            )
            val pubKey = CryptoUtils.pubkeyCreate(privateKey).toHexKey()
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

            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = CryptoUtils.sign(id, privateKey)
            return PrivateDmEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }
    }
}
