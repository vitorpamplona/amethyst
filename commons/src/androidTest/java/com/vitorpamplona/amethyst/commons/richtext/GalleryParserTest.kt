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
package com.vitorpamplona.amethyst.commons.richtext

import android.R.attr.text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.nip01Core.core.EmptyTagList
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GalleryParserTest {
    @Test
    fun testMixedImageAndVideoRenderedIndividually() {
        // Test the bug: when mixed image + video, both should be rendered individually
        val text =
            "Renfield (2023)\n" +
                "https://image.tmdb.org/t/p/original/ekfIcBvqfqKbI6m227NFipBNh7O.jpg\n" +
                "https://archive.org/download/cinema-horror-sci-fi/Renfield.2023.ia.mp4"

        val state = RichTextParser().parseText(text, EmptyTagList, null)

        // Should have 2 image segments (image + video URLs)
        Assert.assertEquals(2, state.paragraphs.size)

        Assert.assertTrue(state.paragraphs[0] !is ImageGalleryParagraph)
        Assert.assertEquals(1, state.paragraphs[0].words.size)
        Assert.assertTrue(state.paragraphs[1] is ImageGalleryParagraph)
        Assert.assertEquals("https://image.tmdb.org/t/p/original/ekfIcBvqfqKbI6m227NFipBNh7O.jpg", state.paragraphs[1].words[0].segmentText)
        Assert.assertEquals("https://archive.org/download/cinema-horror-sci-fi/Renfield.2023.ia.mp4", state.paragraphs[1].words[1].segmentText)
    }

    @Test
    fun testMixedImageAndVideoRenderedIndividuallyDoubled() {
        // Test the bug: when mixed image + video, both should be rendered individually
        val text =
            "Renfield (2023)\n" +
                "https://image.tmdb.org/t/p/original/ekfIcBvqfqKbI6m227NFipBNh7O.jpg\n" +
                "https://archive.org/download/cinema-horror-sci-fi/Renfield.2023.ia.mp4\n" +
                "Renfield (2023)\n" +
                "https://image.tmdb.org/t/p/original/ekfIcBvqfqKbI6m227NFipBNh7O.jpg\n" +
                "https://archive.org/download/cinema-horror-sci-fi/Renfield.2023.ia.mp4"

        val state = RichTextParser().parseText(text, EmptyTagList, null)

        // Should have 2 image segments (image + video URLs)
        Assert.assertEquals(4, state.paragraphs.size)

        Assert.assertTrue(state.paragraphs[0] !is ImageGalleryParagraph)
        Assert.assertEquals(1, state.paragraphs[0].words.size)
        Assert.assertTrue(state.paragraphs[1] is ImageGalleryParagraph)
        Assert.assertEquals("https://image.tmdb.org/t/p/original/ekfIcBvqfqKbI6m227NFipBNh7O.jpg", state.paragraphs[1].words[0].segmentText)
        Assert.assertEquals("https://archive.org/download/cinema-horror-sci-fi/Renfield.2023.ia.mp4", state.paragraphs[1].words[1].segmentText)
        Assert.assertTrue(state.paragraphs[2] !is ImageGalleryParagraph)
        Assert.assertEquals(1, state.paragraphs[2].words.size)
        Assert.assertTrue(state.paragraphs[3] is ImageGalleryParagraph)
        Assert.assertEquals("https://image.tmdb.org/t/p/original/ekfIcBvqfqKbI6m227NFipBNh7O.jpg", state.paragraphs[3].words[0].segmentText)
        Assert.assertEquals("https://archive.org/download/cinema-horror-sci-fi/Renfield.2023.ia.mp4", state.paragraphs[3].words[1].segmentText)
    }

    @Test
    fun testMultipleImagesRenderedAsGallery() {
        // Test that multiple images (no videos) are correctly grouped as gallery
        val text =
            "Gallery:\n" +
                "https://example.com/image1.jpg\n" +
                "https://example.com/image2.png"

        val state = RichTextParser().parseText(text, EmptyTagList, null)

        // Should have 2 image segments (image + video URLs)
        Assert.assertEquals(2, state.paragraphs.size)

        // Should render as gallery (1 gallery with 2 images)
        Assert.assertTrue("Should render 1 gallery", state.paragraphs[1] is ImageGalleryParagraph)
        Assert.assertEquals("Gallery should contain 2 images", 2, state.paragraphs[1].words.size)
        Assert.assertEquals("https://example.com/image1.jpg", state.paragraphs[1].words[0].segmentText)
        Assert.assertEquals("https://example.com/image2.png", state.paragraphs[1].words[1].segmentText)
    }

    @Test
    fun testMultipleImagesInAParagraphRenderedAsGallery() {
        // Test that multiple images (no videos) are correctly grouped as gallery
        val text =
            "Gallery:\n" +
                "https://example.com/image1.jpg https://example.com/image2.png"

        val state = RichTextParser().parseText(text, EmptyTagList, null)

        // Should have 2 image segments (image + video URLs)
        Assert.assertEquals(2, state.paragraphs.size)

        // Should render as gallery (1 gallery with 2 images)
        Assert.assertTrue("Should render 1 gallery", state.paragraphs[1] is ImageGalleryParagraph)
        Assert.assertEquals("Gallery should contain 2 images", 2, state.paragraphs[1].words.size)
        Assert.assertEquals("https://example.com/image1.jpg", state.paragraphs[1].words[0].segmentText)
        Assert.assertEquals("https://example.com/image2.png", state.paragraphs[1].words[1].segmentText)
    }

    @Test
    fun testSingleImageRenderedIndividually() {
        // Test that a single image is rendered individually, not as gallery
        val text = "Single image:\nhttps://example.com/image.jpg"

        val state = RichTextParser().parseText(text, EmptyTagList, null)

        // Should render individually (not as gallery)
        Assert.assertTrue("Should render 1 individual image", state.paragraphs[1] !is ImageGalleryParagraph)
        Assert.assertTrue("Should not render as gallery", state.paragraphs[0] !is ImageGalleryParagraph)
        Assert.assertEquals("https://example.com/image.jpg", state.paragraphs[1].words[0].segmentText)
    }

    @Test
    fun testGigiMessage() {
        val text = "I played The Typing of the Dead a lot when I was younger, and now I just found out that there's a new take on it: The Last Sentence. Tempted! https://relay.dergigi.com/d6a3e33b101fe219ef251ac6261c10392c2af9918c3c252d4c202016b0b4ec83.jpg https://relay.dergigi.com/d60c9c562912573f214c2b1958cc20bf8913cd718d2ed9e020621e3e3120634b.jpg"

        val state = RichTextParser().parseText(text, EmptyTagList, null)

        // Should render individually (not as gallery)
        Assert.assertEquals("I played The Typing of the Dead a lot when I was younger, and now I just found out that there's a new take on it: The Last Sentence. Tempted!", state.paragraphs[0].words[0].segmentText)
        Assert.assertTrue("Should render as gallery", state.paragraphs[1] is ImageGalleryParagraph)
        Assert.assertEquals("https://relay.dergigi.com/d6a3e33b101fe219ef251ac6261c10392c2af9918c3c252d4c202016b0b4ec83.jpg", state.paragraphs[1].words[0].segmentText)
        Assert.assertEquals("https://relay.dergigi.com/d60c9c562912573f214c2b1958cc20bf8913cd718d2ed9e020621e3e3120634b.jpg", state.paragraphs[1].words[1].segmentText)
    }
}
