package com.vitorpamplona.quartz.events

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey

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
            fromJson(it)
        }
    } catch (e: Exception) {
        Log.w("CommunityPostEvent", "Failed to Parse Community Approval Contained Post of $id with $content")
        null
    }

    fun communities() = tags.filter { it.size > 1 && it[0] == "a" }.mapNotNull {
        val aTag = ATag.parse(it[1], it.getOrNull(2))

        if (aTag?.kind == CommunityDefinitionEvent.kind) {
            aTag
        } else {
            null
        }
    }

    fun approvedEvents() = tags.filter {
        it.size > 1 && (it[0] == "e" || (it[0] == "a" && ATag.parse(it[1], null)?.kind != CommunityDefinitionEvent.kind))
    }.map {
        it[1]
    }

    companion object {
        const val kind = 4550

        fun create(approvedPost: Event, community: CommunityDefinitionEvent, privateKey: ByteArray, createdAt: Long = TimeUtils.now()): GenericRepostEvent {
            val content = approvedPost.toJson()

            val communities = listOf("a", community.address().toTag())
            val replyToPost = listOf("e", approvedPost.id())
            val replyToAuthor = listOf("p", approvedPost.pubKey())
            val kind = listOf("k", "${approvedPost.kind()}")

            val pubKey = CryptoUtils.pubkeyCreate(privateKey).toHexKey()
            val tags: List<List<String>> = listOf(communities, replyToPost, replyToAuthor, kind)
            val id = generateId(pubKey, createdAt, GenericRepostEvent.kind, tags, content)
            val sig = CryptoUtils.sign(id, privateKey)
            return GenericRepostEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }
    }
}
