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
package com.vitorpamplona.quartz.nip61Nutzaps.nutzap

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.graph.Link
import com.vitorpamplona.quartz.graph.LinkProvider
import com.vitorpamplona.quartz.graph.Relation
import com.vitorpamplona.quartz.graph.each
import com.vitorpamplona.quartz.graph.links
import com.vitorpamplona.quartz.graph.props.ZapProps
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.kinds.KindTag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class NutzapEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider,
    PubKeyHintProvider,
    SearchableEvent,
    LinkProvider {
    // content is the optional nutzap message.
    override fun indexableContent() = content

    // The read path: the same fields indexableContent() joins, handed over without
    // building the joined string a scan would throw away.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        visitor.visit(content)
    }

    override fun eventHints() = tags.mapNotNull(ETag::parseAsHint)

    override fun linkedEventIds() = tags.mapNotNull(ETag::parseId)

    override fun pubKeyHints() = tags.mapNotNull(PTag::parseAsHint)

    override fun linkedPubKeys() = tags.mapNotNull(PTag::parseKey)

    fun proofs() = tags.proofs()

    fun mintUrl() = tags.mintUrl()

    fun unit() = tags.unit()

    /**
     * NIP-61: `e` is the nutzapped event and `p` the recipient; the sender is the author. The amount
     * is what the proofs claim (it is only checked against the mint at redeem time), and only a
     * sat-denominated nutzap can say it in msats.
     */
    override fun links(): List<Link<*>> =
        links {
            val props = ZapProps(claimedMsats())
            each(tags, ETag::parse) { event(Relation.ZAPPED, it, ETag.TAG_NAME, props) }
            each(tags, PTag::parse) { user(Relation.ZAP_RECIPIENT, it, PTag.TAG_NAME, props) }
            each(tags, KindTag::parse) { tag(Relation.TAG, KindTag.TAG_NAME, it.toString()) }
        }

    companion object {
        const val KIND = 9321

        fun build(
            message: String,
            proofs: List<String>,
            mintUrl: String,
            unit: String,
            zappedEvent: EventHintBundle<out Event>,
            recipientPubKey: HexKey,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<NutzapEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, message, createdAt) {
            proofs(proofs)
            mintUrl(mintUrl)
            unit(unit)
            zappedEvent(zappedEvent)
            zappedEventKind(zappedEvent.event.kind)
            recipient(recipientPubKey)
            initializer()
        }

        /**
         * Nutzap aimed at a profile rather than a specific event: carries only
         * the `p` tag (no `e`/`k` tags), mirroring NIP-57's profile zaps.
         */
        fun buildToUser(
            message: String,
            proofs: List<String>,
            mintUrl: String,
            unit: String,
            recipientPubKey: HexKey,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<NutzapEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, message, createdAt) {
            proofs(proofs)
            mintUrl(mintUrl)
            unit(unit)
            recipient(recipientPubKey)
            initializer()
        }
    }
}
