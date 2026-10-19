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

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopInstanceTest {
    private val root: File = Files.createTempDirectory("instances").toFile()

    @AfterTest
    fun cleanup() {
        root.deleteRecursively()
    }

    @Test
    fun eachCopyClaimsTheLowestFreeSlot() {
        // This process stands in for every copy: a slot it already holds counts as taken.
        val slots = List(3) { DesktopInstance.claimSlot(root) }
        assertEquals(listOf(1, 2, 3), slots)
    }

    @Test
    fun thePrimaryKeepsTheUsualFolderAndLaterCopiesGetTheirOwn() {
        val videos = File(root, "videos")
        assertEquals(videos, DesktopInstance.folderFor(videos, 1))
        assertEquals(File(root, "videos-2"), DesktopInstance.folderFor(videos, 2))
        assertEquals(File(root, "videos-9"), DesktopInstance.folderFor(videos, 9))
    }
}
