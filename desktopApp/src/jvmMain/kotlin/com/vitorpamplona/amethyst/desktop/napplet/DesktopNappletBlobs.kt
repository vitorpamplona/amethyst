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
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.BlobFetcher
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.StaticSiteResolution
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.StaticSiteResolver
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Fetches a napplet's blobs from Blossom into a content-addressed cache under [dir].
 *
 * A blob is written only when its bytes hash to the name it is stored under, and quartz's
 * [StaticSiteResolver] hashes whatever this returns again before it is served, so neither a server
 * nor a corrupted file can put anything but the signed artifact in front of the user.
 */
class DesktopNappletBlobs(
    private val dir: File,
    private val httpClient: () -> OkHttpClient,
) {
    suspend fun resolve(launch: DesktopNappletLaunch): StaticSiteResolution = withContext(Dispatchers.IO) { StaticSiteResolver.resolve(INDEX_PATH, launch.paths, launch.servers, fetch) }

    private val fetch: BlobFetcher = { url ->
        val hash = url.substringAfterLast('/').substringBefore('.').lowercase()
        cached(hash) ?: download(url)?.also { put(hash, it) }
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

    private fun download(url: String): ByteArray? =
        runCatching {
            val client = httpClient().newBuilder().callTimeout(45, TimeUnit.SECONDS).build()
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

        /** The same cap as Android's host (NappletBlobHttp.MAX_BLOB_BYTES). */
        const val MAX_BLOB_BYTES = 20L * 1024 * 1024
        private val HEX64 = Regex("[0-9a-f]{64}")
    }
}
