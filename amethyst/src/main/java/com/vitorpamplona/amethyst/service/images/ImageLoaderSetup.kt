/**
 * Copyright (c) 2024 Vitor Pamplona
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

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.size.Precision
import coil3.svg.SvgDecoder
import coil3.util.DebugLogger
import com.vitorpamplona.amethyst.isDebug
import okhttp3.Call

class ImageLoaderSetup {
    companion object {
        @OptIn(DelicateCoilApi::class)
        fun setup(
            app: Application,
            diskCache: DiskCache,
            memoryCache: MemoryCache,
            callFactory: () -> Call.Factory,
        ) {
            SingletonImageLoader.setUnsafe(
                ImageLoader
                    .Builder(app)
                    .diskCache { diskCache }
                    .memoryCache { memoryCache }
                    .precision(Precision.INEXACT)
                    .logger(if (isDebug) DebugLogger() else null)
                    .components {
                        if (Build.VERSION.SDK_INT >= 28) {
                            add(AnimatedImageDecoder.Factory())
                        } else {
                            add(GifDecoder.Factory())
                        }
                        add(SvgDecoder.Factory())
                        add(Base64Fetcher.Factory)
                        add(BlurHashFetcher.Factory)
                        add(Base64Fetcher.BKeyer)
                        add(BlurHashFetcher.BKeyer)
                        add(OkHttpNetworkFetcherFactory(callFactory))
                    }.build(),
            )
        }
    }
}
