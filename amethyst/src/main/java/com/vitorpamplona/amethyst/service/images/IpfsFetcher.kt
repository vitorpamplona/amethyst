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
package com.vitorpamplona.amethyst.service.images

import coil3.ImageLoader
import coil3.Uri
import coil3.annotation.ExperimentalCoilApi
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.network.CacheStrategy
import coil3.network.ConcurrentRequestStrategy
import coil3.network.ConnectivityChecker
import coil3.network.NetworkFetcher
import coil3.network.okhttp.asNetworkClient
import coil3.request.Options
import com.vitorpamplona.amethyst.commons.richtext.IpfsGatewayResolver
import okhttp3.Call
import kotlin.coroutines.cancellation.CancellationException

/**
 * Resolves an `ipfs:` Coil model at fetch time through the first configured
 * Originless gateway. Failover across the rest of the node list is
 * [com.vitorpamplona.amethyst.commons.originless.OriginlessGatewayFailoverInterceptor]
 * — the same path Coil HTTP images and ExoPlayer use.
 *
 * Keeping the content-addressed URI as Coil's model gives every gateway the
 * same memory-cache identity.
 */
class IpfsFetcher(
    private val data: Uri,
    private val networkFetcher: (url: String) -> Fetcher,
) : Fetcher {
    override suspend fun fetch(): FetchResult? =
        try {
            networkFetcher(IpfsGatewayResolver.toHttpUrl(data.toString())).fetch()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }

    @OptIn(ExperimentalCoilApi::class)
    class Factory(
        private val networkClient: (url: String) -> Call.Factory,
        // Shared with every other network-backed factory on this ImageLoader --
        // see the note in ImageLoaderSetup.setup(): the de-dupe only works when
        // all fetchers coordinate through the same instance.
        concurrentRequestStrategy: ConcurrentRequestStrategy,
    ) : Fetcher.Factory<Uri> {
        private val cacheStrategyLazy = lazy { CacheStrategy.DEFAULT }
        private val connectivityCheckerLazy = singleParameterLazy(::ConnectivityChecker)
        private val concurrentRequestStrategyLazy = lazyOf(concurrentRequestStrategy)

        override fun create(
            data: Uri,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher? {
            val ipfsUri = data.toString()
            if (!IpfsGatewayResolver.isIpfsUri(ipfsUri)) return null

            return IpfsFetcher(data) { url ->
                NetworkFetcher(
                    url = url,
                    options = options,
                    networkClient = lazy { networkClient(url).asNetworkClient() },
                    diskCache = lazy { imageLoader.diskCache },
                    cacheStrategy = cacheStrategyLazy,
                    connectivityChecker = lazy { connectivityCheckerLazy.get(options.context) },
                    concurrentRequestStrategy = concurrentRequestStrategyLazy,
                )
            }
        }
    }
}
