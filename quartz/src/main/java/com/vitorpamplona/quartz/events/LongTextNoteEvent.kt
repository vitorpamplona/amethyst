package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner

@Immutable
class LongTextNoteEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey
) : BaseTextNoteEvent(id, pubKey, createdAt, kind, tags, content, sig), AddressableEvent {
    override fun dTag() = tags.filter { it.firstOrNull() == "d" }.mapNotNull { it.getOrNull(1) }.firstOrNull() ?: ""
    override fun address() = ATag(kind, pubKey, dTag(), null)

    fun topics() = tags.filter { it.firstOrNull() == "t" }.mapNotNull { it.getOrNull(1) }
    fun title() = tags.filter { it.firstOrNull() == "title" }.mapNotNull { it.getOrNull(1) }.firstOrNull()
    fun image() = tags.filter { it.firstOrNull() == "image" }.mapNotNull { it.getOrNull(1) }.firstOrNull()
    fun summary() = tags.filter { it.firstOrNull() == "summary" }.mapNotNull { it.getOrNull(1) }.firstOrNull()

    fun publishedAt() = try {
        tags.firstOrNull { it.size > 1 && it[0] == "published_at" }?.get(1)?.toLongOrNull()
    } catch (_: Exception) {
        null
    }

    companion object {
        const val kind = 30023

        fun create(
            msg: String,
            replyTos: List<String>?,
            mentions: List<String>?,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (LongTextNoteEvent) -> Unit
        ) {
            val tags = mutableListOf<Array<String>>()
            replyTos?.forEach {
                tags.add(arrayOf("e", it))
            }
            mentions?.forEach {
                tags.add(arrayOf("p", it))
            }
            signer.sign(createdAt, kind, tags.toTypedArray(), msg, onReady)
        }
    }
}
