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
package com.vitorpamplona.amethyst.desktop.service.media

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoDiskCacheTest {
    @Test
    fun parsesTheRangesAnEngineSends() {
        assertEquals(0L..1L, VideoDiskCache.parseRange("bytes=0-1", 100))
        assertEquals(10L..99L, VideoDiskCache.parseRange("bytes=10-", 100))
        assertEquals(90L..99L, VideoDiskCache.parseRange("bytes=-10", 100))
        // An end past the file is clamped to it.
        assertEquals(50L..99L, VideoDiskCache.parseRange("bytes=50-1000", 100))
        // No Range header: the whole file.
        assertEquals(0L..99L, VideoDiskCache.parseRange(null, 100))
    }

    @Test
    fun refusesRangesItCannotServe() {
        assertNull(VideoDiskCache.parseRange("bytes=100-", 100))
        assertNull(VideoDiskCache.parseRange("bytes=5-2", 100))
        assertNull(VideoDiskCache.parseRange("bytes=0-1,5-9", 100))
        assertNull(VideoDiskCache.parseRange("items=0-1", 100))
    }

    @Test
    fun piecesWrittenApartJoinIntoTheWholeFile() {
        val dir = Files.createTempDirectory("vdc").toFile()
        try {
            val entry = VideoDiskCache.Entry(File(dir, "a.data"), File(dir, "a.meta"), 100, "video/mp4")
            val bytes = ByteArray(100) { it.toByte() }
            // The way the engine reads a file with its index at the end: start, tail, middle.
            entry.write(0, bytes.copyOfRange(0, 30), 30)
            entry.write(80, bytes.copyOfRange(80, 100), 20)
            assertTrue(entry.covers(0, 29))
            assertTrue(entry.covers(85, 99))
            assertFalse(entry.covers(0, 99))
            assertFalse(entry.covers(25, 35))
            entry.write(30, bytes.copyOfRange(30, 80), 50)
            assertTrue(entry.covers(0, 99))

            entry.persist()
            val reloaded = VideoDiskCache.Entry.load(File(dir, "a.data"), File(dir, "a.meta"))!!
            assertTrue(reloaded.covers(0, 99))
            assertEquals("video/mp4", reloaded.contentType)
            assertEquals(bytes.toList(), File(dir, "a.data").readBytes().toList())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun overlappingPiecesDoNotLeaveGaps() {
        val dir = Files.createTempDirectory("vdc").toFile()
        try {
            val entry = VideoDiskCache.Entry(File(dir, "b.data"), File(dir, "b.meta"), 50, "video/mp4")
            val bytes = ByteArray(50)
            entry.write(10, bytes, 20) // 10..29
            entry.write(0, bytes, 15) // 0..14, overlaps
            entry.write(29, bytes, 21) // 29..49, touches
            assertTrue(entry.covers(0, 49))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun aFileNotSizedUpFrontReloadsWithWhatItHolds() {
        val dir = Files.createTempDirectory("vdc").toFile()
        try {
            val data = File(dir, "c.data")
            val entry = VideoDiskCache.Entry(data, File(dir, "c.meta"), 1000, "video/mp4")
            // Only the head arrived: the file ends there, not at the video's full length.
            entry.write(0, ByteArray(100) { 7 }, 100)
            entry.persist()
            assertEquals(100L, data.length())

            val reloaded = VideoDiskCache.Entry.load(data, File(dir, "c.meta"))!!
            assertTrue(reloaded.covers(0, 99))
            assertFalse(reloaded.covers(0, 100))
            assertEquals(1000L, reloaded.total)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun theStartWithoutAGapIsWhatAPosterCanRead() {
        val dir = Files.createTempDirectory("vdc").toFile()
        try {
            val entry = VideoDiskCache.Entry(File(dir, "d.data"), File(dir, "d.meta"), 1000, "video/mp4")
            val bytes = ByteArray(1000) { it.toByte() }
            // Only the tail: nothing from the start.
            entry.write(900, bytes.copyOfRange(900, 1000), 100)
            assertEquals(0L, entry.heldFromStart())
            entry.write(0, bytes, 300)
            entry.write(500, bytes.copyOfRange(500, 600), 100)
            assertEquals(300L, entry.heldFromStart())

            val out = File(dir, "start.mp4")
            assertEquals(200L, entry.copyStart(out, 200))
            assertEquals(bytes.copyOfRange(0, 200).toList(), out.readBytes().toList())
            // Never past the gap, and a longer copy replaces the shorter one.
            assertEquals(300L, entry.copyStart(out, 10_000))
            assertEquals(bytes.copyOfRange(0, 300).toList(), out.readBytes().toList())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun aPosterWaitsForAsMuchAsItWouldDownload() {
        val chunk = 4L * 1024 * 1024
        assertFalse(VideoThumbnailCache.startIsHeld(0, 10 * chunk))
        assertFalse(VideoThumbnailCache.startIsHeld(chunk - 1, 10 * chunk))
        assertTrue(VideoThumbnailCache.startIsHeld(chunk, 10 * chunk))
        // A video smaller than that needs all of it.
        assertFalse(VideoThumbnailCache.startIsHeld(999, 1000))
        assertTrue(VideoThumbnailCache.startIsHeld(1000, 1000))
    }
}
