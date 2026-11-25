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
package com.vitorpamplona.amethyst.ios

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.size.Precision
import com.vitorpamplona.amethyst.commons.service.http.EncryptionKeyCache
import com.vitorpamplona.amethyst.commons.service.image.Base64Fetcher
import com.vitorpamplona.amethyst.commons.service.image.BlurHashFetcher
import com.vitorpamplona.amethyst.commons.service.image.SkiaDownsamplingDecoder
import com.vitorpamplona.amethyst.commons.service.image.SkiaGifDecoder
import com.vitorpamplona.amethyst.commons.service.image.ThumbHashFetcher
import io.ktor.client.HttpClient
import okio.Path

/**
 * iOS's image loader: Coil over the app's Ktor client (the Darwin engine), with Skia's decoders and
 * the shared placeholder fetchers. The JVM and Android build theirs on OkHttp
 * (`buildAmethystImageLoader`), which iOS cannot link. `blossom:` URIs and the per-URL Tor choice
 * come with Blossom and Tor on iOS (A2, B12).
 */
object IosImageLoaderSetup {
    @OptIn(DelicateCoilApi::class)
    fun setup(
        httpClient: HttpClient,
        cacheDir: Path,
        keyCache: EncryptionKeyCache,
        download: suspend (String) -> ByteArray?,
    ) {
        val context = PlatformContext.INSTANCE
        val loader =
            ImageLoader
                .Builder(context)
                .memoryCache { MemoryCache.Builder().maxSizePercent(context, MEMORY_CACHE_PERCENT).build() }
                .diskCache {
                    DiskCache
                        .Builder()
                        .directory(cacheDir)
                        .maxSizeBytes(MAX_DISK_CACHE_BYTES)
                        .build()
                }.precision(Precision.INEXACT)
                .components {
                    add(SkiaGifDecoder.Factory())
                    // After the GIF decoder: it takes any format, so it must come last.
                    add(SkiaDownsamplingDecoder.Factory())
                    add(Base64Fetcher.Factory)
                    add(BlurHashFetcher.Factory)
                    add(ThumbHashFetcher.Factory)
                    add(Base64Fetcher.BKeyer)
                    add(BlurHashFetcher.BKeyer)
                    add(ThumbHashFetcher.TKeyer)
                    // Before the network fetcher: an encrypted picture's URL must not reach Coil raw.
                    add(EncryptedBlobFetcher.Factory(keyCache, download))
                    add(KtorNetworkFetcherFactory(httpClient = { httpClient }))
                }.build()
        SingletonImageLoader.setUnsafe(loader)
    }

    private const val MEMORY_CACHE_PERCENT = 0.15

    // Android's ceiling (ImageCacheFactory). iOS may purge the Caches folder when space runs low.
    private const val MAX_DISK_CACHE_BYTES = 1024L * 1024 * 1024
}
