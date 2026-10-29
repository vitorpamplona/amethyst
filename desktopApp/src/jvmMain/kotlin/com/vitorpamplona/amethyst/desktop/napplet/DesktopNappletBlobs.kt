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
package com.vitorpamplona.amethyst.desktop.napplet

import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.GENERIC_CONTENT_TYPE
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.StaticSiteResolution
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.StaticSiteResolver
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.guessStaticContentType
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.resolvePath
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.sniffContentType
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Fetches napplet and nSite blobs from Blossom into a content-addressed cache under [dir].
 *
 * A blob is written only when its bytes hash to the name it is stored under, and quartz's
 * [StaticSiteResolver] hashes every download again before it is served, so neither a server nor a
 * corrupted file can put anything but the signed files in front of the user. [httpClient] takes
 * whether the fetch goes through Tor.
 */
class DesktopNappletBlobs(
    private val dir: File,
    private val httpClient: (useTor: Boolean) -> OkHttpClient,
) {
    /**
     * Resolves [requestPath] against [launch]'s files. A cache hit was verified when it was written
     * and is addressed by its hash, so it is served without hashing it again (for a site's large JS
     * bundles, that hash is the dominant cost); a miss downloads and verifies.
     */
    suspend fun resolve(
        launch: DesktopNappletLaunch,
        requestPath: String = INDEX_PATH,
        useTor: Boolean,
    ): StaticSiteResolution =
        withContext(Dispatchers.IO) {
            cacheHit(launch, requestPath) ?: StaticSiteResolver.resolve(requestPath, launch.paths, launch.servers) { url ->
                val hash = url.substringAfterLast('/').substringBefore('.').lowercase()
                cached(hash) ?: download(url, useTor)?.also { put(hash, it) }
            }
        }

    private fun cacheHit(
        launch: DesktopNappletLaunch,
        requestPath: String,
    ): StaticSiteResolution.Resolved? {
        val match = launch.paths.resolvePath(requestPath) ?: return null
        val bytes = cached(match.hash.lowercase()) ?: return null
        val byExtension = guessStaticContentType(match.path)
        val contentType = if (byExtension == GENERIC_CONTENT_TYPE) sniffContentType(bytes) ?: byExtension else byExtension
        return StaticSiteResolution.Resolved(match.path, match.hash, contentType, bytes, server = CACHE_SERVER)
    }

    private fun fileFor(hash: String): File? = if (HEX64.matches(hash)) File(dir, hash) else null

    private fun cached(hash: String): ByteArray? = fileFor(hash)?.takeIf { it.isFile }?.let { runCatching { it.readBytes() }.getOrNull() }

    private fun put(
        hash: String,
        bytes: ByteArray,
    ) {
        val file = fileFor(hash) ?: return
        if (sha256(bytes).toHexKey() != hash) return
        runCatching {
            if (!dir.isDirectory && !dir.mkdirs()) throw IllegalStateException("Cannot create $dir")
            val tmp = File(dir, "$hash.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(file)) {
                if (!tmp.delete()) Log.w(TAG) { "Could not delete $tmp" }
            }
        }.onFailure { Log.w(TAG, "Could not cache blob $hash", it) }
    }

    private fun download(
        url: String,
        useTor: Boolean,
    ): ByteArray? =
        runCatching {
            val client = httpClient(useTor).newBuilder().callTimeout(45, TimeUnit.SECONDS).build()
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                val declared = response.body.contentLength()
                if (!response.isSuccessful || declared > MAX_BLOB_BYTES) return@use null
                val bytes = response.body.byteStream().use { it.readNBytes(MAX_BLOB_BYTES.toInt() + 1) }
                bytes.takeIf { it.size <= MAX_BLOB_BYTES }
            }
        }.onFailure { Log.d(TAG) { "Blob fetch failed: $url (${it.message})" } }.getOrNull()

    companion object {
        private const val TAG = "DesktopNappletBlobs"
        const val INDEX_PATH = "/index.html"

        // Marker "server" for a blob served from the local cache.
        private const val CACHE_SERVER = "cache"

        /** The same cap as Android's host (NappletBlobHttp.MAX_BLOB_BYTES). */
        const val MAX_BLOB_BYTES = 20L * 1024 * 1024
        private val HEX64 = Regex("[0-9a-f]{64}")
    }
}
