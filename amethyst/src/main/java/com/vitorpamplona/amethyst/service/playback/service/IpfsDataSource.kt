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
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.vitorpamplona.amethyst.service.playback.service

import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.vitorpamplona.amethyst.commons.richtext.IpfsGatewayResolver
import java.io.IOException

/**
 * Media3 data source that keeps `ipfs:` as the cache key while fetching bytes from IPFS gateways.
 *
 * A fresh upstream is created for every candidate so an HTTP failure cannot leave a partially
 * opened OkHttp data source attached to the retry.
 */
@OptIn(UnstableApi::class)
class IpfsDataSource(
    private val upstreamFactory: DataSource.Factory,
    private val gateway: () -> String?,
) : DataSource {
    private val transferListeners = mutableListOf<TransferListener>()
    private var upstream: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        transferListeners.add(transferListener)
        upstream?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val original = dataSpec.uri.toString()
        if (!IpfsGatewayResolver.isIpfsUri(original)) {
            return openUpstream(dataSpec)
        }

        val candidates = IpfsGatewayResolver.getAllCandidateUrls(original, gateway())
        if (candidates.isEmpty()) throw IOException("Unable to resolve IPFS URI $original")

        var lastFailure: IOException? = null
        for (candidate in candidates) {
            try {
                return openUpstream(dataSpec.withUri(candidate.toUri()))
            } catch (e: IOException) {
                lastFailure = e
                runCatching { closeUpstream() }
            }
        }
        throw lastFailure ?: IOException("Unable to open IPFS URI $original")
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int = upstream?.read(buffer, offset, length) ?: throw IOException("Data source is not open")

    override fun getUri(): Uri? = upstream?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream?.responseHeaders ?: emptyMap()

    override fun close() {
        closeUpstream()
    }

    private fun openUpstream(dataSpec: DataSpec): Long {
        val source = upstreamFactory.createDataSource()
        transferListeners.forEach(source::addTransferListener)
        upstream = source
        return source.open(dataSpec)
    }

    private fun closeUpstream() {
        val source = upstream
        upstream = null
        source?.close()
    }

    class Factory(
        private val upstreamFactory: DataSource.Factory,
        private val gateway: () -> String?,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = IpfsDataSource(upstreamFactory, gateway)
    }
}
