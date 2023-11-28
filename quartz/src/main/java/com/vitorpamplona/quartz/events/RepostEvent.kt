package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner

@Immutable
class RepostEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {

    fun boostedPost() = taggedEvents()
    fun originalAuthor() = taggedUsers()

    fun containedPost() = try {
        fromJson(content)
    } catch (e: Exception) {
        null
    }

    companion object {
        const val kind = 6

        fun create(
            boostedPost: EventInterface,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (RepostEvent) -> Unit
        ) {
            val content = boostedPost.toJson()

            val replyToPost = arrayOf("e", boostedPost.id())
            val replyToAuthor = arrayOf("p", boostedPost.pubKey())

            var tags: Array<Array<String>> = arrayOf(replyToPost, replyToAuthor)

            if (boostedPost is AddressableEvent) {
                tags = tags + listOf(arrayOf("a", boostedPost.address().toTag()))
            }

            signer.sign(createdAt, kind, tags, content, onReady)
        }
    }
}
