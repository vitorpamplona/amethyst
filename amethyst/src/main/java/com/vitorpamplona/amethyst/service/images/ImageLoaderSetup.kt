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

import android.content.Context
import android.os.Build
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.Uri
import coil3.annotation.DelicateCoilApi
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import coil3.fetch.Fetcher
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.network.CacheStrategy
import coil3.network.ConnectivityChecker
import coil3.network.NetworkFetcher
import coil3.network.okhttp.asNetworkClient
import coil3.request.Options
import coil3.size.Precision
import coil3.svg.SvgDecoder
import coil3.util.Logger
import coil3.video.VideoFrameDecoder
import com.vitorpamplona.amethyst.isDebug
import com.vitorpamplona.quartz.utils.Log
import okhttp3.Call

class ImageLoaderSetup {
    companion object {
        val gifFactory =
            if (Build.VERSION.SDK_INT >= 28) {
                AnimatedImageDecoder.Factory()
            } else {
                GifDecoder.Factory()
            }
        val svgFactory = SvgDecoder.Factory()

        val debugLogger = if (isDebug) MyDebugLogger() else null

        @OptIn(DelicateCoilApi::class)
        fun setup(
            app: Context,
            diskCache: () -> DiskCache,
            memoryCache: () -> MemoryCache,
            callFactory: (url: String) -> Call.Factory,
        ) {
            SingletonImageLoader.setUnsafe(
                ImageLoader
                    .Builder(app)
                    .diskCache(diskCache)
                    .memoryCache(memoryCache)
                    .precision(Precision.INEXACT)
                    .logger(debugLogger)
                    .components {
                        add(gifFactory)
                        add(svgFactory)
                        add(VideoFrameDecoder.Factory())
                        add(Base64Fetcher.Factory)
                        add(BlurHashFetcher.Factory)
                        add(Base64Fetcher.BKeyer)
                        add(BlurHashFetcher.BKeyer)
                        add(OkHttpFactory(callFactory))
                    }.build(),
            )
        }
    }
}

/**
 * Copied from Coil to block the printout of stack traces
 */
class MyDebugLogger(
    override var minLevel: Logger.Level = Logger.Level.Debug,
) : Logger {
    override fun log(
        tag: String,
        level: Logger.Level,
        message: String?,
        throwable: Throwable?,
    ) {
        val msg = message ?: throwable?.message
        if (msg != null) {
            when (level) {
                Logger.Level.Error -> Log.e(tag, msg)
                Logger.Level.Verbose -> Log.d(tag, msg)
                Logger.Level.Debug -> Log.d(tag, msg)
                Logger.Level.Info -> Log.i(tag, msg)
                Logger.Level.Warn -> Log.w(tag, msg)
            }
        }
    }
}

/**
 * Copied from Coil to allow networkClient to be a function of the url.
 * So that Tor and non Tor clients can be used.
 */
@OptIn(ExperimentalCoilApi::class)
class OkHttpFactory(
    val networkClient: (url: String) -> Call.Factory,
) : Fetcher.Factory<Uri> {
    private val cacheStrategyLazy = lazy { CacheStrategy.DEFAULT }
    private val connectivityCheckerLazy = singleParameterLazy(::ConnectivityChecker)

    override fun create(
        data: Uri,
        options: Options,
        imageLoader: ImageLoader,
    ): Fetcher? {
        if (!isApplicable(data)) return null

        val url = data.toString()

        return NetworkFetcher(
            url = url,
            options = options,
            networkClient = lazy { networkClient(url).asNetworkClient() },
            diskCache = lazy { imageLoader.diskCache },
            cacheStrategy = cacheStrategyLazy,
            connectivityChecker = connectivityCheckerLazy.get(options.context),
        )
    }

    private fun isApplicable(data: Uri): Boolean = data.scheme == "http" || data.scheme == "https"
}

internal fun <P, T> singleParameterLazy(initializer: (P) -> T) = SingleParameterLazy(initializer)

internal class SingleParameterLazy<P, T>(
    initializer: (P) -> T,
) : Any() {
    private var initializer: ((P) -> T)? = initializer
    private var value: Any? = UNINITIALIZED

    @Suppress("UNCHECKED_CAST")
    fun get(parameter: P): T {
        val value1 = value
        if (value1 !== UNINITIALIZED) {
            return value1 as T
        }

        return synchronized(this) {
            val value2 = value
            if (value2 !== UNINITIALIZED) {
                value2 as T
            } else {
                val newValue = initializer!!(parameter)
                value = newValue
                initializer = null
                newValue
            }
        }
    }
}

private object UNINITIALIZED
