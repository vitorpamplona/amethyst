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

import coil3.decode.ImageSource
import com.vitorpamplona.amethyst.commons.service.image.DeferredDeleteFileSystem
import okio.FileSystem

/**
 * Re-points a disk-cache-backed [ImageSource] at [FileSystem.SYSTEM] so Coil's platform
 * `ImageDecoder` fast path stays reachable.
 *
 * Coil hands `android.graphics.ImageDecoder` a *file* only when the source's file system is
 * **referentially** [FileSystem.SYSTEM] (`ImageSource.toImageDecoderSourceOrNull`, verified
 * against Coil 3.5.0):
 *
 * ```
 * if (fileSystem === FileSystem.SYSTEM) {
 *     val file = fileOrNull()
 *     if (file != null) return ImageDecoder.createSource(file.toFile())
 * }
 * ```
 *
 * The source Coil's `NetworkFetcher` builds carries `diskCache.fileSystem`, and ours is a
 * [DeferredDeleteFileSystem] wrapper, so that identity check fails for **every** image we
 * fetch from the network. The fallback for an animated image is
 * `ImageDecoder.createSource(source.squashToDirectByteBuffer())`, which pulls the entire
 * encoded animation onto the heap and then copies it into an equally large direct
 * `ByteBuffer` that stays alive for as long as the `AnimatedImageDrawable` does. Measured on
 * a 69.8 MB / 201-frame GIF: 66 MB of heap plus 66 MB of native memory per decode — and
 * animated results are never memory-cached (`DrawableImage.shareable` is false), so the feed
 * pays it again every time the note scrolls back into view.
 *
 * Only the delete path is deferred by the wrapper; reads go straight to
 * [FileSystem.SYSTEM]. So handing the decoder the same file on the real system file system
 * is equivalent, and it is exactly what Coil does when no wrapper is installed.
 *
 * Returns `this` unchanged when there is nothing to gain: already on [FileSystem.SYSTEM], not
 * a wrapper we know unwraps to it, or not backed by a file yet. [ImageSource.fileOrNull] is
 * used rather than [ImageSource.file] on purpose — the latter would materialise a temp copy
 * of a stream-backed source, which is the very cost this exists to avoid.
 */
fun ImageSource.onSystemFileSystem(): ImageSource {
    if (fileSystem === FileSystem.SYSTEM) return this

    var unwrapped: FileSystem = fileSystem
    while (unwrapped is DeferredDeleteFileSystem) unwrapped = unwrapped.delegate
    if (unwrapped !== FileSystem.SYSTEM) return this

    val file = fileOrNull() ?: return this

    return ImageSource(file = file, fileSystem = FileSystem.SYSTEM, metadata = metadata)
}
