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
package com.vitorpamplona.quartz.nip5dNapplets

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip5aStaticWebsites.SiteAggregateHash
import com.vitorpamplona.quartz.nip5aStaticWebsites.siteAggregateHash
import com.vitorpamplona.quartz.nip5aStaticWebsites.siteDescription
import com.vitorpamplona.quartz.nip5aStaticWebsites.sitePaths
import com.vitorpamplona.quartz.nip5aStaticWebsites.siteServers
import com.vitorpamplona.quartz.nip5aStaticWebsites.siteSource
import com.vitorpamplona.quartz.nip5aStaticWebsites.siteTitle
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag

/**
 * Common surface for the three NIP-5D napplet manifest kinds — snapshot
 * ([NappletSnapshotEvent] / 5129), root ([RootNappletEvent] / 15129), and named
 * ([NamedNappletEvent] / 35129). They carry the same NIP-5A tag set and differ
 * only in replaceability and the `d` identifier, so all accessors live here and
 * read off the event's [tags].
 */
interface NappletManifest {
    val tags: TagArray

    /** `path` tags mapping each absolute request path to its blob's sha256. */
    fun paths(): List<PathTag> = tags.sitePaths()

    /** `server` tags hinting which Blossom servers hold the blobs. */
    fun servers(): List<String> = tags.siteServers()

    /** `requires` tags: bare NAP capability domains the napplet needs from the shell. */
    fun requires(): List<String> = tags.nappletRequires()

    /** The aggregate hash declared in the `x` tag, or null when absent. */
    fun declaredAggregateHash(): HexKey? = tags.siteAggregateHash()

    fun title(): String? = tags.siteTitle()

    fun description(): String? = tags.siteDescription()

    fun source(): String? = tags.siteSource()

    /** The NIP-5A aggregate hash recomputed from this manifest's [paths]. */
    fun computeAggregateHash(): HexKey = SiteAggregateHash.compute(paths())

    /**
     * Verifies the declared `x` aggregate hash against the one recomputed from the
     * `path` tags. Returns `true` when no `x` tag is present — per NIP-5D it is only
     * enforced when carried. A runtime MUST still verify each blob's own sha256
     * separately (see `StaticSiteResolver`).
     */
    fun verifyAggregate(): Boolean = SiteAggregateHash.verify(paths(), declaredAggregateHash())
}
