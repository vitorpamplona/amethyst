/**
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
package com.vitorpamplona.amethyst

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.amethyst.commons.richtext.ImageSegment
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.richtext.Segment
import com.vitorpamplona.amethyst.ui.components.ParagraphParser
import com.vitorpamplona.amethyst.ui.components.RenderContext
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.EmptyTagList
import io.mockk.mockk
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ParagraphParserTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testMixedImageAndVideoRenderedIndividually() {
        // Test the bug: when mixed image + video, both should be rendered individually
        val text =
            "Renfield (2023)\n" +
                "https://image.tmdb.org/t/p/original/ekfIcBvqfqKbI6m227NFipBNh7O.jpg\n" +
                "https://archive.org/download/cinema-horror-sci-fi/Renfield.2023.ia.mp4"

        val state = RichTextParser().parseText(text, EmptyTagList, null)

        // Extract the image segments from the parsed paragraphs
        val imageSegments = mutableListOf<ImageSegment>()
        state.paragraphs.forEach { paragraph ->
            paragraph.words.forEach { word ->
                if (word is ImageSegment) {
                    imageSegments.add(word)
                }
            }
        }

        // Should have 2 image segments (image + video URLs)
        assertEquals(2, imageSegments.size)

        // Track what gets rendered
        val singleWordRenders = mutableListOf<Segment>()
        val galleryRenders = mutableListOf<List<MediaUrlImage>>()

        // Set up the test with mocked dependencies
        val context =
            RenderContext(
                state = state,
                backgroundColor = mutableStateOf(Color.White),
                quotesLeft = 3,
                callbackUri = null,
                accountViewModel = mockk<AccountViewModel>(relaxed = true),
                nav = mockk<INav>(relaxed = true),
            )

        // Execute the actual ParagraphParser method
        composeTestRule.setContent {
            ParagraphParser().ProcessWordsWithImageGrouping(
                words = imageSegments.toImmutableList(),
                context = context,
                renderSingleWord = { segment, _ ->
                    singleWordRenders.add(segment)
                },
                renderGallery = { images, _ ->
                    galleryRenders.add(images)
                },
            )
        }

        composeTestRule.waitForIdle()

        assertTrue(
            "Mixed image/video should be rendered individually (2 renders), not as gallery (0 renders). " +
                "Found: $singleWordRenders individual, $galleryRenders gallery",
            singleWordRenders.size == 2 && galleryRenders.isEmpty(),
        )
    }

    @Test
    fun testMultipleImagesRenderedAsGallery() {
        // Test that multiple images (no videos) are correctly grouped as gallery
        val text =
            "Gallery:\n" +
                "https://example.com/image1.jpg\n" +
                "https://example.com/image2.png"

        val state = RichTextParser().parseText(text, EmptyTagList, null)

        // Extract the image segments
        val imageSegments = mutableListOf<ImageSegment>()
        state.paragraphs.forEach { paragraph ->
            paragraph.words.forEach { word ->
                if (word is ImageSegment) {
                    imageSegments.add(word)
                }
            }
        }

        assertEquals(2, imageSegments.size)

        // Track renders
        val singleWordRenders = mutableListOf<Segment>()
        val galleryRenders = mutableListOf<List<MediaUrlImage>>()

        val context =
            RenderContext(
                state = state,
                backgroundColor = mutableStateOf(Color.White),
                quotesLeft = 3,
                callbackUri = null,
                accountViewModel = mockk<AccountViewModel>(relaxed = true),
                nav = mockk<INav>(relaxed = true),
            )

        composeTestRule.setContent {
            ParagraphParser().ProcessWordsWithImageGrouping(
                words = imageSegments.toImmutableList(),
                context = context,
                renderSingleWord = { segment, _ ->
                    singleWordRenders.add(segment)
                },
                renderGallery = { images, _ ->
                    galleryRenders.add(images)
                },
            )
        }

        composeTestRule.waitForIdle()

        // Should render as gallery (1 gallery with 2 images)
        assertEquals("Should render 1 gallery", 1, galleryRenders.size)
        assertEquals("Gallery should contain 2 images", 2, galleryRenders[0].size)
        assertEquals("Should not render individually", 0, singleWordRenders.size)
    }

    @Test
    fun testSingleImageRenderedIndividually() {
        // Test that a single image is rendered individually, not as gallery
        val text = "Single image:\nhttps://example.com/image.jpg"

        val state = RichTextParser().parseText(text, EmptyTagList, null)

        val imageSegments = mutableListOf<ImageSegment>()
        state.paragraphs.forEach { paragraph ->
            paragraph.words.forEach { word ->
                if (word is ImageSegment) {
                    imageSegments.add(word)
                }
            }
        }

        assertEquals(1, imageSegments.size)

        val singleWordRenders = mutableListOf<Segment>()
        val galleryRenders = mutableListOf<List<MediaUrlImage>>()

        val context =
            RenderContext(
                state = state,
                backgroundColor = mutableStateOf(Color.White),
                quotesLeft = 3,
                callbackUri = null,
                accountViewModel = mockk<AccountViewModel>(relaxed = true),
                nav = mockk<INav>(relaxed = true),
            )

        composeTestRule.setContent {
            ParagraphParser().ProcessWordsWithImageGrouping(
                words = imageSegments.toImmutableList(),
                context = context,
                renderSingleWord = { segment, _ ->
                    singleWordRenders.add(segment)
                },
                renderGallery = { images, _ ->
                    galleryRenders.add(images)
                },
            )
        }

        composeTestRule.waitForIdle()

        // Should render individually (not as gallery)
        assertEquals("Should render 1 individual image", 1, singleWordRenders.size)
        assertEquals("Should not render as gallery", 0, galleryRenders.size)
    }
}
