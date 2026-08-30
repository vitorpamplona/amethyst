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
package androidx.core.graphics.drawable

import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.net.Uri

/**
 * JVM stand-in for androidx.core.graphics.drawable.IconCompat.
 *
 * Keeps the bitmap it was built from, because that is what the notification
 * path puts in it — a loaded, circle-cropped avatar — and a presenter that
 * cannot get it back would show a nameless notification with no face.
 */
class IconCompat private constructor(
    val bitmap: Bitmap?,
    val resourceId: Int,
    val uri: Uri?,
) {
    fun toIcon(): Icon =
        when {
            bitmap != null -> Icon.createWithBitmap(bitmap)
            uri != null -> Icon.createWithContentUri(uri)
            else -> Icon.createWithResource("", resourceId)
        }

    companion object {
        @JvmStatic
        fun createWithBitmap(bitmap: Bitmap?) = IconCompat(bitmap, 0, null)

        @JvmStatic
        fun createWithAdaptiveBitmap(bitmap: Bitmap?) = IconCompat(bitmap, 0, null)

        @JvmStatic
        fun createWithResource(
            context: Any?,
            resourceId: Int,
        ) = IconCompat(null, resourceId, null)

        @JvmStatic
        fun createWithContentUri(uri: Uri?) = IconCompat(null, 0, uri)

        @JvmStatic
        fun createWithContentUri(uri: String) = IconCompat(null, 0, Uri.parse(uri))
    }
}
