package com.vitorpamplona.amethyst.service.model

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.toHexKey
import nostr.postr.Utils
import java.util.Date

@Immutable
class AudioTrackEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig), AddressableEvent {

    override fun dTag() = tags.firstOrNull { it.size > 1 && it[0] == "d" }?.get(1) ?: ""
    override fun address() = ATag(kind, pubKey, dTag(), null)

    fun participants() = tags.filter { it.size > 1 && it[0] == "p" }.map { Participant(it[1], it.getOrNull(2)) }
    fun type() = tags.firstOrNull { it.size > 1 && it[0] == TYPE }?.get(1)
    fun price() = tags.firstOrNull { it.size > 1 && it[0] == PRICE }?.get(1)
    fun cover() = tags.firstOrNull { it.size > 1 && it[0] == COVER }?.get(1)
    fun subject() = tags.firstOrNull { it.size > 1 && it[0] == SUBJECT }?.get(1)
    fun media() = tags.firstOrNull { it.size > 1 && it[0] == MEDIA }?.get(1)

    companion object {
        const val kind = 31337

        private const val TYPE = "c"
        private const val PRICE = "price"
        private const val COVER = "cover"
        private const val SUBJECT = "subject"
        private const val MEDIA = "media"

        fun create(
            type: String,
            media: String,
            price: String? = null,
            cover: String? = null,
            subject: String? = null,
            privateKey: ByteArray,
            createdAt: Long = Date().time / 1000
        ): AudioTrackEvent {
            val tags = listOfNotNull(
                listOf(MEDIA, media),
                listOf(TYPE, type),
                price?.let { listOf(PRICE, it) },
                cover?.let { listOf(COVER, it) },
                subject?.let { listOf(SUBJECT, it) }
            )

            val pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            val id = generateId(pubKey, createdAt, kind, tags, "")
            val sig = Utils.sign(id, privateKey)
            return AudioTrackEvent(id.toHexKey(), pubKey, createdAt, tags, "", sig.toHexKey())
        }
    }
}

data class Participant(val key: String, val role: String?)
