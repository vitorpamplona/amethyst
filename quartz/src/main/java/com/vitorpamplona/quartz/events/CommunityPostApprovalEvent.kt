package com.vitorpamplona.quartz.events

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner

@Immutable
class CommunityPostApprovalEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
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

        fun create(
            approvedPost: Event,
            community: CommunityDefinitionEvent,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (CommunityPostApprovalEvent) -> Unit
        ) {
            val content = approvedPost.toJson()

            val communities = arrayOf("a", community.address().toTag())
            val replyToPost = arrayOf("e", approvedPost.id())
            val replyToAuthor = arrayOf("p", approvedPost.pubKey())
            val innerKind = arrayOf("k", "${approvedPost.kind()}")

            val tags: Array<Array<String>> = arrayOf(communities, replyToPost, replyToAuthor, innerKind)

            signer.sign(createdAt, kind, tags, content, onReady)
        }
    }
}
