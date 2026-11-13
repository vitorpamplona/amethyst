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
package com.vitorpamplona.amethyst.commons.claudecode

import com.vitorpamplona.quartz.nip34Git.patch.UnifiedDiffParser
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ToolViewTest {
    private lateinit var dir: File

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("tool-view").toFile()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun json(vararg pairs: Pair<String, String>) = pairs.joinToString(",", "{", "}") { (k, v) -> "\"$k\":\"" + v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"" }

    @Test
    fun aDoneEditIsADiffAtTheLineItLandedOn() {
        val file = File(dir, "calc.py")
        file.writeText("import os\n\ndef add(a, b):\n    return a + b\n\ndef mul(a, b):\n    return a * b\n")
        val input = json("file_path" to file.absolutePath, "old_string" to "def add(a, b):\n    return a + b\n", "new_string" to "def add(a, b):\n    return a + b\n\ndef mul(a, b):\n    return a * b\n")

        val view = assertIs<ToolView.FileChange>(ToolViews.of("Edit", input, dir))
        assertEquals("calc.py", view.path)
        assertEquals(3, view.added)
        assertEquals(0, view.removed)
        assertTrue(view.diff.contains("@@ -3,2 +3,5 @@"), view.diff)

        // The diff view's own parser reads it.
        val parsed = UnifiedDiffParser.parse(view.diff)
        assertEquals(1, parsed.files.size)
        assertEquals(3, parsed.totalAdditions)
    }

    @Test
    fun aPendingEditIsPlacedByItsOldText() {
        val file = File(dir, "a.txt")
        file.writeText("one\ntwo\nthree\nfour\n")
        val input = json("file_path" to file.absolutePath, "old_string" to "three", "new_string" to "THREE")
        val view = assertIs<ToolView.FileChange>(ToolViews.of("Edit", input, dir, pending = true))
        assertTrue(view.diff.contains("@@ -3,1 +3,1 @@\n-three\n+THREE\n"), view.diff)
    }

    @Test
    fun aPendingWriteOverAFileIsComparedWithIt() {
        val file = File(dir, "b.txt")
        file.writeText((1..20).joinToString("\n", postfix = "\n") { "line $it" })
        val content = (1..20).joinToString("\n", postfix = "\n") { if (it == 10) "changed" else "line $it" }
        val view = assertIs<ToolView.FileChange>(ToolViews.of("Write", json("file_path" to file.absolutePath, "content" to content), dir, pending = true))
        assertEquals(1, view.added)
        assertEquals(1, view.removed)
        // Only the change and its context, not the whole file.
        assertTrue(view.diff.contains("@@ -7,7 +7,7 @@"), view.diff)
        assertTrue(!view.newFile)
    }

    @Test
    fun aNewFileIsAllAdded() {
        val view = assertIs<ToolView.FileChange>(ToolViews.of("Write", json("file_path" to File(dir, "n.txt").absolutePath, "content" to "a\nb\n"), dir, pending = true))
        assertTrue(view.newFile)
        assertTrue(view.diff.contains("--- /dev/null\n+++ b/n.txt\n@@ -0,0 +1,2 @@\n+a\n+b\n"), view.diff)
    }

    @Test
    fun otherToolsReadAsWhatTheyDo() {
        assertEquals(ToolView.Command("ls -la", "List files"), ToolViews.of("Bash", json("command" to "ls -la", "description" to "List files"), dir))
        assertEquals(ToolView.FileRead("src/x.kt", 10, 59), ToolViews.of("Read", "{\"file_path\":\"${dir.absolutePath}/src/x.kt\",\"offset\":10,\"limit\":50}", dir))
        assertEquals(ToolView.FileRead("/etc/hosts", null, null), ToolViews.of("Read", json("file_path" to "/etc/hosts"), dir))
        assertIs<ToolView.Raw>(ToolViews.of("SomethingNew", "{\"x\":1}", dir))
    }
}
