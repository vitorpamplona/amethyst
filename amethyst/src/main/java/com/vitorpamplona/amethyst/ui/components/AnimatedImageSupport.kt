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
package com.vitorpamplona.amethyst.ui.components

import androidx.annotation.StringRes
import com.vitorpamplona.amethyst.R

fun isAnimatedImageUrl(url: String): Boolean = isGifUrl(url) || isAvifUrl(url)

fun isGifUrl(url: String): Boolean = hasImageExtension(url, ".gif")

fun isAvifUrl(url: String): Boolean = hasImageExtension(url, ".avif")

fun isAnimatedImageMimeType(mimeType: String?): Boolean =
    mimeType.equals("image/gif", ignoreCase = true) ||
        mimeType.equals("image/avif", ignoreCase = true)

fun isAnimatedImageFileName(fileName: String?): Boolean =
    fileName != null && (isGifUrl(fileName) || isAvifUrl(fileName))

@StringRes
fun animatedImageLabelRes(
    url: String?,
    mimeType: String?,
): Int =
    if (mimeType.equals("image/avif", ignoreCase = true) || (url != null && isAvifUrl(url))) {
        R.string.avif
    } else {
        R.string.gif
    }

private fun hasImageExtension(
    value: String,
    extension: String,
): Boolean =
    value.endsWith(extension, ignoreCase = true) ||
        value.contains("$extension?", ignoreCase = true) ||
        value.contains("$extension#", ignoreCase = true)
