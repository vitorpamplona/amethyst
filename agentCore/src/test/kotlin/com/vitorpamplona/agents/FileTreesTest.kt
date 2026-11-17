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
package com.vitorpamplona.agents

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileTreesTest {
    private val tmp: File = Files.createTempDirectory("filetrees").toFile()

    @AfterTest
    fun cleanUp() {
        deleteTree(tmp)
    }

    @Test
    fun aLinkGoesButNotWhatItPointsTo() {
        val outside = File(tmp, "outside").apply { mkdirs() }
        val precious = File(outside, "keep.txt").apply { writeText("mine") }
        val workspace = File(tmp, "workspace").apply { mkdirs() }
        File(workspace, "a/b").mkdirs()
        File(workspace, "a/b/file.txt").writeText("x")
        Files.createSymbolicLink(File(workspace, "a/home").toPath(), outside.toPath())
        Files.createSymbolicLink(File(workspace, "linked-file").toPath(), precious.toPath())

        assertTrue(deleteTree(workspace))

        assertFalse(workspace.exists())
        assertTrue(precious.isFile, "the link's target must survive")
        assertTrue(precious.canWrite())
    }

    @Test
    fun readOnlyFilesGoToo() {
        val dir = File(tmp, "repo/objects/pack").apply { mkdirs() }
        File(dir, "pack-1.pack").apply {
            writeText("p")
            setWritable(false)
        }
        assertTrue(deleteTree(File(tmp, "repo")))
        assertFalse(File(tmp, "repo").exists())
    }

    @Test
    fun aRootThatIsALinkIsRemovedAlone() {
        val target = File(tmp, "target").apply { mkdirs() }
        File(target, "keep.txt").writeText("mine")
        val link = File(tmp, "link")
        Files.createSymbolicLink(link.toPath(), target.toPath())
        assertTrue(deleteTree(link))
        assertTrue(File(target, "keep.txt").isFile)
    }

    @Test
    fun nothingThereIsDone() {
        assertTrue(deleteTree(File(tmp, "never")))
    }
}
