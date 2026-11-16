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

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.unit.Density
import com.vitorpamplona.amethyst.commons.service.uploads.ImageCrop
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.StringMediaUri
import com.vitorpamplona.amethyst.commons.service.uploads.UnavailableMediaUploader
import com.vitorpamplona.amethyst.commons.ui.prototypes.mediacomposer.sunsetPhoto
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystPreviewTheme
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The crop math, and the crop studio rendered to `commonsUI/build/media-studio/crop-<name>.png`. */
class ImageCropTest {
    private val aspect = 4f / 3f

    private fun pixelRatio(c: ImageCrop) = c.width * aspect / c.height

    @Test
    fun cornerDragKeepsTheRatio() {
        val start = ImageCrop.centered(aspect, 1f)
        val grown = dragCrop(start, CropHandle.BOTTOM_RIGHT, 0.1f, 0.02f, aspect, 1f)
        assertEquals(1f, pixelRatio(grown), 1e-4f)
        assertEquals(start.left, grown.left, 1e-6f, "the opposite corner stays")
        assertEquals(start.top, grown.top, 1e-6f)
    }

    @Test
    fun cornerDragStopsAtTheEdge() {
        // The square already fills the height: dragging outward cannot grow it.
        val start = ImageCrop.centered(aspect, 1f)
        val dragged = dragCrop(start, CropHandle.TOP_LEFT, -0.3f, -0.3f, aspect, 1f)
        assertTrue(dragged.left >= 0f && dragged.top >= 0f)
        assertEquals(1f, pixelRatio(dragged), 1e-4f)
    }

    @Test
    fun bannerShrinksFromItsCorner() {
        val start = ImageCrop.centered(aspect, 3f)
        val smaller = dragCrop(start, CropHandle.TOP_LEFT, 0.2f, 0f, aspect, 3f)
        assertEquals(3f, pixelRatio(smaller), 1e-4f)
        assertEquals(start.right, smaller.right, 1e-6f)
        assertEquals(start.bottom, smaller.bottom, 1e-6f)
        assertTrue(smaller.width < start.width)
    }

    @Test
    fun moveStaysInside() {
        val start = ImageCrop(0.2f, 0.2f, 0.6f, 0.6f)
        val moved = dragCrop(start, CropHandle.MOVE, 0.9f, -0.9f, aspect, null)
        assertEquals(1f, moved.right, 1e-5f)
        assertEquals(0f, moved.top, 1e-5f)
        assertEquals(start.width, moved.width, 1e-5f, "same size")
        assertEquals(start.height, moved.height, 1e-5f)
    }

    @Test
    fun freeCornerDoesNotCross() {
        val start = ImageCrop(0.2f, 0.2f, 0.6f, 0.6f)
        val dragged = dragCrop(start, CropHandle.BOTTOM_RIGHT, -0.9f, -0.9f, aspect, null)
        assertTrue(dragged.right > dragged.left && dragged.bottom > dragged.top)
    }

    @Test
    fun aReopenedCropKeepsItsShape() {
        assertEquals(CropAspect.SQUARE, CropAspect.of(ImageCrop.centered(aspect, 1f), aspect))
        assertEquals(CropAspect.WIDE, CropAspect.of(ImageCrop.centered(aspect, 16f / 9f), aspect))
        assertEquals(CropAspect.FREE, CropAspect.of(ImageCrop(0.1f, 0.1f, 0.7f, 0.5f), aspect))
        assertEquals(CropAspect.FREE, CropAspect.of(null, aspect))
    }

    @Test
    fun aWholePictureIsNoCrop() {
        assertEquals(null, ImageCrop(0.0001f, 0f, 1f, 0.9999f).orNullIfWhole())
        assertEquals(ImageCrop(0.1f, 0f, 1f, 1f), ImageCrop(0.1f, 0f, 1f, 1f).orNullIfWhole())
    }

    @Test
    fun clockAndLengthLabels() {
        assertEquals("0:02.5", formatClock(2_480))
        assertEquals("1:05.0", formatClock(65_000))
        assertEquals("6.5 s", formatLength(6_500))
        assertEquals("1:05", formatLength(65_000))
    }

    // ---- renders ----

    private val outDir = File("build/media-studio").apply { mkdirs() }
    private val sunset =
        File(outDir, "files/crop-sunset.png").apply {
            parentFile.mkdirs()
            writeBytes(Image.makeFromBitmap(sunsetPhoto().asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
    private val media = SelectedMedia(StringMediaUri(sunset.absolutePath), "image/png")
    private val uploader: MediaUploader = UnavailableMediaUploader

    @Test
    fun avatar() = render("avatar") { ImageCropStudio(media, ImageCropShape.Avatar, uploader, {}, {}, initialAspect = aspect) }

    @Test
    fun banner() = render("banner") { ImageCropStudio(media, ImageCropShape.Banner, uploader, {}, {}, initialAspect = aspect) }

    private fun render(
        name: String,
        content: @Composable () -> Unit,
    ) {
        val density = 2f
        val scene =
            ImageComposeScene(width = (400 * density).toInt(), height = (860 * density).toInt(), density = Density(density)) {
                AmethystPreviewTheme(dark = true) { Surface(color = MaterialTheme.colorScheme.background) { content() } }
            }
        try {
            var image = scene.render(0)
            repeat(20) { frame ->
                Thread.sleep(60)
                image = scene.render((frame + 1) * 60 * 1_000_000L)
            }
            File(outDir, "crop-$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        } finally {
            scene.close()
        }
    }
}
