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

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Stable
import coil3.ImageLoader
import coil3.Uri
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.decode.DataSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.network.CacheStrategy
import coil3.network.ConcurrentRequestStrategy
import coil3.network.ConnectivityChecker
import coil3.network.NetworkFetcher
import coil3.network.okhttp.asNetworkClient
import coil3.request.Options
import com.vitorpamplona.amethyst.commons.ui.components.PROFILE_PIC_SCHEME
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.Call
import kotlin.coroutines.cancellation.CancellationException

/**
 * Coil Fetcher that serves pre-resized profile picture thumbnails from a dedicated disk cache.
 *
 * URLs are wrapped as `profilepic://https://example.com/pic.jpg` by the avatar composables.
 *
 * Flow:
 * - Thumbnail cache hit: returns the tiny ~5KB JPEG directly. No network, no large decode.
 * - Thumbnail cache miss (first load): passes the result straight through to Coil's normal
 *   pipeline (zero overhead). In the background, reads the file from Coil's disk cache
 *   after download and generates a thumbnail for next time.
 *
 * The zoomable full-screen dialog uses the raw URL (without profilepic:// prefix),
 * which goes through the normal Coil pipeline for full-resolution display.
 */
@Stable
class ProfilePictureFetcher(
    private val originalUrl: String,
    private val options: Options,
    private val thumbnailCache: ThumbnailDiskCache,
    private val networkFetcher: Fetcher,
    private val diskCacheLazy: Lazy<DiskCache?>,
    private val backgroundScope: CoroutineScope,
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        // Fast path: return pre-resized thumbnail from disk (~5KB read)
        val cached = thumbnailCache.get(originalUrl)
        if (cached != null) {
            val bitmap = thumbnailCache.decodeThumbnail(cached)
            if (bitmap != null) {
                return ImageFetchResult(
                    image = bitmap.asImage(true),
                    isSampled = true,
                    dataSource = DataSource.DISK,
                )
            }
        }

        // Cache miss: download via normal network fetcher.
        // NetworkFetcher writes the original to Coil's disk cache and returns
        // a source pointing to it. We pass this through unchanged — Coil's
        // decoder pipeline handles streaming decode with inSampleSize, no
        // large byte array allocation.
        val result =
            try {
                networkFetcher.fetch() ?: return null
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                return null
            }

        // Fire-and-forget: generate thumbnail from Coil's disk cache in background.
        // The file is already there because NetworkFetcher just wrote it.
        backgroundScope.launch {
            generateThumbnailFromDiskCache()
        }

        return result
    }

    /**
     * Reads the original image from Coil's disk cache and generates a small
     * thumbnail for our dedicated thumbnail cache. Uses file-based BitmapFactory
     * decode which is seekable (supports two-pass bounds+decode) and never loads
     * the entire file into a byte array.
     */
    private fun generateThumbnailFromDiskCache() {
        val diskCache = diskCacheLazy.value ?: return

        diskCache.openSnapshot(originalUrl)?.use { snapshot ->
            val file = snapshot.data.toFile()
            val targetSize = ThumbnailDiskCache.THUMBNAIL_SIZE_PX

            // First pass: decode bounds only (no memory allocation for pixels)
            val boundsOptions =
                BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
            BitmapFactory.decodeFile(file.absolutePath, boundsOptions)

            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return

            // Calculate inSampleSize for efficient memory use during decode
            val sampleSize = calculateInSampleSize(boundsOptions, targetSize, targetSize)

            // Second pass: decode at reduced size (streams from file, not byte array)
            val decodeOptions =
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }
            val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return

            // Scale to exact target size
            val scaled = Bitmap.createScaledBitmap(decoded, targetSize, targetSize, true)
            if (scaled !== decoded) {
                decoded.recycle()
            }

            thumbnailCache.save(originalUrl, scaled)
            scaled.recycle()
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int,
    ): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    companion object {
        const val SCHEME = PROFILE_PIC_SCHEME

        fun extractOriginalUrl(data: Uri): String {
            // profilepic://https://example.com/pic.jpg → https://example.com/pic.jpg
            val full = data.toString()
            return full.removePrefix("$SCHEME://")
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    class Factory(
        private val thumbnailCache: ThumbnailDiskCache,
        private val networkClient: (url: String) -> Call.Factory,
        private val backgroundScope: CoroutineScope,
    ) : Fetcher.Factory<Uri> {
        private val connectivityCheckerLazy = singleParameterLazy(::ConnectivityChecker)

        override fun create(
            data: Uri,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher? {
            if (data.scheme != SCHEME) return null

            val originalUrl = extractOriginalUrl(data)
            val diskCacheLazy = lazy { imageLoader.diskCache }

            val netFetcher =
                NetworkFetcher(
                    url = originalUrl,
                    options = options,
                    networkClient = lazy { networkClient(originalUrl).asNetworkClient() },
                    diskCache = diskCacheLazy,
                    cacheStrategy = lazy { CacheStrategy.DEFAULT },
                    connectivityChecker = lazy { connectivityCheckerLazy.get(options.context) },
                    concurrentRequestStrategy = lazy { ConcurrentRequestStrategy.UNCOORDINATED },
                )

            return ProfilePictureFetcher(
                originalUrl,
                options,
                thumbnailCache,
                netFetcher,
                diskCacheLazy,
                backgroundScope,
            )
        }
    }

    object BKeyer : Keyer<Uri> {
        override fun key(
            data: Uri,
            options: Options,
        ): String? =
            if (data.scheme == SCHEME) {
                "profilepic_thumb_${extractOriginalUrl(data)}"
            } else {
                null
            }
    }
}
