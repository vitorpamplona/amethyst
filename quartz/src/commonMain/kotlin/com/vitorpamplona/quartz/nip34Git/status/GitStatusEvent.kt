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
package com.vitorpamplona.quartz.nip34Git.status

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.fastForEach
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkBuilder
import com.vitorpamplona.quartz.nip01Core.links.LinkProvider
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip01Core.links.links
import com.vitorpamplona.quartz.nip01Core.links.valueTags
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag
import com.vitorpamplona.quartz.nip34Git.gitPeopleLinks
import com.vitorpamplona.quartz.nip34Git.repositoryLinks
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent

/**
 * Common base for NIP-34 status events (kinds 1630/1631/1632/1633). Each
 * event reports the status of a patch, pull request, or issue via an `e`
 * tag marked `root` pointing at the target event, optionally followed by an
 * `e` tag marked `reply` for revision chains.
 */
abstract class GitStatusEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    kind: Int,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, kind, tags, content, sig),
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

    override fun pubKeyHints() = tags.mapNotNull(PTag::parseAsHint)

    override fun linkedPubKeys() = tags.mapNotNull(PTag::parseKey)

    override fun eventHints() = tags.mapNotNull(MarkedETag::parseAsHint)

    override fun linkedEventIds() = tags.mapNotNull(MarkedETag::parseId)

    override fun addressHints() = tags.mapNotNull(ATag::parseAsHint)

    override fun linkedAddressIds() = tags.mapNotNull(ATag::parseAddressId)

    /** The target event ID (patch / PR / issue) this status refers to. */
    fun rootEventId(): HexKey? = tags.firstNotNullOfOrNull(MarkedETag::parseRootId)

    /** The accepted revision root ID when the status applies to a revision. */
    fun replyEventId(): HexKey? = tags.firstNotNullOfOrNull(MarkedETag::parseReply)?.eventId

    fun repositoryAddress() = tags.firstNotNullOfOrNull(ATag::parseAddress)

    fun repository() = tags.firstNotNullOfOrNull(ATag::parse)

    /** Earliest unique commit or merge/applied commit IDs, encoded as plain `r` tags. */
    fun referenceCommits(): List<String> =
        tags.mapNotNull { tag ->
            if (tag.size > 1 && tag[0] == "r" && tag[1].isNotEmpty()) tag[1] else null
        }

    /**
     * NIP-34 statuses: the `e` marked `root` is the issue, PR or patch, the `e` marked `reply` the
     * accepted revision. Their authors are read from the `e` tags' author slot so the unmarked `p`
     * tags can be told apart (see [gitPeopleLinks]).
     */
    override fun links(): List<Link<*>> =
        links {
            var rootAuthor: HexKey? = null
            var parentAuthor: HexKey? = null
            tags.fastForEach {
                val thread = MarkedETag.parseAllThreadTags(it) ?: return@fastForEach
                when (thread.marker) {
                    MarkedETag.MARKER.ROOT -> {
                        event(Relation.ROOT, thread.eventId, "e")
                        if (rootAuthor == null) rootAuthor = LinkBuilder.normalizedHex(thread.author)
                    }
                    MarkedETag.MARKER.REPLY -> {
                        event(Relation.PARENT, thread.eventId, "e")
                        if (parentAuthor == null) parentAuthor = LinkBuilder.normalizedHex(thread.author)
                    }
                    else -> event(Relation.MENTION, thread.eventId, "e")
                }
            }
            gitPeopleLinks(tags, repositoryLinks(tags), rootAuthor, parentAuthor)
            valueTags(Relation.TAG, tags, "r")
        }

    companion object {
        const val KIND_OPEN = 1630
        const val KIND_APPLIED = 1631
        const val KIND_CLOSED = 1632
        const val KIND_DRAFT = 1633
    }
}
