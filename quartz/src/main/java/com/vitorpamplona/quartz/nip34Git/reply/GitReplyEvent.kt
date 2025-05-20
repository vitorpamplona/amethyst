/**
 * Copyright (c) 2025 Vitor Pamplona
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
package com.vitorpamplona.quartz.nip34Git.reply

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.addressables.ATag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip10Notes.BaseThreadedEvent
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag
import com.vitorpamplona.quartz.nip10Notes.tags.markedETags
import com.vitorpamplona.quartz.nip10Notes.tags.prepareMarkedETagsAsReplyTo
import com.vitorpamplona.quartz.nip18Reposts.quotes.QTag
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.lastNotNullOfOrNull

@Immutable
@Deprecated("Replaced by NIP-22")
class GitReplyEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseThreadedEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    PubKeyHintProvider,
    EventHintProvider,
    AddressHintProvider {
    override fun pubKeyHints() = tags.mapNotNull(PTag::parseAsHint)

    override fun eventHints() = tags.mapNotNull(MarkedETag::parseAsHint) + tags.mapNotNull(QTag::parseEventAsHint)

    override fun addressHints() = tags.mapNotNull(ATag::parseAsHint) + tags.mapNotNull(QTag::parseAddressAsHint)

    fun repositoryHex() = tags.firstNotNullOfOrNull(ATag::parseAddressId)

    fun repository() = tags.firstNotNullOfOrNull(ATag::parse)

    fun rootIssueOrPatch() = tags.lastNotNullOfOrNull(MarkedETag::parseRootId)

    companion object {
        const val KIND = 1622
        const val ALT_DESCRIPTION = "A Git Reply"

        @Deprecated("Replaced by NIP-22")
        fun reply(
            post: String,
            replyingTo: EventHintBundle<GitReplyEvent>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<GitReplyEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, post, createdAt) {
            replyingTo.event.repository()?.let { repository(it) }
            markedETags(prepareMarkedETagsAsReplyTo(replyingTo))

            initializer()
        }

        @Deprecated("Replaced by NIP-22")
        fun replyIssue(
            post: String,
            issue: EventHintBundle<GitIssueEvent>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<GitReplyEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, post, createdAt) {
            issue.event.repository()?.let { repository(it) }
            issue(issue)

            initializer()
        }

        @Deprecated("Replaced by NIP-22")
        fun replyPatch(
            post: String,
            patch: EventHintBundle<GitPatchEvent>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<GitReplyEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, post, createdAt) {
            patch.event.repository()?.let { repository(it) }
            patch(patch)

            initializer()
        }
    }
}
