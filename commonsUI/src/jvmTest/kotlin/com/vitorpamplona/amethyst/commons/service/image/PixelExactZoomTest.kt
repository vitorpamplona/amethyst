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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.compose.ImagePainter
import coil3.decode.DataSource
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class PixelExactZoomTest {
    /** A 1-pixel black and white checkerboard: any resampling turns it gray. */
    private fun checker(size: Int): Bitmap {
        val bitmap = Bitmap()
        bitmap.allocN32Pixels(size, size, true)
        val bytes = ByteArray(size * size * 4)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val v = if ((x + y) % 2 == 0) 0 else 255
                val i = (y * size + x) * 4
                bytes[i] = v.toByte()
                bytes[i + 1] = v.toByte()
                bytes[i + 2] = v.toByte()
                bytes[i + 3] = 255.toByte()
            }
        }
        bitmap.installPixels(bytes)
        bitmap.setImmutable()
        return bitmap
    }

    /** Share of pixels near pure black or white in the middle of a 400 px box, fit and zoomed 4x by a layer. */
    private fun sharpShare(painter: Painter): Double {
        val scene =
            ImageComposeScene(400, 400, Density(1f)) {
                Image(painter, null, Modifier.size(400.dp).graphicsLayer(scaleX = 4f, scaleY = 4f), contentScale = ContentScale.Fit)
            }
        val shot = scene.render()
        scene.close()
        val out = Bitmap.makeFromImage(shot)
        var sharp = 0
        var total = 0
        for (y in 150 until 250) {
            for (x in 150 until 250) {
                val g = Color.getG(out.getColor(x, y))
                if (g < 40 || g > 215) sharp++
                total++
            }
        }
        return sharp.toDouble() / total
    }

    @Test
    fun aViewerZoomingWithALayerSeesTheOriginalPixels() {
        // 1600 px fit into a 400 px box: a 4x zoom shows the picture's own pixels, 1:1.
        val bitmap = checker(1600)
        val image = PixelExactImage(bitmap)
        val loaded = AsyncImagePainter.State.Success(ImagePainter(image), successResult(image))

        // The zoom is a layer scale, which a copy made for the fitted size couldn't follow: what the
        // viewer draws keeps every pixel of the checkerboard.
        assertEquals(1.0, sharpShare(loaded.withFullResolution().painter!!))
    }

    private fun successResult(image: PixelExactImage) =
        SuccessResult(
            image = image,
            request = ImageRequest.Builder(PlatformContext.INSTANCE).data("test").build(),
            dataSource = DataSource.MEMORY,
        )
}
