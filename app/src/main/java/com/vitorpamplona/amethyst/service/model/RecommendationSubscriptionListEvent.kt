package com.vitorpamplona.amethyst.service.model

import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.toHexKey
import nostr.postr.Utils
import java.util.Date

class RecommendationSubscriptionListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : GeneralListEvent(id, pubKey, createdAt, kind, tags, content, sig) {

    companion object {
        const val kind = 10020

        fun removeTag(
            hex: String,
            isPublic: Boolean,
            current: RecommendationSubscriptionListEvent?,

            privateKey: ByteArray,
            createdAt: Long = Date().time / 1000
        ): RecommendationSubscriptionListEvent {
            val pubKey = Utils.pubkeyCreate(privateKey)

            val content = if (isPublic) {
                current?.content ?: ""
            } else {
                var currentTags = current?.privateTags(privateKey) ?: listOf()

                currentTags = currentTags.filter { it.getOrNull(1) != hex }

                val msg = gson.toJson(currentTags)

                Utils.encrypt(
                    msg,
                    privateKey,
                    pubKey
                )
            }

            val tags = if (isPublic) {
                (current?.tags ?: listOf()).filter { it.getOrNull(1) != hex }
            } else {
                (current?.tags ?: listOf())
            }

            val id = generateId(pubKey.toHexKey(), createdAt, kind, tags, content)
            val sig = Utils.sign(id, privateKey)
            return RecommendationSubscriptionListEvent(id.toHexKey(), pubKey.toHexKey(), createdAt, tags, content, sig.toHexKey())
        }

        fun addTag(
            type: String,
            hex: String,
            relay: String,
            isPublic: Boolean,
            current: RecommendationSubscriptionListEvent?,

            privateKey: ByteArray,
            createdAt: Long = Date().time / 1000
        ): RecommendationSubscriptionListEvent {
            val pubKey = Utils.pubkeyCreate(privateKey)

            val content = if (isPublic) {
                current?.content ?: ""
            } else {
                var currentTags = current?.privateTags(privateKey) ?: listOf()

                currentTags = currentTags + listOf(listOf(type, hex, relay))

                val msg = gson.toJson(currentTags)

                Utils.encrypt(
                    msg,
                    privateKey,
                    pubKey
                )
            }

            val tags = if (isPublic) {
                (current?.tags ?: listOf()) + listOf(listOf(type, hex, relay))
            } else {
                (current?.tags ?: listOf())
            }

            val id = generateId(pubKey.toHexKey(), createdAt, kind, tags, content)
            val sig = Utils.sign(id, privateKey)
            return RecommendationSubscriptionListEvent(id.toHexKey(), pubKey.toHexKey(), createdAt, tags, content, sig.toHexKey())
        }
    }
}
