package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey

@Immutable
class BookmarkListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : GeneralListEvent(id, pubKey, createdAt, kind, tags, content, sig) {
    var decryptedContent = ""

    companion object {
        const val kind = 30001

        fun create(
            name: String = "",
            events: List<String>? = null,
            users: List<String>? = null,
            addresses: List<ATag>? = null,
            content: String,
            pubKey: HexKey,
            createdAt: Long = TimeUtils.now()
        ): BookmarkListEvent {
            val tags = mutableListOf<List<String>>()
            tags.add(listOf("d", name))

            events?.forEach {
                tags.add(listOf("e", it))
            }
            users?.forEach {
                tags.add(listOf("p", it))
            }
            addresses?.forEach {
                tags.add(listOf("a", it.toTag()))
            }

            val id = generateId(pubKey, createdAt, kind, tags, content)
            return BookmarkListEvent(id.toHexKey(), pubKey, createdAt, tags, content, "")
        }

        fun create(
            name: String = "",

            events: List<String>? = null,
            users: List<String>? = null,
            addresses: List<ATag>? = null,

            privEvents: List<String>? = null,
            privUsers: List<String>? = null,
            privAddresses: List<ATag>? = null,

            privateKey: ByteArray,
            createdAt: Long = TimeUtils.now()
        ): BookmarkListEvent {
            val pubKey = CryptoUtils.pubkeyCreate(privateKey)
            val content = createPrivateTags(privEvents, privUsers, privAddresses, privateKey, pubKey)

            val tags = mutableListOf<List<String>>()
            tags.add(listOf("d", name))

            events?.forEach {
                tags.add(listOf("e", it))
            }
            users?.forEach {
                tags.add(listOf("p", it))
            }
            addresses?.forEach {
                tags.add(listOf("a", it.toTag()))
            }

            val id = generateId(pubKey.toHexKey(), createdAt, kind, tags, content)
            val sig = CryptoUtils.sign(id, privateKey)
            return BookmarkListEvent(id.toHexKey(), pubKey.toHexKey(), createdAt, tags, content, sig.toHexKey())
        }

        fun create(
            unsignedEvent: BookmarkListEvent, signature: String
        ): BookmarkListEvent {
            return BookmarkListEvent(unsignedEvent.id, unsignedEvent.pubKey, unsignedEvent.createdAt, unsignedEvent.tags, unsignedEvent.content, signature)
        }
    }
}
