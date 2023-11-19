package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner

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

        fun createWarning(originalNote: EventInterface, signer: NostrSigner, createdAt: Long = TimeUtils.now(), onReady: (ReactionEvent) -> Unit) {
            return create("\u26A0\uFE0F", originalNote, signer, createdAt, onReady)
        }

        fun createLike(originalNote: EventInterface, signer: NostrSigner, createdAt: Long = TimeUtils.now(), onReady: (ReactionEvent) -> Unit) {
            return create("+", originalNote, signer, createdAt, onReady)
        }

        fun create(content: String, originalNote: EventInterface, signer: NostrSigner, createdAt: Long = TimeUtils.now(), onReady: (ReactionEvent) -> Unit) {
            var tags = listOf(listOf("e", originalNote.id()), listOf("p", originalNote.pubKey()))
            if (originalNote is AddressableEvent) {
                tags = tags + listOf(listOf("a", originalNote.address().toTag()))
            }

            return signer.sign(createdAt, kind, tags, content, onReady)
        }

        fun create(emojiUrl: EmojiUrl, originalNote: EventInterface, signer: NostrSigner, createdAt: Long = TimeUtils.now(), onReady: (ReactionEvent) -> Unit) {
            val content = ":${emojiUrl.code}:"

            var tags = listOf(
                listOf("e", originalNote.id()),
                listOf("p", originalNote.pubKey()),
                listOf("emoji", emojiUrl.code, emojiUrl.url)
            )

            if (originalNote is AddressableEvent) {
                tags = tags + listOf(listOf("a", originalNote.address().toTag()))
            }

            signer.sign(createdAt, kind, tags, content, onReady)
        }
    }
}
