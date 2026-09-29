/*
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
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkBuilder
import com.vitorpamplona.quartz.nip01Core.links.LinkProvider
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip01Core.links.contentMentions
import com.vitorpamplona.quartz.nip01Core.links.links
import com.vitorpamplona.quartz.nip01Core.links.quotes
import com.vitorpamplona.quartz.nip01Core.links.userTags
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip10Notes.BaseThreadedEvent
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag
import com.vitorpamplona.quartz.nip10Notes.tags.markedETags
import com.vitorpamplona.quartz.nip10Notes.tags.prepareMarkedETagsAsReplyTo
import com.vitorpamplona.quartz.nip18Reposts.quotes.QTag
import com.vitorpamplona.quartz.nip19Bech32.addressHints
import com.vitorpamplona.quartz.nip19Bech32.addressIds
import com.vitorpamplona.quartz.nip19Bech32.eventHints
import com.vitorpamplona.quartz.nip19Bech32.eventIds
import com.vitorpamplona.quartz.nip19Bech32.pubKeyHints
import com.vitorpamplona.quartz.nip19Bech32.pubKeys
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.repositoryLinks
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
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
    AddressHintProvider,
    SearchableEvent,
    LinkProvider {
    override fun indexableContent() = content

    // The read path: the same fields indexableContent() joins, handed over without
    // building the joined string a scan would throw away.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        visitor.visit(content)
    }

    override fun eventHints(): List<EventIdHint> {
        val eHints = tags.mapNotNull(ETag::parseAsHint)
        val qHints = tags.mapNotNull(QTag::parseEventAsHint)
        val nip19Hints = citedNIP19().eventHints()

        return eHints + qHints + nip19Hints
    }

    override fun linkedEventIds(): List<HexKey> {
        val eHints = tags.mapNotNull(ETag::parseId)
        val qHints = tags.mapNotNull(QTag::parseEventId)
        val nip19Hints = citedNIP19().eventIds()

        return eHints + qHints + nip19Hints
    }

    override fun addressHints(): List<AddressHint> {
        val aHints = tags.mapNotNull(ATag::parseAsHint)
        val qHints = tags.mapNotNull(QTag::parseAddressAsHint)
        val nip19Hints = citedNIP19().addressHints()

        return aHints + qHints + nip19Hints
    }

    override fun linkedAddressIds(): List<String> {
        val aHints = tags.mapNotNull(ATag::parseAddressId)
        val qHints = tags.mapNotNull(QTag::parseAddressId)
        val nip19Hints = citedNIP19().addressIds()

        return aHints + qHints + nip19Hints
    }

    override fun pubKeyHints(): List<PubKeyHint> {
        val pHints = tags.mapNotNull(PTag::parseAsHint)
        val nip19Hints = citedNIP19().pubKeyHints()

        return pHints + nip19Hints
    }

    override fun linkedPubKeys(): List<HexKey> {
        val pHints = tags.mapNotNull(PTag::parseKey)
        val nip19Hints = citedNIP19().pubKeys()

        return pHints + nip19Hints
    }

    fun repositoryHex() = tags.firstNotNullOfOrNull(ATag::parseAddressId)

    fun repository() = tags.firstNotNullOfOrNull(ATag::parse)

    fun rootIssueOrPatch() = tags.lastNotNullOfOrNull(MarkedETag::parseRootId)

    @Suppress("DEPRECATION")
    /** The repository, the NIP-10 thread (the root is the issue or patch), notified people, quotes and citations. */
    override fun links(): List<Link> =
        links {
            repositoryLinks(tags)
            threadLinks(tags)
            userTags(Relation.MENTION, tags)
            quotes(tags)
            contentMentions(citedNIP19())
        }

    /**
     * NIP-10 threading: marked `e` tags say their role, and a thread with a `root` but no `reply`
     * replies to the root. Unmarked tags are positional: the first is the root, the last the parent,
     * the ones between are mentions.
     */
    private fun LinkBuilder.threadLinks(tags: TagArray) {
        val thread = tags.mapNotNull(MarkedETag::parseAllThreadTags)
        if (thread.isEmpty()) return
        if (thread.any { it.marker == MarkedETag.MARKER.ROOT || it.marker == MarkedETag.MARKER.REPLY }) {
            var root: MarkedETag? = null
            var hasParent = false
            thread.forEach {
                when (it.marker) {
                    MarkedETag.MARKER.ROOT -> {
                        event(Relation.ROOT, it.eventId, "e")
                        if (root == null) root = it
                    }
                    MarkedETag.MARKER.REPLY -> {
                        event(Relation.PARENT, it.eventId, "e")
                        hasParent = true
                    }
                    else -> event(Relation.MENTION, it.eventId, "e")
                }
            }
            if (!hasParent) event(Relation.PARENT, root?.eventId, "e")
        } else {
            thread.forEachIndexed { index, it ->
                if (index == 0) event(Relation.ROOT, it.eventId, "e")
                if (index == thread.lastIndex) event(Relation.PARENT, it.eventId, "e")
                if (index != 0 && index != thread.lastIndex) event(Relation.MENTION, it.eventId, "e")
            }
        }
    }

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
