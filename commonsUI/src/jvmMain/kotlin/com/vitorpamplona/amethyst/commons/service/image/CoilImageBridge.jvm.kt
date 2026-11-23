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
package com.vitorpamplona.amethyst.commons.service.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import coil3.Image
import com.vitorpamplona.amethyst.commons.base64Image.toPlatformImage
import com.vitorpamplona.amethyst.commons.blurhash.PlatformImage
import com.vitorpamplona.amethyst.commons.richtext.Base64Image

// Placeholders (blurhash, thumbhash) are a few dozen pixels stretched over a whole card: drawn 1:1 from
// a copy at the card's size, not resampled every frame (see PixelExactImage).
actual fun PlatformImage.toCoilImage(): Image = PixelExactImage(toSkiaBitmap())

actual fun base64DataUriToCoilImage(dataUri: String): Image = Base64Image.toPlatformImage(dataUri).toCoilImage()

actual fun PlatformImage.toComposeImageBitmap(): ImageBitmap = toSkiaBitmap().asComposeImageBitmap()
