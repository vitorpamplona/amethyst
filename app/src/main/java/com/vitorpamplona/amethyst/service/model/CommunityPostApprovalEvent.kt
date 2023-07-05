package com.vitorpamplona.amethyst.service.model

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.toHexKey
import com.vitorpamplona.amethyst.service.relays.Client
import nostr.postr.Utils
import java.util.Date

@Immutable
class CommunityPostApprovalEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {
    fun containedPost(): Event? = try {
        content.ifBlank { null }?.let {
            fromJson(it, Client.lenient)
        }
    } catch (e: Exception) {
        Log.e("LnZapEvent", "Failed to Parse Contained Post $content", e)
        null
    }

    companion object {
        const val kind = 4550

        fun create(approvedPost: Event, community: CommunityDefinitionEvent, privateKey: ByteArray, createdAt: Long = Date().time / 1000): GenericRepostEvent {
            val content = approvedPost.toJson()

            val communities = listOf("a", community.address().toTag())
            val replyToPost = listOf("e", approvedPost.id())
            val replyToAuthor = listOf("p", approvedPost.pubKey())
            val kind = listOf("k", "${approvedPost.kind()}")

            val pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            val tags: List<List<String>> = listOf(communities, replyToPost, replyToAuthor, kind)
            val id = generateId(pubKey, createdAt, GenericRepostEvent.kind, tags, content)
            val sig = Utils.sign(id, privateKey)
            return GenericRepostEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }
    }
}
