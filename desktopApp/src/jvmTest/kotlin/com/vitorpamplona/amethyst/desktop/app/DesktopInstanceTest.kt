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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopInstanceTest {
    private val root: File = Files.createTempDirectory("instances").toFile()
    private val slots = File(root, "slots")

    // The claims this test holds, like the copies it stands in for: released after each test.
    private val held = mutableListOf<DesktopInstance.Claim>()

    private fun claim() = DesktopInstance.tryClaim(slots).also { held += it }

    @AfterTest
    fun cleanup() {
        held.forEach { claim ->
            claim.lock?.let {
                it.release()
                it.channel().close()
            }
        }
        root.deleteRecursively()
    }

    @Test
    fun eachCopyClaimsTheLowestFreeSlot() {
        // A slot this process already holds counts as taken, as one another copy holds would.
        assertEquals(listOf(1, 2, 3), List(3) { claim().slot })
    }

    @Test
    fun aSlotIsFreeAgainOnceItsCopyLetsGo() {
        claim()
        val second = claim()
        second.lock!!.release()
        second.lock.channel().close()
        held.remove(second)

        assertEquals(2, claim().slot)
    }

    @Test
    fun withoutALockFolderTheCopyIsNeverThePrimary() {
        // A file where the folder should be: it cannot be created.
        val blocked = File(root, "blocked").apply { writeText("not a folder") }
        val claim = DesktopInstance.tryClaim(blocked)

        assertEquals(DesktopInstance.OVERFLOW_SLOT, claim.slot)
        assertNull(claim.lock)
    }

    @Test
    fun removesOnlyTheCachesOfSlotsNoCopyHolds() {
        val videos = File(root, "videos").apply { mkdirs() }
        val running = File(root, "videos-2").apply { mkdirs() }
        val idle = File(root, "videos-3").apply { mkdirs() }
        File(idle, "chunk").writeText("stale")

        // Slots 1 and 2 are taken; 3 is free.
        claim()
        claim()
        DesktopInstance.removeIdleCopies(slots, listOf(videos))

        assertTrue(videos.isDirectory, "the primary's folder is never touched")
        assertTrue(running.isDirectory, "a running copy keeps its folder")
        assertFalse(idle.exists(), "an idle slot's folder goes")
        // Its lock was given back: the next copy can still take slot 3.
        assertEquals(3, claim().slot)
    }

    @Test
    fun thePrimaryKeepsTheUsualFolderAndLaterCopiesGetTheirOwn() {
        val videos = File(root, "videos")
        assertEquals(videos, DesktopInstance.folderFor(videos, 1))
        assertEquals(File(root, "videos-2"), DesktopInstance.folderFor(videos, 2))
        assertEquals(File(root, "videos-9"), DesktopInstance.folderFor(videos, DesktopInstance.OVERFLOW_SLOT))
    }
}
