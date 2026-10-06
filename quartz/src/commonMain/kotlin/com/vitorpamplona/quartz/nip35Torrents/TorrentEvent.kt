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
package com.vitorpamplona.quartz.nip35Torrents

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
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.references.references
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip10Notes.content.findNostrUris
import com.vitorpamplona.quartz.nip10Notes.content.findURLs
import com.vitorpamplona.quartz.nip18Reposts.quotes.QAddressableTag
import com.vitorpamplona.quartz.nip18Reposts.quotes.QEventTag
import com.vitorpamplona.quartz.nip18Reposts.quotes.QTag
import com.vitorpamplona.quartz.nip18Reposts.quotes.quotes
import com.vitorpamplona.quartz.nip19Bech32.addressHints
import com.vitorpamplona.quartz.nip19Bech32.addressIds
import com.vitorpamplona.quartz.nip19Bech32.entities.Entity
import com.vitorpamplona.quartz.nip19Bech32.eventHints
import com.vitorpamplona.quartz.nip19Bech32.eventIds
import com.vitorpamplona.quartz.nip19Bech32.pubKeyHints
import com.vitorpamplona.quartz.nip19Bech32.pubKeys
import com.vitorpamplona.quartz.nip23LongContent.tags.TitleTag
import com.vitorpamplona.quartz.nip35Torrents.tags.BtihTag
import com.vitorpamplona.quartz.nip35Torrents.tags.FileTag
import com.vitorpamplona.quartz.nip35Torrents.tags.InfoHashTag
import com.vitorpamplona.quartz.nip35Torrents.tags.TrackerTag
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarning
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.UrlEncoder

@Immutable
class TorrentEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    SearchableEvent,
    PubKeyHintProvider,
    EventHintProvider,
    AddressHintProvider {
    // Torrents are searched by file name as much as by title: each file path
    // follows the description on its own line.
    override fun indexableContent() = (listOfNotNull(title(), content) + fileNames()).joinToString("\n")

    // The read path: the same fields indexableContent() joins, handed over without
    // building the joined string a scan would throw away.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(title())) return
        if (!visitor.visit(content)) return
        tags.forEach { tag -> FileTag.parseName(tag)?.let { if (!visitor.visit(it)) return } }
    }

    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    private var citedNIP19Cache: List<Entity>? = null

    /** NIP-19 entities cited as `nostr:` URIs in the description, parsed once. */
    fun citedNIP19(): List<Entity> = citedNIP19Cache ?: findNostrUris(content).also { citedNIP19Cache = it }

    // build() turns the description's NIP-19 citations into `p` (npub/nprofile)
    // and `q` (note/nevent/naddr) tags; the content citations cover other clients.
    override fun pubKeyHints(): List<PubKeyHint> = tags.mapNotNull(PTag::parseAsHint) + citedNIP19().pubKeyHints()

    override fun linkedPubKeys(): List<HexKey> = mentions().map { it.pubKey } + citedNIP19().pubKeys()

    override fun eventHints(): List<EventIdHint> = tags.mapNotNull(QTag::parseEventAsHint) + citedNIP19().eventHints()

    override fun linkedEventIds(): List<HexKey> = quotedEvents().map { it.eventId } + citedNIP19().eventIds()

    override fun addressHints(): List<AddressHint> = tags.mapNotNull(QTag::parseAddressAsHint) + citedNIP19().addressHints()

    override fun linkedAddressIds(): List<String> = quotedAddresses().map { it.address.toValue() } + citedNIP19().addressIds()

    /** The users the description mentions (`p`), in tag order. */
    fun mentions(): List<PTag> = tags.mapNotNull(PTag::parse)

    /** NIP-18 quotes (`q`) of regular events the description cites, in tag order. */
    fun quotedEvents(): List<QEventTag> = tags.mapNotNull(QEventTag::parse)

    /** NIP-18 quotes (`q`) of addressable events the description cites, in tag order. */
    fun quotedAddresses(): List<QAddressableTag> = tags.mapNotNull(QAddressableTag::parse)

    fun title() = tags.firstNotNullOfOrNull(TitleTag::parse)

    fun btih() = tags.firstNotNullOfOrNull(BtihTag::parse)

    fun x() = tags.firstNotNullOfOrNull(InfoHashTag::parse)

    fun trackers() = tags.mapNotNull(TrackerTag::parse)

    fun files() = tags.mapNotNull(FileTag::parse)

    /** The file paths of the `file` tags, without parsing their sizes. */
    fun fileNames() = tags.mapNotNull(FileTag::parseName)

    fun toMagnetLink(): String? {
        val btih = btih()?.ifBlank { null } ?: return null
        val title = title()?.ifBlank { null }

        return buildString {
            append("magnet:?xt=urn:btih:")
            append(btih)
            append("&")
            if (title != null) {
                append("dn=")
                append(UrlEncoder.encode(title))
                append("&")
            }
            trackers().forEachIndexed { idx, trackerUrl ->
                if (idx > 0) {
                    append("&")
                }
                append("tr=")
                append(UrlEncoder.encode(trackerUrl))
            }
        }
    }

    fun totalSizeBytes(): Long = tags.sumOf { FileTag.parseBytes(it) ?: 0L }

    companion object {
        const val KIND = 2003
        const val ALT_DESCRIPTION = "A torrent file"

        fun build(
            description: String?,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<TorrentEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, description ?: "", createdAt) {
            initializer()
        }

        fun build(
            title: String,
            btih: String,
            files: List<FileTag>,
            description: String? = null,
            x: String? = null,
            trackers: List<String>? = null,
            contentWarningReason: String? = null,
            createdAt: Long = TimeUtils.now(),
        ) = eventTemplate(KIND, description ?: "", createdAt) {
            title(title)
            btih(btih)
            files(files)
            trackers?.let { trackers(it) }
            x?.let { infohash(it) }
            contentWarningReason?.let { contentWarning(it) }

            description?.let {
                hashtags(findHashtags(it))
                references(findURLs(it))
                quotes(findNostrUris(it))
            }
        }
    }
}
