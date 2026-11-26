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
package com.vitorpamplona.amethyst.commons.ui.components.animated

import androidx.collection.LruCache
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.vitorpamplona.amethyst.commons.richtext.isAnimatedGifUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

// Enough for the WebP header up to its VP8X flags (byte 20) and the GIF signature.
private const val HEADER_BYTES = 32L

// URLs probed this session, so a card scrolled back into view goes straight to the player.
private const val MAX_REMEMBERED = 1024

/**
 * Finds the images that animate although their URL doesn't end in `.gif`: an animated WebP, or a
 * GIF behind an extensionless Blossom URL. Coil on desktop and iOS draws only the first frame of those, so
 * once the image loader has the file, its header is read from the loader's disk cache and, when it
 * holds frames, the card hands the URL to [AnimatedGifImage], whose Skia codec plays WebP as well
 * as GIF. Android's decoder animates these by itself.
 */
object AnimatedImageProbe {
    // LruCache locks itself: cards on the UI thread and probes on IO share it.
    private val probed = LruCache<String, Boolean>(MAX_REMEMBERED)

    /** True when [url] is played by [AnimatedGifImage]: a `.gif`, or a file a probe found frames in. */
    fun isAnimated(url: String): Boolean = isAnimatedGifUrl(url) || probed[url] == true

    /**
     * Whether a load of [url] is worth a [probe]: not yet probed, and not named as a format Skia
     * can't animate (JPEG, PNG, whose APNG frames it doesn't play, SVG, BMP). Checked before
     * launching anything, since every memory-cache hit of a scrolled feed reports a load.
     */
    fun needsProbe(url: String): Boolean = !isAnimatedGifUrl(url) && !hasStillExtension(url) && probed[url] == null

    /**
     * Reads the header of [url]'s file from the image loader's disk cache (under [diskCacheKey],
     * the key its load reported, else the URL) and, when it may animate, loads its frames for the
     * player ([preloadAnimation]), so the card switches without a blank frame and only when there
     * is more than one frame. Remembers the answer. False when the file isn't cached there, as for
     * media fetched over Tor or encrypted, which stays still.
     */
    suspend fun probe(
        url: String,
        diskCacheKey: String?,
        fetcher: AnimatedImageFetcher = AnimatedImageFetcher.installed,
    ): Boolean {
        probed[url]?.let { return it }
        val animated =
            withContext(Dispatchers.IO) {
                val disk = SingletonImageLoader.get(PlatformContext.INSTANCE).diskCache ?: return@withContext null
                val header =
                    disk.openSnapshot(diskCacheKey ?: url)?.use { snapshot ->
                        runCatching {
                            disk.fileSystem.read(snapshot.data) {
                                request(HEADER_BYTES)
                                readByteArray(minOf(HEADER_BYTES, buffer.size))
                            }
                        }.getOrNull()
                    } ?: return@withContext null
                isAnimatedImageHeader(header)
            } ?: return false
        val playable = animated && preloadAnimation(url, fetcher)
        probed.put(url, playable)
        return playable
    }
}

private val STILL_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".svg", ".bmp")

/** True when [url]'s path ends in an extension of a format that never animates here. */
internal fun hasStillExtension(url: String): Boolean {
    val path = url.substringBefore('#').substringBefore('?')
    return STILL_EXTENSIONS.any { path.endsWith(it, ignoreCase = true) }
}

/**
 * True for a file that may hold more than one frame: an animated WebP (its VP8X chunk sets the
 * animation flag), or any GIF, whose frames [AnimatedGifImage] counts itself.
 */
internal fun isAnimatedImageHeader(header: ByteArray): Boolean = isGif(header) || isAnimatedWebp(header)

private fun isGif(header: ByteArray) = header.size >= 6 && header.startsWith(0, "GIF8")

// RIFF....WEBPVP8X, then the chunk size and a flags byte whose bit 1 says "animation".
private fun isAnimatedWebp(header: ByteArray) =
    header.size > 20 &&
        header.startsWith(0, "RIFF") &&
        header.startsWith(8, "WEBP") &&
        header.startsWith(12, "VP8X") &&
        (header[20].toInt() and 0x02) != 0

private fun ByteArray.startsWith(
    offset: Int,
    ascii: String,
): Boolean {
    if (size < offset + ascii.length) return false
    for (i in ascii.indices) if (this[offset + i] != ascii[i].code.toByte()) return false
    return true
}
