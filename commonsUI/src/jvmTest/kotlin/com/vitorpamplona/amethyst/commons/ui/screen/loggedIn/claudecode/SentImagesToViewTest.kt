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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.claudecode

import com.vitorpamplona.amethyst.commons.claudecode.SentImage
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What a click on a sent image opens: the message's images still on disk, the clicked one first shown. */
class SentImagesToViewTest {
    private val dir = Files.createTempDirectory("sent-images").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun image(name: String): SentImage = SentImage(name, File(dir, name).also { it.writeBytes(byteArrayOf(1)) }.absolutePath)

    @Test
    fun theClickedImageIsShownAmongTheMessagesImages() {
        val images = listOf(image("a.png"), image("b.png"), image("c.png"))
        val (all, shown) = sentImagesToView(images, 1)!!
        assertEquals(3, all.size)
        assertEquals(File(dir, "b.png").toURI().toString(), (shown as MediaUrlImage).url)
        assertEquals("b.png", shown.description)
    }

    @Test
    fun imagesWhoseFileIsGoneAreLeftOut() {
        val gone = SentImage("gone.png", File(dir, "gone.png").absolutePath)
        val images = listOf(image("a.png"), gone, SentImage(null, null), image("c.png"))
        val (all, shown) = sentImagesToView(images, 3)!!
        assertEquals(listOf("a.png", "c.png"), all.map { it.description })
        assertEquals("c.png", shown.description)
        assertNull(sentImagesToView(images, 1))
        assertNull(sentImagesToView(images, 2))
    }
}
