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

import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.svg.SvgDecoder
import com.vitorpamplona.amethyst.commons.service.http.BlossomReadAuthTokenProvider
import com.vitorpamplona.amethyst.commons.service.image.SkiaDownsamplingDecoder
import com.vitorpamplona.amethyst.commons.service.image.SkiaGifDecoder
import com.vitorpamplona.amethyst.commons.service.image.buildAmethystImageLoader
import com.vitorpamplona.amethyst.commons.service.upload.blossom.bud10.BlossomServerResolver
import com.vitorpamplona.amethyst.desktop.app.DesktopInstance
import okhttp3.Call
import okio.Path.Companion.toOkioPath
import java.io.File

object DesktopImageLoaderSetup {
    /**
     * The shared image loader ([buildAmethystImageLoader]) with Skia's decoders. [clientForUrl]
     * picks the HTTP client per image, so each URL gets its own Tor decision;
     * [blossomServerResolver] loads `blossom:` URIs from the server that holds the blob; [readAuth]
     * signs the retry when a gated Blossom host answers 401.
     */
    @OptIn(DelicateCoilApi::class)
    fun setup(
        clientForUrl: (String) -> Call.Factory,
        blossomServerResolver: (() -> BlossomServerResolver)? = null,
        readAuth: BlossomReadAuthTokenProvider? = null,
    ) {
        SingletonImageLoader.setUnsafe(
            buildAmethystImageLoader(
                context = PlatformContext.INSTANCE,
                diskCache = { newDiskCache() },
                memoryCache = { newMemoryCache() },
                callFactory = clientForUrl,
                blossomServerResolver = blossomServerResolver,
                readAuth = readAuth,
                decoders = {
                    add(SvgDecoder.Factory())
                    add(SkiaGifDecoder.Factory())
                    // After the SVG and GIF decoders: it takes any format, so it must come last.
                    add(SkiaDownsamplingDecoder.Factory())
                },
            ),
        )
    }

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
            // One process per cache: a second copy of the app gets its own (see DesktopInstance).
            .directory(DesktopInstance.ownFolder(DesktopInstance.PerCopyFolder.IMAGES).toOkioPath())
            .maxSizeBytes(512L * 1024 * 1024)
            .build()

    /** The OS's per-user cache folder. */
    fun cacheDir(): File {
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
