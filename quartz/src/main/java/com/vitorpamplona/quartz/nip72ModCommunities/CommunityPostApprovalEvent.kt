/**
 * Copyright (c) 2024 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.quartz.nip72ModCommunities

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.addressables.ATag
import com.vitorpamplona.quartz.nip19Bech32.parse
import com.vitorpamplona.quartz.nip31Alts.AltTagSerializer
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class CommunityPostApprovalEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun containedPost(): Event? =
        try {
            content.ifBlank { null }?.let { fromJson(it) }
        } catch (e: Exception) {
            Log.w(
                "CommunityPostEvent",
                "Failed to Parse Community Approval Contained Post of $id with $content",
            )
            null
        }

    fun communities() =
        tags
            .filter { it.size > 1 && it[0] == "a" }
            .mapNotNull {
                val aTag = ATag.parse(it[1], it.getOrNull(2))

                if (aTag?.kind == CommunityDefinitionEvent.KIND) {
                    aTag
                } else {
                    null
                }
            }

    fun approvedEvents() =
        tags
            .filter {
                it.size > 1 &&
                    (
                        it[0] == "e" ||
                            (it[0] == "a" && ATag.parse(it[1], null)?.kind != CommunityDefinitionEvent.KIND)
                    )
            }.map { it[1] }

    companion object {
        const val KIND = 4550
        const val ALT = "Community post approval"

        fun create(
            approvedPost: Event,
            community: CommunityDefinitionEvent,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (CommunityPostApprovalEvent) -> Unit,
        ) {
            val content = approvedPost.toJson()

            val communities = arrayOf("a", community.address().toTag())
            val replyToPost = arrayOf("e", approvedPost.id)
            val replyToAuthor = arrayOf("p", approvedPost.pubKey)
            val innerKind = arrayOf("k", "${approvedPost.kind}")
            val alt = AltTagSerializer.toTagArray(ALT)

            val tags: Array<Array<String>> =
                arrayOf(communities, replyToPost, replyToAuthor, innerKind, alt)

            signer.sign(createdAt, KIND, tags, content, onReady)
        }
    }
}
