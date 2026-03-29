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
package com.vitorpamplona.amethyst.service.uploads.blossom.bud10

import androidx.collection.LruCache
import com.vitorpamplona.amethyst.commons.richtext.mimeTypeMap
import com.vitorpamplona.amethyst.model.privacyOptions.IRoleBasedHttpClientBuilder
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.isValid
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServersEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomUri
import com.vitorpamplona.quartz.utils.firstNotNullOrNullAsync
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient

class BlossomServerResolver(
    val loggedInUsers: () -> List<HexKey>,
    val blossomServers: (Set<Address>) -> List<Flow<BlossomServersEvent>>,
    val httpClientBuilder: IRoleBasedHttpClientBuilder,
) {
    val blossomHitCache: ServerHeadCache = ServerHeadCache()
    val uriToUrlCache = LruCache<String, BlossomUriServer>(200)

    class BlossomUriServer(
        val uri: BlossomUri,
        val serverUrl: String,
    )

    fun cachedFindServer(uriStr: String): BlossomUriServer? = uriToUrlCache[uriStr]

    suspend fun findServers(uriStr: String): BlossomUriServer? {
        uriToUrlCache[uriStr]?.let { return it }

        val result =
            withTimeoutOrNull(10000) {
                findServersInner(uriStr)
            }

        if (result != null) {
            uriToUrlCache.put(uriStr, result)
        }

        return result
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun findServersInner(uriStr: String): BlossomUriServer? {
        val uri = BlossomUri.parse(uriStr) ?: return null

        val expectedMimeType = mimeTypeMap[uri.extension]
        val filename = uri.filename()

        if (uri.servers.isNotEmpty()) {
            val workingUrl = firstWorkingUrl(uri.servers, filename, expectedMimeType, uri.size)
            if (workingUrl != null) {
                return BlossomUriServer(uri, workingUrl)
            }
        }

        val blossomServerConfigNeeded = mutableSetOf<Address>()

        uri.authors.forEach {
            if (it.isValid()) {
                blossomServerConfigNeeded.add(BlossomServersEvent.createAddress(it))
            }
        }

        loggedInUsers().forEach {
            blossomServerConfigNeeded.add(BlossomServersEvent.createAddress(it))
        }

        val flows =
            blossomServers(blossomServerConfigNeeded)
                .map { blossomServerFlow ->
                    blossomServerFlow.transformLatest {
                        val servers = it.servers()
                        if (servers.isNotEmpty()) {
                            firstWorkingUrl(servers, filename, expectedMimeType, uri.size)?.let { serverUrl ->
                                emit(serverUrl)
                            }
                        }
                    }
                }.toTypedArray()

        if (flows.isNotEmpty()) {
            val serverResult = merge(*flows).first()
            return BlossomUriServer(uri, serverResult)
        }

        return null
    }

    private suspend fun firstWorkingUrl(
        servers: List<String>,
        filename: String,
        expectedMimeType: String?,
        expectedSize: Long?,
    ): String? =
        firstNotNullOrNullAsync(servers, 10000) {
            blossomHitCache.urlIfServerHasFile(it, filename, expectedMimeType, expectedSize) { url ->
                client(url, expectedMimeType)
            }
        }

    fun client(
        url: String,
        mimeType: String?,
    ): OkHttpClient =
        if (mimeType == null) {
            httpClientBuilder.okHttpClientForPreview(url)
        } else if (mimeType.startsWith("audio/") || mimeType.startsWith("video/")) {
            httpClientBuilder.okHttpClientForVideo(url)
        } else if (mimeType.startsWith("image/")) {
            httpClientBuilder.okHttpClientForImage(url)
        } else {
            httpClientBuilder.okHttpClientForPreview(url)
        }

    fun canResolve(scheme: String) = scheme == SCHEME

    companion object {
        const val SCHEME = "blossom"
    }
}
