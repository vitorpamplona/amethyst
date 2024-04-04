/**
 * Copyright (c) 2024 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.quartz.events

import android.util.Log
import androidx.compose.runtime.Immutable
import com.fasterxml.jackson.annotation.JsonProperty
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class SealedGossipEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : WrappedEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    @Transient private var cachedInnerEvent: Map<HexKey, Event?> = mapOf()

    override fun isContentEncoded() = true

    fun preCachedGossip(signer: NostrSigner): Event? {
        return cachedInnerEvent[signer.pubKey]
    }

    fun addToCache(
        pubKey: HexKey,
        gift: Event,
    ) {
        cachedInnerEvent = cachedInnerEvent + Pair(pubKey, gift)
    }

    fun cachedGossip(
        signer: NostrSigner,
        onReady: (Event) -> Unit,
    ) {
        cachedInnerEvent[signer.pubKey]?.let {
            onReady(it)
            return
        }

        unseal(signer) { gossip ->
            val event = gossip.mergeWith(this)
            if (event is WrappedEvent) {
                event.host = host ?: this
            }
            addToCache(signer.pubKey, event)

            onReady(event)
        }
    }

    private fun unseal(
        signer: NostrSigner,
        onReady: (Gossip) -> Unit,
    ) {
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

    private fun plainContent(
        signer: NostrSigner,
        onReady: (String) -> Unit,
    ) {
        if (content.isEmpty()) return

        signer.nip44Decrypt(content, pubKey, onReady)
    }

    companion object {
        const val KIND = 13

        fun create(
            event: Event,
            encryptTo: HexKey,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (SealedGossipEvent) -> Unit,
        ) {
            val gossip = Gossip.create(event)
            create(gossip, encryptTo, signer, createdAt, onReady)
        }

        fun create(
            gossip: Gossip,
            encryptTo: HexKey,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.randomWithinAWeek(),
            onReady: (SealedGossipEvent) -> Unit,
        ) {
            val msg = Gossip.toJson(gossip)

            signer.nip44Encrypt(msg, encryptTo) { content ->
                signer.sign(createdAt, KIND, emptyArray(), content, onReady)
            }
        }
    }
}

class Gossip(
    val id: HexKey?,
    @JsonProperty("pubkey") val pubKey: HexKey?,
    @JsonProperty("created_at") val createdAt: Long?,
    val kind: Int?,
    val tags: Array<Array<String>>?,
    val content: String?,
) {
    fun mergeWith(event: SealedGossipEvent): Event {
        val newPubKey = pubKey?.ifBlank { null } ?: event.pubKey
        val newCreatedAt = if (createdAt != null && createdAt > 1000) createdAt else event.createdAt
        val newKind = kind ?: -1
        val newTags = (tags ?: emptyArray()).plus(event.tags)
        val newContent = content ?: ""
        val newID =
            id?.ifBlank { null }
                ?: Event.generateId(newPubKey, newCreatedAt, newKind, newTags, newContent).toHexKey()
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
