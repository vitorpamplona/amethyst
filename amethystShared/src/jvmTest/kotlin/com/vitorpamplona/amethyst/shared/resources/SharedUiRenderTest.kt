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
package com.vitorpamplona.amethyst.shared.resources

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.shared.R
import com.vitorpamplona.amethyst.ui.painterRes
import com.vitorpamplona.amethyst.ui.stringRes
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * End-to-end proof that a composable written against the Android app's own
 * resource API — `stringRes(R.string.x)` and `painterRes(R.drawable.x)` —
 * renders on the JVM with no Android framework present.
 *
 * Renders offscreen through [ImageComposeScene] so it runs headless in CI, and
 * inspects the resulting pixels rather than merely asserting the call returned.
 */
class SharedUiRenderTest {
    @Composable
    private fun Sample() {
        Column(Modifier.fillMaxSize().background(Color.White).padding(16.dp)) {
            Text(stringRes(R.string.app_name), color = Color.Black)
            Text(stringRes(R.string.cancel), color = Color.Black)
            Row {
                Icon(
                    painter = painterRes(R.drawable.amethyst_monochrome, 48),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color(0xFF7B2FBF),
                )
                Icon(
                    painter = painterRes(R.drawable.ic_home, 48),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color(0xFF2FBF7B),
                )
            }
        }
    }

    private fun render(
        width: Int = 320,
        height: Int = 200,
    ): IntArray {
        AndroidResourceTable.setLocale(Locale.ENGLISH)
        val scene = ImageComposeScene(width, height, Density(1f)) { Sample() }
        try {
            val image = scene.render()
            File("build/reports/shared-ui-render.png").apply {
                parentFile.mkdirs()
                writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            }
            val bitmap = Bitmap()
            bitmap.allocN32Pixels(width, height)
            check(image.readPixels(bitmap)) { "could not read rendered pixels" }
            return IntArray(width * height) { i -> bitmap.getColor(i % width, i / width) }
        } finally {
            scene.close()
        }
    }

    @Test
    fun `renders shared text and vector drawables without an Android framework`() {
        val pixels = render()
        val distinct = pixels.toHashSet()

        // A blank canvas would be a single colour. Real glyphs and two tinted
        // vector icons must put many more than that on screen.
        assertTrue(distinct.size > 50, "expected an actually drawn frame, saw ${distinct.size} distinct colours")

        // The two icon tints must both appear, which they only can if the
        // VectorDrawable XML parsed into real paths.
        val purple = pixels.count { (it shr 16 and 0xFF) > 100 && (it and 0xFF) > 150 && (it shr 8 and 0xFF) < 100 }
        val green = pixels.count { (it shr 8 and 0xFF) > 150 && (it shr 16 and 0xFF) < 100 }
        assertTrue(purple > 100, "purple-tinted vector icon did not draw ($purple px)")
        assertTrue(green > 100, "green-tinted raster icon did not draw ($green px)")
    }
}
