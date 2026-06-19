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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseReplaceableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.siteAggregateHash
import com.vitorpamplona.quartz.nip5aStaticWebsites.siteDescription
import com.vitorpamplona.quartz.nip5aStaticWebsites.sitePaths
import com.vitorpamplona.quartz.nip5aStaticWebsites.siteServers
import com.vitorpamplona.quartz.nip5aStaticWebsites.siteSource
import com.vitorpamplona.quartz.nip5aStaticWebsites.siteTitle
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * NIP-5D **root napplet** (kind 15129) — the replaceable manifest for a pubkey's
 * default napplet. One per author; newer events replace older ones. Carries the
 * NIP-5A tag set; the `x` aggregate hash is recommended and included by [build].
 */
@Immutable
class RootNappletEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseReplaceableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    NappletManifest,
    SearchableEvent {
    override fun indexableContent() = listOfNotNull(title(), description()).joinToString("\n")

    companion object {
        const val KIND = 15129
        const val ALT_DESCRIPTION = "Napplet"

        fun build(
            paths: List<PathTag>,
            servers: List<String> = emptyList(),
            requires: List<String> = emptyList(),
            title: String? = null,
            description: String? = null,
            source: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<RootNappletEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            alt(ALT_DESCRIPTION)
            sitePaths(paths)
            siteAggregateHash(paths)
            if (servers.isNotEmpty()) siteServers(servers)
            if (requires.isNotEmpty()) nappletRequires(requires)
            title?.let { siteTitle(it) }
            description?.let { siteDescription(it) }
            source?.let { siteSource(it) }
            initializer()
        }
    }
}
