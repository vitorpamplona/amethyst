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
package com.vitorpamplona.quartz.nip5aStaticWebsites

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.fastForEach
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.AppTag
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.OriginSiteTag
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * NIP-5A manifest **snapshot** (kind 5128): a regular, immutable event that captures one version
 * of a root ([RootSiteEvent], 15128) or named ([NamedSiteEvent], 35128) nsite. The snapshot's own
 * `created_at` is the version timestamp and its event id is the version's permanent address
 * (`v<snapshotIdB36>.nsite-host.com`).
 *
 * NIP-5A requires the snapshot to copy the source manifest's `path` tags, to carry exactly one
 * aggregate `x` tag equal to the source's, exactly one `a` tag naming the source site, and the
 * source's `A` tag unchanged when it has one. `title`, `description`, `source`, `app` and `server`
 * may be copied. Parsing is lax: every accessor returns null/empty on a missing or malformed tag.
 *
 * Searchable like its root/named siblings: only the human-written `title` and `description`.
 * Paths, hashes, server and source URLs are not natural language and are not indexed.
 *
 * Not to be confused with the NIP-5D napplet snapshot (kind 5129, `NappletSnapshotEvent`).
 */
@Immutable
class SiteSnapshotEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    AddressHintProvider,
    SearchableEvent {
    override fun indexableContent() = listOfNotNull(title(), description()).joinToString("\n")

    // The read path: the same fields indexableContent() joins, handed over without
    // building the joined string a scan would throw away.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(title())) return
        visitor.visit(description())
    }

    override fun addressHints(): List<AddressHint> =
        buildList {
            tags.siteParentHint()?.let { add(it) }
            tags.siteOriginHint()?.let { add(it) }
            addAll(tags.siteAppHints())
        }

    /**
     * `SNAPSHOTTED`: the root/named site this snapshot captures (`a`);
     * `ORIGIN`: the origin nsite of the copy lineage, when the site was a copy (`A`);
     * `APP`: the upstream app descriptors the manifest is part of (`app`).
     * The `path` hashes, the `x` aggregate hash and `server`/`source` URLs are values, not edges.
     */
    override fun linkedAddressIds(): List<String> =
        buildList {
            snapshotOf()?.let { add(it.toValue()) }
            origin()?.let { add(it.toValue()) }
            apps().forEach { add(it.toValue()) }
        }.distinct()

    fun paths() = tags.sitePaths()

    fun servers() = tags.siteServers()

    fun title() = tags.siteTitle()

    fun description() = tags.siteDescription()

    fun source() = tags.siteSource()

    fun icon() = tags.siteIcon()

    /** The bundled blob that best looks like this site's app icon, or null. See [NappletIconPath]. */
    fun iconBlob() = NappletIconPath.choose(paths())

    /** The root (15128) or named (35128) site this snapshot captures, from its `a` tag. */
    fun snapshotOf() = tags.siteParent()

    /** The origin nsite of the copy lineage, from the `A` tag, when the snapshotted site is a copy. */
    fun origin() = tags.siteOrigin()

    fun apps() = tags.siteApps()

    /** The aggregate hash declared in the `x` tag, or null when absent. */
    fun declaredAggregateHash(): HexKey? = tags.siteAggregateHash()

    /** The NIP-5A aggregate hash recomputed from this snapshot's [paths]. */
    fun computeAggregateHash(): HexKey = SiteAggregateHash.compute(paths())

    /**
     * True when the snapshot carries an `x` aggregate hash and it matches the one recomputed from its
     * `path` tags. Unlike the replaceable manifests, a snapshot MUST include the `x` tag, so a
     * missing one fails. Each blob's own sha256 still has to be verified when it is served.
     */
    fun verifyAggregate(): Boolean {
        val declared = declaredAggregateHash() ?: return false
        return SiteAggregateHash.verify(paths(), declared)
    }

    companion object {
        const val KIND = 5128
        const val ALT_DESCRIPTION = "Website snapshot"

        /**
         * A snapshot built from scratch. [aggregateHash] defaults to the hash computed from [paths];
         * pass the source manifest's declared value when it has one, since NIP-5A requires the two
         * to match exactly.
         */
        fun build(
            snapshotOf: Address,
            paths: List<PathTag>,
            snapshotOfRelay: NormalizedRelayUrl? = null,
            origin: Address? = null,
            aggregateHash: HexKey = SiteAggregateHash.compute(paths),
            servers: List<String> = emptyList(),
            title: String? = null,
            description: String? = null,
            source: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<SiteSnapshotEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            siteParent(snapshotOf, snapshotOfRelay)
            origin?.let { siteOrigin(it) }
            sitePaths(paths)
            siteAggregateHash(aggregateHash)
            if (servers.isNotEmpty()) siteServers(servers)
            title?.let { siteTitle(it) }
            description?.let { siteDescription(it) }
            source?.let { siteSource(it) }
            initializer()
        }

        /** Snapshots the current version of a root site. See [snapshotOf]. */
        fun snapshotOf(
            site: RootSiteEvent,
            relay: NormalizedRelayUrl? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<SiteSnapshotEvent>.() -> Unit = {},
        ) = snapshotOf(site.address(), site.tags, relay, createdAt, initializer)

        /** Snapshots the current version of a named site. See [snapshotOf]. */
        fun snapshotOf(
            site: NamedSiteEvent,
            relay: NormalizedRelayUrl? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<SiteSnapshotEvent>.() -> Unit = {},
        ) = snapshotOf(site.address(), site.tags, relay, createdAt, initializer)

        /**
         * Copies what NIP-5A says a snapshot of [siteTags] must carry: every `path` tag, an `x` equal to
         * the source's (computed from the paths only when the source declared none), an `a` naming
         * [site] (with [relay] as its hint) and the source's `A` tag unchanged. The optional `title`,
         * `description`, `source`, `icon`, `server` and `app` tags are copied as well.
         */
        private fun snapshotOf(
            site: Address,
            siteTags: TagArray,
            relay: NormalizedRelayUrl?,
            createdAt: Long,
            initializer: TagArrayBuilder<SiteSnapshotEvent>.() -> Unit,
        ) = eventTemplate(KIND, "", createdAt) {
            val paths = siteTags.sitePaths()
            siteParent(site, relay)
            siteTags.fastForEach { tag ->
                if (OriginSiteTag.isTag(tag)) addUnique(tag)
            }
            sitePaths(paths)
            siteAggregateHash(siteTags.siteAggregateHash() ?: SiteAggregateHash.compute(paths))
            siteServers(siteTags.siteServers())
            siteTags.fastForEach { tag ->
                if (AppTag.isTag(tag)) add(tag)
            }
            siteTags.siteTitle()?.let { siteTitle(it) }
            siteTags.siteDescription()?.let { siteDescription(it) }
            siteTags.siteSource()?.let { siteSource(it) }
            siteTags.siteIcon()?.let { siteIcon(it) }
            initializer()
        }
    }
}
