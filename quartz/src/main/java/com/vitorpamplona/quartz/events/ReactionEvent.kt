package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.HexKey

@Immutable
class ReactionEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {

    fun originalPost() = tags.filter { it.size > 1 && it[0] == "e" }.map { it[1] }
    fun originalAuthor() = tags.filter { it.size > 1 && it[0] == "p" }.map { it[1] }

    companion object {
        const val kind = 7

        fun createWarning(originalNote: EventInterface, privateKey: ByteArray, createdAt: Long = TimeUtils.now()): ReactionEvent {
            return create("\u26A0\uFE0F", originalNote, privateKey, createdAt)
        }

        fun createLike(originalNote: EventInterface, privateKey: ByteArray, createdAt: Long = TimeUtils.now()): ReactionEvent {
            return create("+", originalNote, privateKey, createdAt)
        }

        fun create(content: String, originalNote: EventInterface, privateKey: ByteArray, createdAt: Long = TimeUtils.now()): ReactionEvent {
            val pubKey = CryptoUtils.pubkeyCreate(privateKey).toHexKey()

            var tags = listOf(listOf("e", originalNote.id()), listOf("p", originalNote.pubKey()))
            if (originalNote is AddressableEvent) {
                tags = tags + listOf(listOf("a", originalNote.address().toTag()))
            }

            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = CryptoUtils.sign(id, privateKey)
            return ReactionEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }

        fun create(emojiUrl: EmojiUrl, originalNote: EventInterface, privateKey: ByteArray, createdAt: Long = TimeUtils.now()): ReactionEvent {
            val content = ":${emojiUrl.code}:"
            val pubKey = CryptoUtils.pubkeyCreate(privateKey).toHexKey()

            var tags = listOf(
                listOf("e", originalNote.id()),
                listOf("p", originalNote.pubKey()),
                listOf("emoji", emojiUrl.code, emojiUrl.url)
            )

            if (originalNote is AddressableEvent) {
                tags = tags + listOf(listOf("a", originalNote.address().toTag()))
            }

            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = CryptoUtils.sign(id, privateKey)
            return ReactionEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }
    }
}
