package com.vitorpamplona.quartz.events

import android.util.Log
import androidx.compose.runtime.Immutable
import com.fasterxml.jackson.annotation.JsonProperty
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.hexToByteArray
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.crypto.Nip44Version
import com.vitorpamplona.quartz.crypto.decodeNIP44
import com.vitorpamplona.quartz.crypto.encodeNIP44
import com.vitorpamplona.quartz.encoders.HexKey

@Immutable
class SealedGossipEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
): WrappedEvent(id, pubKey, createdAt, kind, tags, content, sig) {
    @Transient
    private var cachedInnerEvent: Map<HexKey, Event?> = mapOf()

    fun cachedGossip(privKey: ByteArray): Event? {
        val hex = privKey.toHexKey()
        if (cachedInnerEvent.contains(hex)) return cachedInnerEvent[hex]

        val gossip = unseal(privKey = privKey)
        val event = gossip?.mergeWith(this)
        if (event is WrappedEvent) {
            event.host = host ?: this
        }

        cachedInnerEvent = cachedInnerEvent + Pair(hex, event)
        return event
    }

    fun cachedGossip(pubKey: ByteArray, decryptedContent: String): Event? {
        val hex = pubKey.toHexKey()
        if (cachedInnerEvent.contains(hex)) return cachedInnerEvent[hex]

        val gossip = unseal(decryptedContent)
        val event = gossip?.mergeWith(this)
        if (event is WrappedEvent) {
            event.host = host ?: this
        }

        cachedInnerEvent = cachedInnerEvent + Pair(hex, event)
        return event
    }

    fun unseal(privKey: ByteArray): Gossip? = try {
        plainContent(privKey)?.let { Gossip.fromJson(it) }
    } catch (e: Exception) {
        Log.w("GossipEvent", "Fail to decrypt or parse Gossip", e)
        null
    }

    fun unseal(decryptedContent: String): Gossip? = try {
        plainContent(decryptedContent)?.let { Gossip.fromJson(it) }
    } catch (e: Exception) {
        Log.w("GossipEvent", "Fail to decrypt or parse Gossip", e)
        null
    }

    private fun plainContent(decryptedContent: String): String? {
        if (decryptedContent.isEmpty()) return null
        return decryptedContent
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
            val sharedSecret = CryptoUtils.getSharedSecretNIP44(privateKey, encryptTo.hexToByteArray())

            val content = encodeNIP44(
                CryptoUtils.encryptNIP44(
                    Gossip.toJson(gossip),
                    sharedSecret
                )
            )
            val pubKey = CryptoUtils.pubkeyCreate(privateKey).toHexKey()
            val tags = listOf<List<String>>()
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = CryptoUtils.sign(id, privateKey)
            return SealedGossipEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }

        fun create(
            encryptedContent: String,
            pubKey: HexKey,
            createdAt: Long = TimeUtils.randomWithinAWeek()
        ): SealedGossipEvent {
            val tags = listOf<List<String>>()
            val id = generateId(pubKey, createdAt, kind, tags, encryptedContent)
            return SealedGossipEvent(id.toHexKey(), pubKey, createdAt, tags, encryptedContent, "")
        }

        fun create(
            unsignedEvent: SealedGossipEvent,
            signature: String
        ): SealedGossipEvent {
            return SealedGossipEvent(unsignedEvent.id, unsignedEvent.pubKey, unsignedEvent.createdAt, unsignedEvent.tags, unsignedEvent.content, signature)
        }
    }
}

class Gossip(
    val id: HexKey?,
    @JsonProperty("pubkey")
    val pubKey: HexKey?,
    @JsonProperty("created_at")
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

        return EventFactory.create(newID, newPubKey, newCreatedAt, newKind, newTags, newContent, sig)
    }

    companion object {
        fun fromJson(json: String): Gossip = Event.mapper.readValue(json, Gossip::class.java)
        fun toJson(event: Gossip): String = Event.mapper.writeValueAsString(event)

        fun create(event: Event): Gossip {
            return Gossip(event.id, event.pubKey, event.createdAt, event.kind, event.tags, event.content)
        }
    }
}
