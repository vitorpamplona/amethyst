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
package com.vitorpamplona.amethyst.desktop.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.ui.components.ImageViewerDialog
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The shared touch viewer iOS opens on an image: it composes over the window, and its close button
 * ends the viewer (after the shrink-back) even when the image never loads, as offline here.
 */
@OptIn(ExperimentalTestApi::class)
class ImageViewerDialogUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun closeEndsTheViewerWithoutALoadedImage() {
        val first = MediaUrlImage("https://example.invalid/a.jpg")
        val second = MediaUrlImage("https://example.invalid/b.jpg")
        var dismissed = false

        compose.setContent {
            ImageViewerDialog(first, persistentListOf(first, second), source = null, sourceCornerRadius = 0.dp) { dismissed = true }
        }

        compose.onNodeWithContentDescription("Close").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()

        assertTrue(dismissed, "the close button dismisses the viewer")
    }
}
