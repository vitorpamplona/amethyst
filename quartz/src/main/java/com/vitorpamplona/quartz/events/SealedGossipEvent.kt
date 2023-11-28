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
import com.vitorpamplona.quartz.signers.NostrSigner
import java.util.UUID

@Immutable
class SealedGossipEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey
): WrappedEvent(id, pubKey, createdAt, kind, tags, content, sig) {
    @Transient
    private var cachedInnerEvent: Map<HexKey, Event?> = mapOf()

    fun cachedGossip(signer: NostrSigner, onReady: (Event) -> Unit) {
        cachedInnerEvent[signer.pubKey]?.let {
            onReady(it)
            return
        }

        unseal(signer) { gossip ->
            val event = gossip.mergeWith(this)
            if (event is WrappedEvent) {
                event.host = host ?: this
            }

            cachedInnerEvent = cachedInnerEvent + Pair(signer.pubKey, event)
            onReady(event)
        }
    }

    private fun unseal(signer: NostrSigner, onReady: (Gossip) -> Unit) {
        try {
            plainContent(signer) {
                try {
                    onReady(Gossip.fromJson(it))
                } catch (e: Exception) {
                    Log.w("GossipEvent", "Fail to decrypt or parse Gossip", e)
                }
            }
        } catch (e: Exception) {
            Log.w("GossipEvent", "Fail to decrypt or parse Gossip", e)
        }
    }

    private fun plainContent(signer: NostrSigner, onReady: (String) -> Unit) {
        if (content.isEmpty()) return

        signer.nip44Decrypt(content, pubKey, onReady)
    }

    companion object {
        const val kind = 13

        fun create(
            event: Event,
            encryptTo: HexKey,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (SealedGossipEvent) -> Unit
        ) {
            val gossip = Gossip.create(event)
            create(gossip, encryptTo, signer, createdAt, onReady)
        }

        fun create(
            gossip: Gossip,
            encryptTo: HexKey,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.randomWithinAWeek(),
            onReady: (SealedGossipEvent) -> Unit
        ) {
            val msg = Gossip.toJson(gossip)

            signer.nip44Encrypt(msg, encryptTo) { content ->
                signer.sign(createdAt, kind, emptyArray(), content, onReady)
            }
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
    val tags: Array<Array<String>>?,
    val content: String?
) {
    fun mergeWith(event: SealedGossipEvent): Event {
        val newPubKey = pubKey?.ifBlank { null } ?: event.pubKey
        val newCreatedAt = if (createdAt != null && createdAt > 1000) createdAt else event.createdAt
        val newKind = kind ?: -1
        val newTags = (tags ?: emptyArray()).plus(event.tags)
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
