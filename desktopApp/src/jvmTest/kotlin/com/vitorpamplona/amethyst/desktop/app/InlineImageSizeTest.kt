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
package com.vitorpamplona.amethyst.desktop.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * An image in a note keeps to its slot. A tall photo whose imeta declared its size was measured at
 * the column's full width and its own ratio, past the height cap, and its slot centered it, so it
 * spilled over the text above and the reactions below.
 */
class InlineImageSizeTest {
    @get:Rule
    val compose = createComposeRule()

    /** The size the image is drawn at, in dp, in a 490dp column. */
    private fun measure(dim: DimensionTag?): Pair<Float, Float> = measureWith { inlineImageSize(dim, 600.dp) }

    private fun measureRatio(ratio: Float?): Pair<Float, Float> = measureWith { inlineImageSize(ratio, 600.dp) }

    private fun measureWith(sizing: Modifier.() -> Modifier): Pair<Float, Float> {
        var measured = IntSize.Zero
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            Column(Modifier.width(490.dp)) {
                Box(Modifier.sizing().onSizeChanged { measured = it })
            }
        }
        compose.waitForIdle()
        return measured.width / density to measured.height / density
    }

    @Test
    fun aTallPhotoStaysWithinTheHeightCap() {
        // 3:4, as a phone photo: full width would need 653dp.
        val (width, height) = measure(DimensionTag(1536, 2048))
        assertEquals(600f, height, 0.5f)
        assertEquals(450f, width, 0.5f)
    }

    @Test
    fun aWidePhotoTakesTheColumnsWidth() {
        val (width, height) = measure(DimensionTag(1600, 900))
        assertEquals(490f, width, 0.5f)
        assertEquals(275.6f, height, 0.5f)
    }

    @Test
    fun withoutADeclaredSizeTheSlotIsTheColumnUpToTheCap() {
        val (width, _) = measure(null)
        assertEquals(490f, width, 0.5f)
    }

    @Test
    fun aRatioRememberedFromAnEarlierLoadReservesTheHeightBeforeTheImageArrives() {
        // A GIF posted as a bare link declares no size: the slot used to be 0dp tall until it
        // decoded, so the card jumped open on every load.
        assertEquals(0f, measureRatio(null).second, 0.5f)
        val (width, height) = measureRatio(500f / 280f)
        assertEquals(490f, width, 0.5f)
        assertEquals(274.4f, height, 0.5f)
    }
}
