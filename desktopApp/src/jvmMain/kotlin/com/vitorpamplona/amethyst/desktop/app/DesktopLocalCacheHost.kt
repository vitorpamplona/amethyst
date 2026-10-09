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
package com.vitorpamplona.amethyst.desktop.app

import com.vitorpamplona.amethyst.commons.model.cache.FileSystemNip95BlobStore
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.LocalCacheHost
import com.vitorpamplona.amethyst.commons.model.cache.Nip95BlobStore
import com.vitorpamplona.amethyst.commons.relays.nip11RelayInfo.Nip11CachedRetriever
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.RelayStats
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import com.vitorpamplona.quartz.nip57Zaps.validate.LnurlEndpointCache
import com.vitorpamplona.quartz.nip57Zaps.validate.LnurlEndpointInfo
import kotlinx.coroutines.CoroutineScope
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * Binds the shared [LocalCache] to the desktop graph. Members read through [modules] on each call:
 * several of them are lazy and are built after the cache singleton exists.
 */
class DesktopLocalCacheHost(
    private val modules: DesktopAppModules,
    override val isDebug: Boolean,
) : LocalCacheHost {
    override val scope: CoroutineScope get() = modules.applicationIOScope

    // Lazy, not a getter: creating the directory is the point of touching it.
    override val nip95Blobs: Nip95BlobStore by lazy {
        val dir = File(modules.filesDir, "nip95").apply { mkdirs() }
        FileSystemNip95BlobStore(dir.toOkioPath())
    }

    override val relayStats: RelayStats get() = modules.relayStats

    override fun relaySelfPubKey(relay: NormalizedRelayUrl): HexKey? = modules.nip11Cache.getFromCache(relay).self

    override fun relayInfo(relay: NormalizedRelayUrl): Nip11RelayInformation = modules.nip11Cache.getFromCache(relay)

    override val nip11Cache: Nip11CachedRetriever get() = modules.nip11Cache

    override fun lnurlEndpoint(lnurlpUrl: String): LnurlEndpointInfo? = LnurlEndpointCache.get(lnurlpUrl)
}
