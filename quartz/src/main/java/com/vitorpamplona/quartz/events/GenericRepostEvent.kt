package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner

@Immutable
class GenericRepostEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {

    fun boostedPost() = tags.filter { it.firstOrNull() == "e" }.mapNotNull { it.getOrNull(1) }
    fun originalAuthor() = tags.filter { it.firstOrNull() == "p" }.mapNotNull { it.getOrNull(1) }

    fun containedPost() = try {
        fromJson(content)
    } catch (e: Exception) {
        null
    }

    companion object {
        const val kind = 16

        fun create(
            boostedPost: EventInterface,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (GenericRepostEvent) -> Unit
        ) {
            val content = boostedPost.toJson()

            val replyToPost = arrayOf("e", boostedPost.id())
            val replyToAuthor = arrayOf("p", boostedPost.pubKey())

            var tags: List<Array<String>> = listOf(replyToPost, replyToAuthor)

            if (boostedPost is AddressableEvent) {
                tags = tags + listOf(arrayOf("a", boostedPost.address().toTag()))
            }

            tags = tags + listOf(arrayOf("k", "${boostedPost.kind()}"))

            signer.sign(createdAt, kind, tags.toTypedArray(), content, onReady)
        }
    }
}
