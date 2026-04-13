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
package com.vitorpamplona.amethyst.desktop.service.images

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.Uri
import coil3.annotation.DelicateCoilApi
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import coil3.fetch.Fetcher
import coil3.memory.MemoryCache
import coil3.network.CacheStrategy
import coil3.network.ConcurrentRequestStrategy
import coil3.network.ConnectivityChecker
import coil3.network.NetworkFetcher
import coil3.network.okhttp.asNetworkClient
import coil3.request.Options
import coil3.size.Precision
import coil3.svg.SvgDecoder
import com.vitorpamplona.amethyst.desktop.network.DesktopHttpClient
import okhttp3.Call
import okio.Path.Companion.toOkioPath
import java.io.File

object DesktopImageLoaderSetup {
    @OptIn(DelicateCoilApi::class)
    fun setup() {
        SingletonImageLoader.setUnsafe(createImageLoader())
    }

    fun createImageLoader(): ImageLoader =
        ImageLoader
            .Builder(PlatformContext.INSTANCE)
            .memoryCache { newMemoryCache() }
            .diskCache { newDiskCache() }
            .precision(Precision.INEXACT)
            .components {
                add(TorAwareOkHttpFactory { DesktopHttpClient.currentClient() })
                add(SvgDecoder.Factory())
                add(SkiaGifDecoder.Factory())
                add(DesktopBase64Fetcher.Factory)
                add(DesktopBlurHashFetcher.Factory)
                add(DesktopBase64Fetcher.BKeyer)
                add(DesktopBlurHashFetcher.BKeyer)
            }.build()

    private fun newMemoryCache(): MemoryCache {
        val maxMemory = Runtime.getRuntime().maxMemory()
        val cacheSize = (maxMemory * 0.15).toLong().coerceAtMost(256L * 1024 * 1024)
        return MemoryCache
            .Builder()
            .maxSizeBytes(cacheSize)
            .strongReferencesEnabled(false)
            .build()
    }

    private fun newDiskCache(): DiskCache =
        DiskCache
            .Builder()
            .directory(cacheDir().resolve("AmethystDesktop/image_cache").toOkioPath())
            .maxSizeBytes(512L * 1024 * 1024)
            .build()

    private fun cacheDir(): File {
        val os = System.getProperty("os.name").lowercase()
        return when {
            "mac" in os -> {
                File(System.getProperty("user.home"), "Library/Caches")
            }

            "win" in os -> {
                File(
                    System.getenv("LOCALAPPDATA")
                        ?: System.getProperty("user.home"),
                )
            }

            else -> {
                File(
                    System.getenv("XDG_CACHE_HOME")
                        ?: "${System.getProperty("user.home")}/.cache",
                )
            }
        }
    }
}

/**
 * Custom Coil Fetcher.Factory that routes all image requests through the Tor-aware client.
 * Pattern ported from Android's OkHttpFactory in ImageLoaderSetup.kt.
 *
 * Each image request calls [clientProvider] to get the current OkHttpClient,
 * which returns the SOCKS proxy client when Tor is active or the direct client when off.
 */
@OptIn(ExperimentalCoilApi::class)
private class TorAwareOkHttpFactory(
    val clientProvider: () -> Call.Factory,
) : Fetcher.Factory<Uri> {
    private val cacheStrategyLazy = lazy { CacheStrategy.DEFAULT }

    override fun create(
        data: Uri,
        options: Options,
        imageLoader: ImageLoader,
    ): Fetcher? {
        if (data.scheme != "http" && data.scheme != "https") return null

        return NetworkFetcher(
            url = data.toString(),
            options = options,
            networkClient = lazy { clientProvider().asNetworkClient() },
            diskCache = lazy { imageLoader.diskCache },
            cacheStrategy = cacheStrategyLazy,
            connectivityChecker = lazy { ConnectivityChecker(options.context) },
            concurrentRequestStrategy = lazy { ConcurrentRequestStrategy.UNCOORDINATED },
        )
    }
}
