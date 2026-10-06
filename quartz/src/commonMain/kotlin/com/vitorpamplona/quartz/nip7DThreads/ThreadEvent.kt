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
package com.vitorpamplona.quartz.nip7DThreads

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.firstTagValue
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip10Notes.content.findNostrUris
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
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class ThreadEvent(
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
    override fun indexableContent() = listOfNotNull(title(), content).joinToString("\n")

    // The read path: the same fields indexableContent() joins, handed over without
    // building the joined string a scan would throw away.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(title())) return
        visitor.visit(content)
    }

    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    private var citedNIP19Cache: List<Entity>? = null

    /** NIP-19 entities cited as `nostr:` URIs in the thread body, parsed once. */
    fun citedNIP19(): List<Entity> = citedNIP19Cache ?: findNostrUris(content).also { citedNIP19Cache = it }

    // Note-like: NIP-27 mentions in the body, plus the `p` / `q` tags clients add for them.
    override fun pubKeyHints(): List<PubKeyHint> = tags.mapNotNull(PTag::parseAsHint) + citedNIP19().pubKeyHints()

    override fun linkedPubKeys(): List<HexKey> = tags.mapNotNull(PTag::parseKey) + citedNIP19().pubKeys()

    override fun eventHints(): List<EventIdHint> = tags.mapNotNull(QTag::parseEventAsHint) + citedNIP19().eventHints()

    override fun linkedEventIds(): List<HexKey> = tags.mapNotNull(QTag::parseEventId) + citedNIP19().eventIds()

    override fun addressHints(): List<AddressHint> = tags.mapNotNull(QTag::parseAddressAsHint) + citedNIP19().addressHints()

    override fun linkedAddressIds(): List<String> = tags.mapNotNull(QTag::parseValidAddress) + citedNIP19().addressIds()

    /**
     * The thread title. NIP-7D specifies a `title` tag (what we emit), but some
     * NIP-29 clients (e.g. nostrord) put it in a `subject` tag instead, so fall
     * back to that when reading so their threads still show a title.
     */
    fun title() = tags.title() ?: tags.firstTagValue("subject")

    companion object {
        const val KIND = 11

        fun build(
            content: String,
            title: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ThreadEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, content, createdAt) {
            title(title)
            initializer()
        }
    }
}
