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
package com.vitorpamplona.quartz.nip88Polls.poll

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip10Notes.content.findNostrUris
import com.vitorpamplona.quartz.nip18Reposts.quotes.QAddressableTag
import com.vitorpamplona.quartz.nip18Reposts.quotes.QEventTag
import com.vitorpamplona.quartz.nip18Reposts.quotes.QTag
import com.vitorpamplona.quartz.nip19Bech32.addressHints
import com.vitorpamplona.quartz.nip19Bech32.addressIds
import com.vitorpamplona.quartz.nip19Bech32.entities.Entity
import com.vitorpamplona.quartz.nip19Bech32.eventHints
import com.vitorpamplona.quartz.nip19Bech32.eventIds
import com.vitorpamplona.quartz.nip19Bech32.pubKeyHints
import com.vitorpamplona.quartz.nip19Bech32.pubKeys
import com.vitorpamplona.quartz.nip22Comments.RootScope
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip88Polls.poll.tags.OptionTag
import com.vitorpamplona.quartz.nip88Polls.poll.tags.PollType
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class PollEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    RootScope,
    PubKeyHintProvider,
    EventHintProvider,
    AddressHintProvider,
    SearchableEvent {
    override fun indexableContent() =
        buildString {
            append(content)
            options().forEach { append('\n').append(it.label) }
        }

    // The read path. `content` is visited even when empty: the joined form appends it
    // unconditionally, so the separator it produces is part of what the store indexed.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(content)) return
        options().forEach { if (!visitor.visit(it.label)) return }
    }

    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    private var citedNIP19Cache: List<Entity>? = null

    /** NIP-19 entities cited as `nostr:` URIs in the poll question, parsed once. */
    fun citedNIP19(): List<Entity> = citedNIP19Cache ?: findNostrUris(content).also { citedNIP19Cache = it }

    // Note-like: NIP-27 mentions in the body, plus the `p` / `q` tags clients add for them.
    override fun pubKeyHints(): List<PubKeyHint> = tags.mapNotNull(PTag::parseAsHint) + citedNIP19().pubKeyHints()

    override fun linkedPubKeys(): List<HexKey> = mentionKeys() + citedNIP19().pubKeys()

    override fun eventHints(): List<EventIdHint> = tags.mapNotNull(QTag::parseEventAsHint) + citedNIP19().eventHints()

    override fun linkedEventIds(): List<HexKey> = quotedEvents().map { it.eventId } + citedNIP19().eventIds()

    override fun addressHints(): List<AddressHint> = tags.mapNotNull(QTag::parseAddressAsHint) + citedNIP19().addressHints()

    override fun linkedAddressIds(): List<String> = quotedAddresses().map { it.address.toValue() } + citedNIP19().addressIds()

    /** Users mentioned with `p` tags (NIP-27 / NIP-08), in tag order. */
    fun mentions(): List<PTag> = tags.mapNotNull(PTag::parse)

    /** The keys of [mentions] (`p`), in tag order. */
    fun mentionKeys(): List<HexKey> = tags.mapNotNull(PTag::parseKey)

    /** Events quoted with `q` tags (NIP-18), in tag order. */
    fun quotedEvents(): List<QEventTag> = tags.mapNotNull(QEventTag::parse)

    /** Addressable events quoted with `q` tags (NIP-18), in tag order. */
    fun quotedAddresses(): List<QAddressableTag> = tags.mapNotNull(QAddressableTag::parse)

    fun options() = tags.options()

    fun relays() = tags.relays()

    fun pollType() = tags.pollType()

    fun endsAt() = tags.endsAt()

    fun hasEnded() = tags.hasEnded()

    companion object {
        const val KIND = 1068

        fun build(
            description: String,
            options: List<OptionTag>,
            endsAt: Long?,
            relays: List<NormalizedRelayUrl>,
            pollType: PollType = PollType.SINGLE_CHOICE,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<PollEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, description, createdAt) {
            poolType(pollType)
            options(options)
            relays(relays)
            endsAt?.let {
                endsAt(it)
            }
            initializer()
        }
    }
}
