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
package com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.studio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.ImageCompressionPreview
import com.vitorpamplona.amethyst.commons.service.uploads.ImageCrop
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMediaProcessing

/**
 * The studio's compressed previews, one per (image, level), each made exactly as the upload would
 * make it. They are kept while the studio is open, so returning to a level is instant and every
 * stop can show its real size; the temp files go when the studio closes. A level maps to null when
 * the platform could not compress that image.
 */
@Stable
class CompressionPreviews(
    private val uploader: MediaUploader,
) {
    private val results = mutableStateMapOf<Pair<SelectedMedia, CompressorQuality>, ImageCompressionPreview?>()

    fun isLoaded(
        media: SelectedMedia,
        quality: CompressorQuality,
    ) = results.containsKey(media to quality)

    operator fun get(
        media: SelectedMedia,
        quality: CompressorQuality,
    ): ImageCompressionPreview? = results[media to quality]

    /** Any preview of [media], for the original's stats before the chosen level has loaded. */
    fun anyOf(media: SelectedMedia): ImageCompressionPreview? = results.entries.firstOrNull { it.key.first === media && it.value != null }?.value

    /** Compresses [media] at [quality] unless already done. Cancellable: the uploader deletes what it wrote. */
    suspend fun load(
        media: SelectedMedia,
        quality: CompressorQuality,
    ) {
        if (quality.imageMaxDimension == null || isLoaded(media, quality)) return
        results[media to quality] = uploader.previewImageCompression(media.uri, media.mimeType, quality)
    }

    fun discard(media: SelectedMedia) {
        results.keys.filter { it.first === media }.forEach { key -> results.remove(key)?.let { uploader.discardTempFile(it.compressedUri) } }
    }

    fun discardAll() {
        results.values.forEach { preview -> preview?.let { uploader.discardTempFile(it.compressedUri) } }
        results.clear()
    }
}

@Composable
fun rememberCompressionPreviews(uploader: MediaUploader): CompressionPreviews {
    val previews = remember(uploader) { CompressionPreviews(uploader) }
    DisposableEffect(previews) { onDispose { previews.discardAll() } }
    return previews
}

/**
 * The cropped copy of each picture whose options carry a crop, written as the upload will write
 * it ([MediaUploader.cropImage]), so the quality previews, their sizes and hold-for-original show
 * the part that will be sent. A copy is rewritten when its crop changes and deleted, with its
 * previews, when replaced or when the studio closes.
 */
@Stable
class CroppedSources(
    private val uploader: MediaUploader,
    private val previews: CompressionPreviews,
) {
    private class Made(
        val crop: ImageCrop,
        /** Null when the platform could not write it: the previews then show the whole picture. */
        val copy: SelectedMedia?,
    )

    private val made = mutableStateMapOf<SelectedMediaProcessing, Made>()

    /** What the previews of [item] are made from: its cropped copy, the file itself when uncropped, null while the copy is being written. */
    fun sourceFor(item: SelectedMediaProcessing): SelectedMedia? {
        val crop = item.options.crop ?: return item.media
        val done = made[item]?.takeIf { it.crop == crop } ?: return null
        return done.copy ?: item.media
    }

    /** Writes [item]'s cropped copy unless it is current. */
    suspend fun prepare(item: SelectedMediaProcessing) {
        val crop = item.options.crop
        val old = made[item]
        if (crop == null) {
            if (old != null) made.remove(item)?.let(::drop)
            return
        }
        if (old?.crop == crop) return
        val written = uploader.cropImage(item.media.uri, item.media.mimeType, crop)?.let { SelectedMedia(it.uri, it.contentType) }
        made[item] = Made(crop, written)
        old?.let(::drop)
    }

    private fun drop(old: Made) {
        old.copy?.let {
            previews.discard(it)
            uploader.discardTempFile(it.uri)
        }
    }

    fun discardAll() {
        made.values.forEach(::drop)
        made.clear()
    }
}

@Composable
fun rememberCroppedSources(
    uploader: MediaUploader,
    previews: CompressionPreviews,
): CroppedSources {
    val sources = remember(uploader, previews) { CroppedSources(uploader, previews) }
    DisposableEffect(sources) { onDispose { sources.discardAll() } }
    return sources
}
