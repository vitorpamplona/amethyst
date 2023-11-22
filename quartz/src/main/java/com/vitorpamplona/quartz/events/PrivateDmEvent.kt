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
import com.vitorpamplona.quartz.encoders.hexToByteArray
import com.vitorpamplona.quartz.signers.NostrSigner
import kotlinx.collections.immutable.persistentSetOf
import java.util.UUID

@Immutable
class PrivateDmEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig), ChatroomKeyable {
    @Transient
    private var decryptedContent: Map<HexKey, String> = mapOf()

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

    fun cachedContentFor(signer: NostrSigner): String? {
        return decryptedContent[signer.pubKey]
    }

    fun plainContent(signer: NostrSigner, onReady: (String) -> Unit) {
        decryptedContent[signer.pubKey]?.let {
            onReady(it)
            return
        }

        signer.nip04Decrypt(content, talkingWith(signer.pubKey)) { retVal ->
            val content = if (retVal.startsWith(nip18Advertisement)) {
                retVal.substring(16)
            } else {
                retVal
            }

            decryptedContent = decryptedContent + Pair(signer.pubKey, content)

            onReady(content)
        }
    }

    companion object {
        const val kind = 4

        const val nip18Advertisement = "[//]: # (nip18)\n"

        fun create(
            recipientPubKey: HexKey,
            msg: String,
            replyTos: List<String>? = null,
            mentions: List<String>? = null,
            zapReceiver: List<ZapSplitSetup>? = null,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            publishedRecipientPubKey: HexKey? = null,
            advertiseNip18: Boolean = true,
            markAsSensitive: Boolean,
            zapRaiserAmount: Long?,
            geohash: String? = null,
            onReady: (PrivateDmEvent) -> Unit
        ) {
            val message = if (advertiseNip18) { nip18Advertisement } else { "" } + msg
            val tags = mutableListOf<List<String>>()
            publishedRecipientPubKey?.let {
                tags.add(listOf("p", publishedRecipientPubKey))
            }
            replyTos?.forEach {
                tags.add(listOf("e", it))
            }
            mentions?.forEach {
                tags.add(listOf("p", it))
            }
            zapReceiver?.forEach {
                tags.add(listOf("zap", it.lnAddressOrPubKeyHex, it.relay ?: "", it.weight.toString()))
            }
            if (markAsSensitive) {
                tags.add(listOf("content-warning", ""))
            }
            zapRaiserAmount?.let {
                tags.add(listOf("zapraiser", "$it"))
            }
            geohash?.let {
                tags.addAll(geohashMipMap(it))
            }

            signer.nip04Encrypt(message, recipientPubKey) { content ->
                signer.sign(createdAt, kind, tags, content, onReady)
            }
        }
    }
}

fun geohashMipMap(geohash: String): List<List<String>> {
    return geohash.indices.asSequence().map {
        listOf("g", geohash.substring(0, it+1))
    }.toList().reversed()
}