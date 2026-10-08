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
package com.vitorpamplona.quartz.nip34Git.coverNote

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
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
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip10Notes.BaseNoteEvent
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag
import com.vitorpamplona.quartz.nip18Reposts.quotes.QAddressableTag
import com.vitorpamplona.quartz.nip18Reposts.quotes.QTag
import com.vitorpamplona.quartz.nip19Bech32.addressHints
import com.vitorpamplona.quartz.nip19Bech32.addressIds
import com.vitorpamplona.quartz.nip19Bech32.eventHints
import com.vitorpamplona.quartz.nip19Bech32.eventIds
import com.vitorpamplona.quartz.nip19Bech32.pubKeyHints
import com.vitorpamplona.quartz.nip19Bech32.pubKeys
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Kind 1624 — Cover Note (gitworkshop / ngit, "Shared Issue / Patch / PR Metadata").
 *
 * A pinned, supersedable markdown note that renders above a NIP-34 issue (1621), patch (1617) or
 * pull request (1618). The root item is referenced with a **lowercase** NIP-10 `e` tag carrying
 * the `root` marker, so cover notes thread alongside legacy NIP-34 replies and are fetched with
 * `{"kinds":[1624],"#e":[<item id>]}`; `p` is the root item's author and `k` its kind.
 *
 * Resolution is a reader's job, not this class's: only notes by the root author or a confirmed
 * repository maintainer count, and the latest by `created_at` (ties broken by event id,
 * descending) is the one displayed. The class only types the tags.
 *
 * Tolerance: the notes seen on relays (2026-10) carry the root `e` *without* a marker and omit
 * `k`, so [rootEventId] falls back to the first well-formed `e` when no `root`-marked one exists.
 *
 * Searchable: the body is human-written markdown — status banners, summaries, "blocked on X" —
 * so `content` is indexed as is, like a NIP-34 reply. No [com.vitorpamplona.quartz.nip22Comments.RootScope]:
 * a cover note annotates its root item, and NIP-22 discussion belongs on that item rather than
 * on one of its (replaceable-in-spirit) cover notes.
 */
@Immutable
class GitCoverNoteEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseNoteEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider,
    PubKeyHintProvider,
    AddressHintProvider,
    SearchableEvent {
    override fun indexableContent() = content

    override fun eventHints(): List<EventIdHint> = tags.mapNotNull(MarkedETag::parseAsHint) + tags.mapNotNull(QTag::parseEventAsHint) + citedNIP19().eventHints()

    /**
     * `ROOT`: the issue, patch or PR this note covers (the `e` tag);
     * `QUOTE`: events quoted with NIP-18 `q`;
     * `MENTION`: `nostr:` event URIs in the markdown body (`via: content`).
     */
    override fun linkedEventIds(): List<HexKey> = tags.mapNotNull(MarkedETag::parseId) + tags.mapNotNull(QTag::parseEventId) + citedNIP19().eventIds()

    override fun pubKeyHints(): List<PubKeyHint> = tags.mapNotNull(PTag::parseAsHint) + citedNIP19().pubKeyHints()

    /**
     * `ROOT_AUTHOR`: the covered item's author (the `p` tag);
     * `MENTION`: `nostr:` profile URIs in the markdown body (`via: content`).
     */
    override fun linkedPubKeys(): List<HexKey> = tags.mapNotNull(PTag::parseKey) + citedNIP19().pubKeys()

    override fun addressHints(): List<AddressHint> = tags.mapNotNull { tag -> QAddressableTag.parse(tag)?.let { q -> q.relay?.let { AddressHint(q.address.toValue(), it) } } } + citedNIP19().addressHints()

    /**
     * `QUOTE`: addresses quoted with NIP-18 `q` (validated `kind:pubkey:d`);
     * `MENTION`: `nostr:naddr` URIs in the markdown body (`via: content`).
     */
    override fun linkedAddressIds(): List<String> = tags.mapNotNull { QAddressableTag.parse(it)?.address?.toValue() } + citedNIP19().addressIds()

    /** The covered issue, patch or PR: the `root`-marked `e`, else the first well-formed `e`. */
    fun rootEvent() = tags.coverNoteRoot()

    fun rootEventId() = rootEvent()?.eventId

    /** The covered item's author (`p`). */
    fun rootAuthor() = tags.coverNoteRootAuthor()

    /** The covered item's kind (`k`): 1621, 1617 or 1618 when present. */
    fun rootKind() = tags.coverNoteRootKind()

    fun quotes() = tags.coverNoteQuotes()

    companion object {
        const val KIND = 1624

        /**
         * Builds a cover note for [root], tagging it as the NIP-10 `root` with its relay hint and
         * author, notifying that author with `p` and recording its kind with `k`.
         *
         * No `alt` tag is added: Quartz no longer writes boilerplate NIP-31 descriptions. A caller
         * that wants one (or `imeta` tags for embedded uploads) adds it in [initializer].
         */
        fun build(
            markdown: String,
            root: EventHintBundle<out Event>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<GitCoverNoteEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, markdown, createdAt) {
            coverNoteRoot(root.event.id, root.relay, root.event.pubKey)
            coverNoteRootAuthor(root.event.pubKey, root.authorHomeRelay)
            coverNoteRootKind(root.event.kind)
            initializer()
        }
    }
}
