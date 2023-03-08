package com.vitorpamplona.amethyst.service.model

import android.util.Log
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.toHexKey
import fr.acinq.secp256k1.Hex
import nostr.postr.Utils
import nostr.postr.toHex
import java.util.Date

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
    fun recipientPubKey() = tags.firstOrNull { it.firstOrNull() == "p" }?.run { Hex.decode(this[1]).toHexKey() } // makes sure its a valid one

    /**
     * To be fully compatible with nip-04, we read e-tags that are in violation to nip-18.
     *
     * Nip-18 messages should refer to other events by inline references in the content like
     * `[](e/c06f795e1234a9a1aecc731d768d4f3ca73e80031734767067c82d67ce82e506).
     */
    fun replyTo() = tags.firstOrNull { it.firstOrNull() == "e" }?.getOrNull(1)

    fun plainContent(privKey: ByteArray, pubKey: ByteArray): String? {
        return try {
            val sharedSecret = Utils.getSharedSecret(privKey, pubKey)

            val retVal = Utils.decrypt(content, sharedSecret)

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
            privateKey: ByteArray,
            createdAt: Long = Date().time / 1000,
            publishedRecipientPubKey: ByteArray? = null,
            advertiseNip18: Boolean = true
        ): PrivateDmEvent {
            val content = Utils.encrypt(
                if (advertiseNip18) { nip18Advertisement } else { "" } + msg,
                privateKey,
                recipientPubKey
            )
            val pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            val tags = mutableListOf<List<String>>()
            publishedRecipientPubKey?.let {
                tags.add(listOf("p", publishedRecipientPubKey.toHex()))
            }
            replyTos?.forEach {
                tags.add(listOf("e", it))
            }
            mentions?.forEach {
                tags.add(listOf("p", it))
            }
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = Utils.sign(id, privateKey)
            return PrivateDmEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }
    }
}
