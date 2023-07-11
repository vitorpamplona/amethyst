package com.vitorpamplona.amethyst.service.model

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.TimeUtils
import com.vitorpamplona.amethyst.model.toHexKey
import com.vitorpamplona.amethyst.service.relays.Client
import nostr.postr.Utils

@Immutable
class GenericRepostEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {

    fun boostedPost() = tags.filter { it.firstOrNull() == "e" }.mapNotNull { it.getOrNull(1) }
    fun originalAuthor() = tags.filter { it.firstOrNull() == "p" }.mapNotNull { it.getOrNull(1) }

    fun containedPost() = try {
        fromJson(content, Client.lenient)
    } catch (e: Exception) {
        null
    }

    companion object {
        const val kind = 16

        fun create(boostedPost: EventInterface, privateKey: ByteArray, createdAt: Long = TimeUtils.now()): GenericRepostEvent {
            val content = boostedPost.toJson()

            val replyToPost = listOf("e", boostedPost.id())
            val replyToAuthor = listOf("p", boostedPost.pubKey())

            val pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            var tags: List<List<String>> = listOf(replyToPost, replyToAuthor)

            if (boostedPost is AddressableEvent) {
                tags = tags + listOf(listOf("a", boostedPost.address().toTag()))
            }

            tags = tags + listOf(listOf("k", "${boostedPost.kind()}"))

            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = Utils.sign(id, privateKey)
            return GenericRepostEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }
    }
}
