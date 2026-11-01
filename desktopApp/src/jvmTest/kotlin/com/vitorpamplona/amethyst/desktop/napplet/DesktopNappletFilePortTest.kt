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
package com.vitorpamplona.amethyst.desktop.napplet

import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFsError
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFsException
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFsWriteMode
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The desktop's bytes behind NAP-FS, on real files. The pickers themselves need a person. */
class DesktopNappletFilePortTest {
    private val dir = Files.createTempDirectory("napfs").toFile()
    private val port = DesktopNappletFilePort()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun readsRanges() =
        runBlocking {
            val file = File(dir, "photo.png").apply { writeBytes(ByteArray(100) { it.toByte() }) }
            assertEquals(100L, port.size(file.absolutePath))
            val tail = port.read(file.absolutePath, 90, 64)
            assertContentEquals(ByteArray(10) { (90 + it).toByte() }, tail.bytes)
            assertEquals(100L, tail.size)
            assertEquals(0, port.read(file.absolutePath, 100, 64).bytes.size)
            assertEquals(NappletFsError.NOT_FOUND, assertFailsWith<NappletFsException> { port.read(File(dir, "gone").absolutePath, 0, 1) }.code)
        }

    @Test
    fun writesReplaceAppendAndPatch() =
        runBlocking {
            val file = File(dir, "export.png").apply { writeText("an old, longer file") }
            assertEquals(5L, port.write(file.absolutePath, "Hello".encodeToByteArray(), NappletFsWriteMode.Replace))
            assertEquals(11L, port.write(file.absolutePath, " world".encodeToByteArray(), NappletFsWriteMode.Append))
            assertEquals(11L, port.write(file.absolutePath, "W".encodeToByteArray(), NappletFsWriteMode.Patch(6)))
            assertEquals("Hello World", file.readText())
            // The replace went through a file beside it that is gone again.
            assertFalse(File(dir, ".export.png.part").exists())
            assertEquals(listOf("export.png"), dir.list()!!.toList())
        }

    @Test
    fun replaceKeepsASymlink() =
        runBlocking {
            val real = File(dir, "real.png").apply { writeText("old") }
            val link = File(dir, "latest.png").toPath()
            // Windows makes links only with Developer Mode or an elevated shell.
            val linked = runCatching { Files.createSymbolicLink(link, real.toPath()) }.isSuccess
            assumeTrue("This file system or user cannot make symbolic links", linked)
            port.write(link.toString(), "new".encodeToByteArray(), NappletFsWriteMode.Replace)
            assertTrue(Files.isSymbolicLink(link))
            assertEquals("new", real.readText())
        }

    @Test
    fun replaceKeepsThePermissions() =
        runBlocking {
            // NTFS has no POSIX permissions to keep.
            assumeTrue("No POSIX permissions on this file system", "posix" in dir.toPath().fileSystem.supportedFileAttributeViews())
            val file = File(dir, "real.png").apply { writeText("old") }
            Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-r-----"))
            port.write(file.absolutePath, "new".encodeToByteArray(), NappletFsWriteMode.Replace)
            assertEquals("new", file.readText())
            assertEquals("rw-r-----", PosixFilePermissions.toString(Files.getPosixFilePermissions(file.toPath())))
        }

    @Test
    fun replaceWritesInPlaceWhereNoFileCanBeMadeBesideIt() =
        runBlocking {
            val file = File(dir, "out.txt").apply { writeText("a much longer old text") }
            // Something already holds the name the new bytes would be staged under.
            assertTrue(File(dir, ".out.txt.part").mkdir())
            assertEquals(5L, port.write(file.absolutePath, "short".encodeToByteArray(), NappletFsWriteMode.Replace))
            assertEquals("short", file.readText())
        }

    @Test
    fun replaceCreatesANewFile() =
        runBlocking {
            val file = File(dir, "new.txt")
            assertEquals(2L, port.write(file.absolutePath, "hi".encodeToByteArray(), NappletFsWriteMode.Replace))
            assertEquals("hi", file.readText())
        }
}
