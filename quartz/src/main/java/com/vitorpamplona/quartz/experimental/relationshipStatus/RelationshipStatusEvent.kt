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
package com.vitorpamplona.quartz.experimental.relationshipStatus

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.relationshipStatus.tags.PetnameTag
import com.vitorpamplona.quartz.experimental.relationshipStatus.tags.RankTag
import com.vitorpamplona.quartz.experimental.relationshipStatus.tags.SummaryTag
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.tagArray
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.dTags.dTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip51Lists.Nip51PrivateTags
import com.vitorpamplona.quartz.nip51Lists.PrivateTagArrayEvent
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class RelationshipStatusEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : PrivateTagArrayEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun rank() = tags.firstNotNullOfOrNull(RankTag::parse)

    fun petname() = tags.firstNotNullOfOrNull(PetnameTag::parse)

    fun summary() = tags.firstNotNullOfOrNull(SummaryTag::parse)

    companion object {
        const val KIND = 30382
        const val ALT = "Relationship Status"

        fun create(
            targetUser: HexKey,
            petname: String? = null,
            summary: String? = null,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            publicInitializer: TagArrayBuilder<RelationshipStatusEvent>.() -> Unit = {},
            privateInitializer: TagArrayBuilder<RelationshipStatusEvent>.() -> Unit = {},
            onReady: (RelationshipStatusEvent) -> Unit,
        ) {
            val publicTags =
                tagArray {
                    alt(ALT)
                    dTag(targetUser)
                    publicInitializer()
                }

            val privateTags =
                tagArray {
                    petname?.let { petname(it) }
                    summary?.let { summary(it) }
                    privateInitializer()
                }

            Nip51PrivateTags.encryptNip44(privateTags, signer) { content ->
                signer.sign(createdAt, KIND, publicTags, content, onReady)
            }
        }
    }
}
