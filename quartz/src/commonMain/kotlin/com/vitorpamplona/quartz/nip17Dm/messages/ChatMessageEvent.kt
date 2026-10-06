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
package com.vitorpamplona.quartz.nip17Dm.messages

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.toPTag
import com.vitorpamplona.quartz.nip14Subject.subject
import com.vitorpamplona.quartz.nip17Dm.base.BaseDMGroupEvent
import com.vitorpamplona.quartz.nip18Reposts.quotes.QTag
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip57Zaps.splits.hasZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplitHintsTo
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplitPubKeysTo
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class ChatMessageEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseDMGroupEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider,
    AddressHintProvider,
    SearchableEvent {
    // content is the decrypted (plaintext) direct-message body; the optional
    // NIP-14 subject (`changeSubject()`) names the conversation.
    override fun indexableContent() = listOfNotNull(subject(), content).joinToString("\n")

    // The read path: the same fields indexableContent() joins, handed over without
    // building the joined string a scan would throw away.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(subject())) return
        visitor.visit(content)
    }

    // Runs on every relay copy and a `zap` tag is almost never present: hand back super's list
    // as is unless there is one, instead of an empty zap list plus a concatenation per call.
    override fun pubKeyHints(): List<PubKeyHint> {
        val base = super.pubKeyHints()
        return if (tags.hasZapSplitSetup()) tags.zapSplitHintsTo(base.toMutableList()) else base
    }

    override fun linkedPubKeys(): List<HexKey> {
        val base = super.linkedPubKeys()
        return if (tags.hasZapSplitSetup()) tags.zapSplitPubKeysTo(base.toMutableList()) else base
    }

    override fun eventHints() = tags.mapNotNull(ETag::parseAsHint) + tags.mapNotNull(QTag::parseEventAsHint)

    override fun linkedEventIds() = tags.mapNotNull(ETag::parseId) + tags.mapNotNull(QTag::parseEventId)

    override fun addressHints() = tags.mapNotNull(ATag::parseAsHint) + tags.mapNotNull(QTag::parseAddressAsHint)

    override fun linkedAddressIds() = tags.mapNotNull(ATag::parseValidAddress) + tags.mapNotNull(QTag::parseValidAddress)

    fun replyTo() = tags.mapNotNull(ETag::parseId)

    companion object {
        const val KIND = 14

        fun build(
            msg: String,
            to: List<PTag>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ChatMessageEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, msg, createdAt) {
            group(to)
            initializer()
        }

        fun reply(
            msg: String,
            reply: EventHintBundle<BaseDMGroupEvent>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ChatMessageEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, msg, createdAt) {
            reply(reply)
            group((reply.event.recipients() + reply.toPTag()).distinctBy { it.pubKey })
            initializer()
        }
    }
}
