package com.vitorpamplona.amethyst.service.model

import android.util.Log
import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.TimeUtils
import com.vitorpamplona.amethyst.model.hexToByteArray
import com.vitorpamplona.amethyst.model.toHexKey
import com.vitorpamplona.amethyst.service.CryptoUtils
import com.vitorpamplona.amethyst.service.EncryptedInfo
import com.vitorpamplona.amethyst.service.Nip44Version
import com.vitorpamplona.amethyst.service.relays.Client

@Immutable
class SealedGossipEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {
    @Transient
    private var cachedInnerEvent: Map<HexKey, Event?> = mapOf()

    fun cachedGossip(privKey: ByteArray): Event? {
        val hex = privKey.toHexKey()
        if (cachedInnerEvent.contains(hex)) return cachedInnerEvent[hex]

        val gossip = unseal(privKey = privKey)
        val event = gossip?.mergeWith(this)
        cachedInnerEvent = cachedInnerEvent + Pair(hex, event)
        return event
    }

    fun unseal(privKey: ByteArray): Gossip? = try {
        plainContent(privKey)?.let { gson.fromJson(it, Gossip::class.java) }
    } catch (e: Exception) {
        Log.w("GossipEvent", "Fail to decrypt or parse Gossip", e)
        null
    }

    private fun plainContent(privKey: ByteArray): String? {
        if (content.isBlank()) return null

        return try {
            val toDecrypt = gson.fromJson(content, EncryptedInfo::class.java)

            return when (toDecrypt.v) {
                Nip44Version.NIP04.versionCode -> CryptoUtils.decryptNIP04(toDecrypt, privKey, pubKey.hexToByteArray())
                Nip44Version.NIP24.versionCode -> CryptoUtils.decryptNIP24(toDecrypt, privKey, pubKey.hexToByteArray())
                else -> null
            }
        } catch (e: Exception) {
            Log.w("GossipEvent", "Error decrypting the message ${e.message}")
            null
        }
    }

    companion object {
        const val kind = 13

        fun create(
            event: Event,
            encryptTo: HexKey,
            privateKey: ByteArray,
            createdAt: Long = TimeUtils.now()
        ): SealedGossipEvent {
            val gossip = Gossip.create(event)
            return create(gossip, encryptTo, privateKey, createdAt)
        }

        fun create(
            gossip: Gossip,
            encryptTo: HexKey,
            privateKey: ByteArray,
            createdAt: Long = TimeUtils.randomWithinAWeek()
        ): SealedGossipEvent {
            val sharedSecret = CryptoUtils.getSharedSecretNIP24(privateKey, encryptTo.hexToByteArray())

            val content = gson.toJson(
                CryptoUtils.encryptNIP24(
                    gson.toJson(gossip),
                    sharedSecret
                )
            )
            val pubKey = CryptoUtils.pubkeyCreate(privateKey).toHexKey()
            val tags = listOf<List<String>>()
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = CryptoUtils.sign(id, privateKey)
            return SealedGossipEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }
    }
}

class Gossip(
    val id: HexKey?,
    @SerializedName("pubkey")
    val pubKey: HexKey?,
    @SerializedName("created_at")
    val createdAt: Long?,
    val kind: Int?,
    val tags: List<List<String>>?,
    val content: String?
) {
    fun mergeWith(event: SealedGossipEvent): Event {
        val newPubKey = pubKey?.ifBlank { null } ?: event.pubKey
        val newCreatedAt = if (createdAt != null && createdAt > 1000) createdAt else event.createdAt
        val newKind = kind ?: -1
        val newTags = (tags ?: emptyList()).plus(event.tags)
        val newContent = content ?: ""
        val newID = id?.ifBlank { null } ?: Event.generateId(newPubKey, newCreatedAt, newKind, newTags, newContent).toHexKey()
        val sig = ""

        return EventFactory.create(newID, newPubKey, newCreatedAt, newKind, newTags, newContent, sig, Client.lenient)
    }

    companion object {
        fun create(event: Event): Gossip {
            return Gossip(event.id, event.pubKey, event.createdAt, event.kind, event.tags, event.content)
        }
    }
}
