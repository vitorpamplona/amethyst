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
package com.vitorpamplona.quartz.nip51Lists.releaseArtifactSet

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.SoftwareAssetEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.appId
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.assets
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.channel
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.isNip82SoftwareRelease
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.tags.AppIdTag
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.version
import com.vitorpamplona.quartz.graph.Link
import com.vitorpamplona.quartz.graph.LinkProvider
import com.vitorpamplona.quartz.graph.Relation
import com.vitorpamplona.quartz.graph.each
import com.vitorpamplona.quartz.graph.links
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.AddressBookmark
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.BookmarkIdTag
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.EventBookmark
import com.vitorpamplona.quartz.nip51Lists.remove
import com.vitorpamplona.quartz.nip51Lists.tags.DescriptionTag
import com.vitorpamplona.quartz.nip51Lists.tags.TitleTag
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Kind 30063: the artifacts of one software release.
 *
 * Two specs publish this kind and both are parsed here:
 * - NIP-51 "release artifact set": a `title`, a `description` and `e`/`a` items.
 * - NIP-82 "software release": `d = <app-id>@<version>`, `i` (app id), `version`,
 *   `c` (channel), `e` tags to its kind 3063 assets, and the release notes as content.
 *   Use `isNip82SoftwareRelease()` to tell them apart.
 */
@Immutable
class ReleaseArtifactSetEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider,
    AddressHintProvider,
    SearchableEvent,
    LinkProvider {
    override fun indexableContent() = listOfNotNull(title(), description(), searchableReleaseNotes()).joinToString("\n")

    // The read path: the same fields indexableContent() joins, handed over without
    // building the joined string a scan would throw away.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(title())) return
        if (!visitor.visit(description())) return
        visitor.visit(searchableReleaseNotes())
    }

    /**
     * The release notes when they are safe to index, else null. Only NIP-82 releases carry
     * release notes in `content`; a NIP-51 set may keep encrypted private items there, which
     * must never reach the index. The blank check runs first so empty sets skip the two tag
     * scans on every search. `SearchFieldExtractor` reads this too, so both search paths
     * apply the same guard.
     */
    fun searchableReleaseNotes() = if (content.isNotBlank() && isNip82SoftwareRelease()) content else null

    override fun eventHints() = tags.mapNotNull(EventBookmark::parseAsHint)

    override fun linkedEventIds() = tags.mapNotNull(EventBookmark::parseId)

    override fun addressHints() = tags.mapNotNull(AddressBookmark::parseAsHint)

    override fun linkedAddressIds() = tags.mapNotNull(AddressBookmark::parseAddressId)

    fun title() = tags.firstNotNullOfOrNull(TitleTag::parse)

    fun description() = tags.firstNotNullOfOrNull(DescriptionTag::parse)

    fun items(): List<BookmarkIdTag> = tags.mapNotNull(BookmarkIdTag::parse)

    /** NIP-82: the application identifier (`i` tag). */
    fun appId() = tags.appId()

    /** NIP-82: the release version. */
    fun version() = tags.version()

    /** NIP-82: the release channel (`c` tag). */
    fun channel() = tags.channel()

    /** NIP-82: the kind 3063 assets of this release. */
    fun assets() = tags.assets()

    /** NIP-82: the release notes. */
    fun releaseNotes() = content

    /**
     * The release's artifacts (`e`: NIP-51 file metadata, NIP-82 kind 3063 assets) are
     * `CURATED`; the `a` is the software application (`APP`) they release and the NIP-82 `i` its
     * app id.
     */
    override fun links(): List<Link<*>> =
        links {
            each(tags, AppIdTag::parse) { tag(Relation.TAG, AppIdTag.TAG_NAME, it) }
            each(tags, EventBookmark::parse) { event(Relation.CURATED, it, EventBookmark.TAG_NAME) }
            each(tags, AddressBookmark::parse) { address(Relation.APP, it, AddressBookmark.TAG_NAME) }
        }

    companion object {
        const val KIND = 30063

        /** NIP-82 requires `d = <app-id>@<version>`. */
        fun buildSoftwareReleaseDTag(
            appId: String,
            version: String,
        ) = "$appId@$version"

        /** Builds a NIP-82 software release. */
        fun buildSoftwareRelease(
            appId: String,
            version: String,
            channel: String,
            assets: List<EventHintBundle<SoftwareAssetEvent>>,
            releaseNotes: String = "",
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ReleaseArtifactSetEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, releaseNotes, createdAt) {
            dTag(buildSoftwareReleaseDTag(appId, version))
            appId(appId)
            version(version)
            channel(channel)
            assets(assets)
            initializer()
        }

        fun createAddress(
            pubKey: HexKey,
            dTag: String,
        ) = Address(KIND, pubKey, dTag)

        suspend fun add(
            earlierVersion: ReleaseArtifactSetEvent,
            item: BookmarkIdTag,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): ReleaseArtifactSetEvent =
            resign(
                content = earlierVersion.content,
                tags = earlierVersion.tags.plus(item.toTagArray()),
                signer = signer,
                createdAt = createdAt,
            )

        suspend fun remove(
            earlierVersion: ReleaseArtifactSetEvent,
            item: BookmarkIdTag,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): ReleaseArtifactSetEvent =
            resign(
                content = earlierVersion.content,
                tags = earlierVersion.tags.remove(item.toTagIdOnly()),
                signer = signer,
                createdAt = createdAt,
            )

        suspend fun resign(
            content: String,
            tags: TagArray,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): ReleaseArtifactSetEvent {
            val newTags = tags

            return signer.sign(createdAt, KIND, newTags, content)
        }

        @OptIn(ExperimentalUuidApi::class)
        suspend fun create(
            title: String = "",
            description: String? = null,
            items: List<BookmarkIdTag> = emptyList(),
            dTag: String = Uuid.random().toString(),
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): ReleaseArtifactSetEvent {
            val template =
                build(title, items, dTag, createdAt) {
                    if (description != null) description(description)
                }
            return signer.sign(template)
        }

        @OptIn(ExperimentalUuidApi::class)
        fun build(
            title: String = "",
            items: List<BookmarkIdTag> = emptyList(),
            dTag: String = Uuid.random().toString(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ReleaseArtifactSetEvent>.() -> Unit = {},
        ) = eventTemplate(
            kind = KIND,
            description = "",
            createdAt = createdAt,
        ) {
            dTag(dTag)
            title(title)
            artifacts(items)

            initializer()
        }
    }
}
