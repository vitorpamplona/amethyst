package com.vitorpamplona.amethyst.service.model

import com.vitorpamplona.amethyst.model.toHexKey
import java.util.Date
import nostr.postr.Utils
import nostr.postr.events.Event

class LongTextNoteEvent(
    id: ByteArray,
    pubKey: ByteArray,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: ByteArray
): Event(id, pubKey, createdAt, kind, tags, content, sig) {
    fun replyTos() = tags.filter { it.firstOrNull() == "e" }.mapNotNull { it.getOrNull(1) }
    fun mentions() = tags.filter { it.firstOrNull() == "p" }.mapNotNull { it.getOrNull(1) }

    fun dTag() = tags.filter { it.firstOrNull() == "d" }.mapNotNull { it.getOrNull(1) }.firstOrNull() ?: ""
    fun address() = ATag(kind, pubKey.toHexKey(), dTag())

    fun topics() = tags.filter { it.firstOrNull() == "t" }.mapNotNull { it.getOrNull(1) }
    fun title() = tags.filter { it.firstOrNull() == "title" }.mapNotNull { it.getOrNull(1) }.firstOrNull()
    fun image() = tags.filter { it.firstOrNull() == "image" }.mapNotNull { it.getOrNull(1) }.firstOrNull()
    fun summary() = tags.filter { it.firstOrNull() == "summary" }.mapNotNull { it.getOrNull(1) }.firstOrNull()

    fun publishedAt() = try {
        tags.filter { it.firstOrNull() == "published_at" }.mapNotNull { it.getOrNull(1) }.firstOrNull()?.toLong()
    } catch (_: Exception) {
        null
    }

    companion object {
        const val kind = 30023

        fun create(msg: String, replyTos: List<String>?, mentions: List<String>?, privateKey: ByteArray, createdAt: Long = Date().time / 1000): LongTextNoteEvent {
            val pubKey = Utils.pubkeyCreate(privateKey)
            val tags = mutableListOf<List<String>>()
            replyTos?.forEach {
                tags.add(listOf("e", it))
            }
            mentions?.forEach {
                tags.add(listOf("p", it))
            }
            val id = generateId(pubKey, createdAt, kind, tags, msg)
            val sig = Utils.sign(id, privateKey)
            return LongTextNoteEvent(id, pubKey, createdAt, tags, msg, sig)
        }
    }
}
