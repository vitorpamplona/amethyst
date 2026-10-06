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
package com.vitorpamplona.quartz.nip10Notes

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.forks.IForkableEvent
import com.vitorpamplona.quartz.experimental.forks.parseForkedAddress
import com.vitorpamplona.quartz.experimental.forks.parseForkedEventId
import com.vitorpamplona.quartz.nip01Core.core.AddressSerializer
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag
import com.vitorpamplona.quartz.nip10Notes.tags.markedETags
import com.vitorpamplona.quartz.nip10Notes.tags.prepareETagsAsReplyTo
import com.vitorpamplona.quartz.nip14Subject.subject
import com.vitorpamplona.quartz.nip18Reposts.quotes.QTag
import com.vitorpamplona.quartz.nip19Bech32.addressHints
import com.vitorpamplona.quartz.nip19Bech32.addressIds
import com.vitorpamplona.quartz.nip19Bech32.eventHints
import com.vitorpamplona.quartz.nip19Bech32.eventIds
import com.vitorpamplona.quartz.nip19Bech32.pubKeyHints
import com.vitorpamplona.quartz.nip19Bech32.pubKeys
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplitHintsTo
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplitPubKeysTo
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class TextNoteEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseThreadedEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider,
    AddressHintProvider,
    PubKeyHintProvider,
    IForkableEvent,
    SearchableEvent {
    override fun indexableContent() = listOfNotNull(subject(), content).joinToString("\n")

    // The read path: hands over the same fields indexableContent() joins, without
    // building the joined string a scan would throw away.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(subject())) return
        visitor.visit(content)
    }

    override fun eventHints(): List<EventIdHint> {
        val eHints = tags.mapNotNull(MarkedETag::parseAsHint)
        val qHints = tags.mapNotNull(QTag::parseEventAsHint)
        val nip19Hints = citedNIP19().eventHints()

        return eHints + qHints + nip19Hints
    }

    override fun linkedEventIds(): List<HexKey> {
        val result = ArrayList<HexKey>()
        result.addAll(threadEventIds())
        quotedEvents().mapTo(result) { it.eventId }
        result.addAll(citedNIP19().eventIds())
        return result
    }

    override fun addressHints(): List<AddressHint> {
        val aHints = tags.mapNotNull(ATag::parseAsHint)
        val qHints = tags.mapNotNull(QTag::parseAddressAsHint)
        val nip19Hints = citedNIP19().addressHints()

        return aHints + qHints + nip19Hints
    }

    override fun linkedAddressIds(): List<String> {
        val result = ArrayList<String>()
        result.addAll(referencedAddresses())
        quotedAddresses().mapTo(result) { it.address.toValue() }
        result.addAll(citedNIP19().addressIds())
        return result
    }

    // Runs on every relay copy of every note: one list, filled in the order the old
    // `p + zap + nip19` concatenation produced, instead of three lists and two copies.
    override fun pubKeyHints(): List<PubKeyHint> {
        val result = tags.mapNotNullTo(ArrayList(), PTag::parseAsHint)
        tags.zapSplitHintsTo(result)
        result.addAll(citedNIP19().pubKeyHints())
        return result
    }

    override fun linkedPubKeys(): List<HexKey> {
        val result = ArrayList<HexKey>()
        result.addAll(mentionKeys())
        tags.zapSplitPubKeysTo(result)
        result.addAll(citedNIP19().pubKeys())
        return result
    }

    /**
     * Every `a` this note carries, as address ids in tag order: an addressable it replies to or
     * cites, the community it is posted to, the version it forks ([forkFromAddress]).
     */
    fun referencedAddresses(): List<String> = tags.mapNotNull { ATag.parseAddressId(it)?.takeIf(AddressSerializer::isAddressShape) }

    fun isNewThread() = threadEventIds().isEmpty()

    override fun isAFork() = forkFromVersion() != null || forkFromAddress() != null

    override fun forkFromAddress() = tags.firstNotNullOfOrNull(::parseForkedAddress)

    override fun forkFromVersion() = tags.firstNotNullOfOrNull(MarkedETag::parseForkedEventId)

    companion object {
        const val KIND = 1

        fun build(
            note: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<TextNoteEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, note, createdAt) {
            initializer()
        }

        fun build(
            note: String,
            replyingTo: EventHintBundle<TextNoteEvent>? = null,
            forkingFrom: EventHintBundle<TextNoteEvent>? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<TextNoteEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, note, createdAt) {
            if (replyingTo != null || forkingFrom != null) {
                markedETags(prepareETagsAsReplyTo(replyingTo, forkingFrom))
            }

            initializer()
        }
    }
}
