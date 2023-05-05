package com.vitorpamplona.amethyst.service.model

import android.util.Log
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.toHexKey
import nostr.postr.Utils
import java.util.Date

class RecommendationResponseEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {

    fun requestEvent() = tags.firstOrNull { it.size < 3 && it[0] == "e" }?.getOrNull(1)

    fun recommendedEvents() = tags.filter { it.size > 1 && it[0] == "e" }.mapNotNull {
        try {
            EventScores(it[1], it.getOrNull(2), it.getOrNull(3)?.toFloat())
        } catch (e: Exception) {
            Log.w("RecommendationResponseEvent", "Unable to parse recommendation score: ${it[1]}, ${it[2]}, ${it[3]}")
            null
        }
    }

    fun recommendedPeople() = tags.filter { it.size > 1 && it[0] == "p" }.mapNotNull {
        try {
            PubKeyScores(it[1], it.getOrNull(2), it.getOrNull(3)?.toFloat())
        } catch (e: Exception) {
            Log.w("RecommendationResponseEvent", "Unable to parse recommendation score: ${it[1]}, ${it[2]}, ${it[3]}")
            null
        }
    }

    companion object {
        const val kind = 20021

        fun create(
            users: List<String>,
            events: List<String>,
            filters: RecommendationFilter,
            requestingEvent: String,
            requestingPubKey: String,
            privateKey: ByteArray,
            createdAt: Long = Date().time / 1000
        ): RecommendationResponseEvent {
            val pubKey = Utils.pubkeyCreate(privateKey)
            val content = filters.toJson()

            val tags = mutableListOf<List<String>>()
            tags.add(listOf("e", requestingEvent))
            tags.add(listOf("p", requestingPubKey))
            users.forEach {
                tags.add(listOf("p", it))
            }
            events.forEach {
                tags.add(listOf("e", it))
            }

            val id = generateId(pubKey.toHexKey(), createdAt, kind, tags, content)
            val sig = Utils.sign(id, privateKey)
            return RecommendationResponseEvent(id.toHexKey(), pubKey.toHexKey(), createdAt, tags, content, sig.toHexKey())
        }
    }
}

class EventScores(val idHex: HexKey, val relay: String?, val score: Float?)
class PubKeyScores(val pubkeyHex: HexKey, val relay: String?, val score: Float?)
