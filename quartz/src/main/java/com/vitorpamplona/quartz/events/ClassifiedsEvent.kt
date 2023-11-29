package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner

@Immutable
class ClassifiedsEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey
) : BaseAddressableEvent(id, pubKey, createdAt, kind, tags, content, sig) {
    fun title() = tags.firstOrNull { it.size > 1 && it[0] == "title" }?.get(1)
    fun image() = tags.firstOrNull { it.size > 1 && it[0] == "image" }?.get(1)
    fun images() = tags.filter { it.size > 1 && it[0] == "image" }.map { it[1] }
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
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ClassifiedsEvent) -> Unit
        ) {
            val tags = mutableListOf<Array<String>>()

            tags.add(arrayOf("d", dTag))
            title?.let { tags.add(arrayOf("title", it)) }
            image?.let { tags.add(arrayOf("image", it)) }
            summary?.let { tags.add(arrayOf("summary", it)) }
            price?.let {
                if (it.frequency != null && it.currency != null) {
                    tags.add(arrayOf("price", it.amount, it.currency, it.frequency))
                } else if (it.currency != null) {
                    tags.add(arrayOf("price", it.amount, it.currency))
                } else {
                    tags.add(arrayOf("price", it.amount))
                }
            }
            location?.let { tags.add(arrayOf("location", it)) }
            publishedAt?.let { tags.add(arrayOf("publishedAt", it.toString())) }
            title?.let { tags.add(arrayOf("title", it)) }

            signer.sign(createdAt, kind, tags.toTypedArray(), "", onReady)
        }
    }
}

data class Price(val amount: String, val currency: String?, val frequency: String?)
