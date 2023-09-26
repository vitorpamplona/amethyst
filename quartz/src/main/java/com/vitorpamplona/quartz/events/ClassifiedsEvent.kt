package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey

@Immutable
class ClassifiedsEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : BaseAddressableEvent(id, pubKey, createdAt, kind, tags, content, sig) {
    fun title() = tags.firstOrNull { it.size > 1 && it[0] == "title" }?.get(1)
    fun image() = tags.firstOrNull { it.size > 1 && it[0] == "image" }?.get(1)
    fun summary() = tags.firstOrNull { it.size > 1 && it[0] == "summary" }?.get(1)
    fun price() = tags.firstOrNull { it.size > 1 && it[0] == "price" }?.let {
        Price(it[1], it.getOrNull(2), it.getOrNull(3))
    }
    fun location() = tags.firstOrNull { it.size > 1 && it[0] == "location" }?.get(1)

    fun publishedAt() = try {
        tags.firstOrNull { it.size > 1 && it[0] == "published_at" }?.get(1)?.toLongOrNull()
    } catch (_: Exception) {
        null
    }

    companion object {
        const val kind = 30402

        fun create(
            dTag: String,
            title: String?,
            image: String?,
            summary: String?,
            price: Price?,
            location: String?,
            publishedAt: Long?,
            privateKey: ByteArray,
            createdAt: Long = TimeUtils.now()
        ): ClassifiedsEvent {
            val tags = mutableListOf<List<String>>()

            tags.add(listOf("d", dTag))
            title?.let { tags.add(listOf("title", it)) }
            image?.let { tags.add(listOf("image", it)) }
            summary?.let { tags.add(listOf("summary", it)) }
            price?.let {
                if (it.frequency != null && it.currency != null) {
                    tags.add(listOf("price", it.amount, it.currency, it.frequency))
                } else if (it.currency != null) {
                    tags.add(listOf("price", it.amount, it.currency))
                } else {
                    tags.add(listOf("price", it.amount))
                }
            }
            location?.let { tags.add(listOf("location", it)) }
            publishedAt?.let { tags.add(listOf("publishedAt", it.toString())) }
            title?.let { tags.add(listOf("title", it)) }

            val pubKey = CryptoUtils.pubkeyCreate(privateKey).toHexKey()
            val id = generateId(pubKey, createdAt, kind, tags, "")
            val sig = CryptoUtils.sign(id, privateKey)
            return ClassifiedsEvent(id.toHexKey(), pubKey, createdAt, tags, "", sig.toHexKey())
        }
    }
}

data class Price(val amount: String, val currency: String?, val frequency: String?)
