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
package com.vitorpamplona.amethyst.commons.napplet.blobs

import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.BlobFetcher
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.GENERIC_CONTENT_TYPE
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.StaticSiteResolution
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.StaticSiteResolver
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.guessStaticContentType
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.resolvePath
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.sniffContentType
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import okhttp3.OkHttpClient

/**
 * A napplet's or nSite's files, from the shared content-addressed [cache] first and from its Blossom
 * servers through [client] on a miss. Every download is verified by quartz's [StaticSiteResolver]
 * before it is returned, and the cache stores only bytes that hash to their name, so nothing but the
 * signed files can come out of here.
 *
 * One per launch: [client] carries that launch's route (Tor or the open web).
 */
class NappletBlobStore(
    private val cache: NappletBlobCache,
    private val client: OkHttpClient,
) {
    private val fetch: BlobFetcher = { url ->
        val hash = blobHashOf(url)
        cache.get(hash) ?: NappletBlobHttp.download(client, url)?.also { cache.put(hash, it) }
    }

    /**
     * Resolves [requestPath] against [paths]. A cache hit was verified when it was written and is
     * addressed by its hash, so it is served without hashing it again (for a site's large JS bundles
     * that hash is the dominant cost); a miss downloads from [servers] and verifies. Blocking: call it
     * off the main thread.
     */
    suspend fun resolve(
        requestPath: String,
        paths: List<PathTag>,
        servers: List<String>,
    ): StaticSiteResolution = cacheHit(requestPath, paths) ?: StaticSiteResolver.resolve(requestPath, paths, servers, fetch)

    private fun cacheHit(
        requestPath: String,
        paths: List<PathTag>,
    ): StaticSiteResolution.Resolved? {
        val match = paths.resolvePath(requestPath) ?: return null
        val bytes = cache.get(match.hash.lowercase()) ?: return null
        val byExtension = guessStaticContentType(match.path)
        val contentType = if (byExtension == GENERIC_CONTENT_TYPE) sniffContentType(bytes) ?: byExtension else byExtension
        return StaticSiteResolution.Resolved(match.path, match.hash, contentType, bytes, server = CACHE_SERVER)
    }

    /**
     * Releases [client]'s threads and connections and cancels any fetch in flight. Only for a client
     * this store's owner built for it alone; never call it on the app's shared client.
     */
    fun closeClient() {
        runCatching {
            client.dispatcher.cancelAll()
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    companion object {
        /** Marker "server" for a blob served from the local cache. */
        const val CACHE_SERVER = "cache"

        /** The sha256 a Blossom URL names: its last segment, without an extension. */
        fun blobHashOf(url: String): String =
            url
                .substringAfterLast('/')
                .substringBefore('.')
                .lowercase()
    }
}
