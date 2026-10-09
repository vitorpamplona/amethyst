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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The full-screen viewer's transition, headless: a red "photo" filling a 200x200 viewer opens out of
 * a thumbnail at (10,10)-(60,60) and shrinks back into it on dismiss.
 */
class ZoomTransitionTest {
    private val thumbnail = Rect(10f, 10f, 60f, 60f)

    private fun Image.isRed(
        x: Int,
        y: Int,
    ): Boolean {
        val bitmap = Bitmap.makeFromImage(this)
        val color = bitmap.getColor(x, y)
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return r > 200 && g < 60 && b < 60
    }

    @Test
    fun growsOutOfTheThumbnailAndShrinksBackIntoIt() {
        var transition: ZoomTransition? = null
        var dismissed = false
        val scene =
            ImageComposeScene(200, 200, Density(1f)) {
                val t = rememberZoomTransition(thumbnail, onDismissed = { dismissed = true })
                transition = t
                Box(Modifier.fillMaxSize().zoomTransitionContainer(t)) {
                    Box(Modifier.fillMaxSize().zoomTransitionLayer(t)) {
                        Box(Modifier.fillMaxSize().background(Color.Red).onGloballyPositioned { t.onMediaPositioned(it, null) })
                    }
                }
            }
        try {
            val ms = 1_000_000L
            scene.render(0)
            scene.render(16 * ms)
            val start = scene.render(32 * ms)
            assertTrue(start.isRed(35, 35), "the photo starts inside the thumbnail")
            assertFalse(start.isRed(150, 150), "and nowhere else")

            val open = scene.render(600 * ms)
            assertEquals(1f, transition!!.progress)
            assertTrue(open.isRed(150, 150), "then fills the viewer")

            transition!!.dismiss()
            scene.render(616 * ms)
            scene.render(632 * ms)
            val closing = scene.render(760 * ms)
            assertFalse(closing.isRed(190, 190), "on dismiss it pulls back toward the thumbnail")
            assertFalse(dismissed, "the viewer stays until the exit has played")

            scene.render(1200 * ms)
            scene.render(1216 * ms)
            assertTrue(dismissed, "and closes once it has")
        } finally {
            scene.close()
        }
    }
}
