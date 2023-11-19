package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner

@Immutable
class AudioTrackEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : BaseAddressableEvent(id, pubKey, createdAt, kind, tags, content, sig) {

    fun participants() = tags.filter { it.size > 1 && it[0] == "p" }.map { Participant(it[1], it.getOrNull(2)) }
    fun type() = tags.firstOrNull { it.size > 1 && it[0] == TYPE }?.get(1)
    fun price() = tags.firstOrNull { it.size > 1 && it[0] == PRICE }?.get(1)
    fun cover() = tags.firstOrNull { it.size > 1 && it[0] == COVER }?.get(1)

    // fun subject() = tags.firstOrNull { it.size > 1 && it[0] == SUBJECT }?.get(1)
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
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (AudioTrackEvent) -> Unit
        ) {
            val tags = listOfNotNull(
                listOf(MEDIA, media),
                listOf(TYPE, type),
                price?.let { listOf(PRICE, it) },
                cover?.let { listOf(COVER, it) },
                subject?.let { listOf(SUBJECT, it) }
            )

            signer.sign(createdAt, kind, tags, "", onReady)
        }
    }
}

@Immutable
data class Participant(val key: String, val role: String?)
