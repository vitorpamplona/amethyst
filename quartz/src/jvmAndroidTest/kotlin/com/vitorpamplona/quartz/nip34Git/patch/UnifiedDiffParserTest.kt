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
package com.vitorpamplona.quartz.nip34Git.patch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class UnifiedDiffParserTest {
    // A real `git format-patch -1 --stdout`: modify a.txt, delete b.txt, add c.txt.
    private val sampleB64 =
        "RnJvbSAyZTllMGZlZjZhOWZkMTZiYzdjZGNhMjY2ZmI2MGVlNTdmN2Q0NzZjIE1vbiBTZXAgMTcgMDA6MDA6MDAgMjAwMQpGcm9tOiBUZXN0ZXIgPHRAdC5jb20+CkRhdGU6IFN1biwgMjggSnVuIDIwMjYgMTU6NDc6MTkgKzAwMDAKU3ViamVjdDogW1BBVENIXSBVcGRhdGUgYS50eHQsIHJlbW92ZSBiLnR4dCwgYWRkIGMudHh0CgpUaGlzIGlzIHRoZSBib2R5IG9mIHRoZSBjb21taXQgbWVzc2FnZS4KSXQgc3BhbnMgbXVsdGlwbGUgbGluZXMuCi0tLQogYS50eHQgfCAzICsrLQogYi50eHQgfCAxIC0KIGMudHh0IHwgMiArKwogMyBmaWxlcyBjaGFuZ2VkLCA0IGluc2VydGlvbnMoKyksIDIgZGVsZXRpb25zKC0pCiBkZWxldGUgbW9kZSAxMDA2NDQgYi50eHQKIGNyZWF0ZSBtb2RlIDEwMDY0NCBjLnR4dAoKZGlmZiAtLWdpdCBhL2EudHh0IGIvYS50eHQKaW5kZXggMGMyYWEzOC4uNThlMWYxMiAxMDA2NDQKLS0tIGEvYS50eHQKKysrIGIvYS50eHQKQEAgLTEsMyArMSw0IEBACiBsaW5lIG9uZQotbGluZSB0d28KK2xpbmUgdHdvIENIQU5HRUQKIGxpbmUgdGhyZWUKK2xpbmUgZm91cgpkaWZmIC0tZ2l0IGEvYi50eHQgYi9iLnR4dApkZWxldGVkIGZpbGUgbW9kZSAxMDA2NDQKaW5kZXggMmZhOTkyYy4uMDAwMDAwMAotLS0gYS9iLnR4dAorKysgL2Rldi9udWxsCkBAIC0xICswLDAgQEAKLWtlZXAKZGlmZiAtLWdpdCBhL2MudHh0IGIvYy50eHQKbmV3IGZpbGUgbW9kZSAxMDA2NDQKaW5kZXggMDAwMDAwMC4uOTQ5NTRhYgotLS0gL2Rldi9udWxsCisrKyBiL2MudHh0CkBAIC0wLDAgKzEsMiBAQAoraGVsbG8KK3dvcmxkCi0tIAoyLjQzLjAKCg=="

    private fun sample(): String = String(Base64.getDecoder().decode(sampleB64))

    @Test
    fun extractsCommitMessageBody() {
        val parsed = UnifiedDiffParser.parse(sample())
        assertEquals("This is the body of the commit message.\nIt spans multiple lines.", parsed.message)
        assertTrue(parsed.hasDiff)
    }

    @Test
    fun parsesAllThreeFiles() {
        val files = UnifiedDiffParser.parse(sample()).files
        assertEquals(3, files.size)

        val a = files[0]
        assertEquals("a.txt", a.displayPath)
        assertEquals(GitFileChange.MODIFY, a.change)
        assertEquals(2, a.additions) // "line two CHANGED" + "line four"
        assertEquals(1, a.deletions) // "line two"

        val b = files[1]
        assertEquals("b.txt", b.displayPath)
        assertEquals(GitFileChange.DELETE, b.change)
        assertNull(b.newPath)
        assertEquals(1, b.deletions)

        val c = files[2]
        assertEquals("c.txt", c.displayPath)
        assertEquals(GitFileChange.ADD, c.change)
        assertNull(c.oldPath)
        assertEquals(2, c.additions)
    }

    @Test
    fun assignsLineNumbers() {
        val a = UnifiedDiffParser.parse(sample()).files[0]
        val hunk = a.hunks.single()
        assertEquals(1, hunk.oldStart)
        assertEquals(1, hunk.newStart)

        // context "line one" keeps both numbers
        val first = hunk.lines.first()
        assertEquals(GitDiffLineType.CONTEXT, first.type)
        assertEquals("line one", first.content)
        assertEquals(1, first.oldNumber)
        assertEquals(1, first.newNumber)

        val deleted = hunk.lines.first { it.type == GitDiffLineType.DELETE }
        assertEquals("line two", deleted.content)
        assertNull(deleted.newNumber)

        val added = hunk.lines.first { it.type == GitDiffLineType.ADD }
        assertEquals("line two CHANGED", added.content)
        assertNull(added.oldNumber)
    }

    @Test
    fun totalsAcrossFiles() {
        val parsed = UnifiedDiffParser.parse(sample())
        assertEquals(4, parsed.totalAdditions)
        assertEquals(2, parsed.totalDeletions)
    }

    @Test
    fun plainTextWithoutDiffIsMessageOnly() {
        val parsed = UnifiedDiffParser.parse("Just a description, no diff here.")
        assertFalse(parsed.hasDiff)
        assertEquals("Just a description, no diff here.", parsed.message)
        assertTrue(parsed.files.isEmpty())
    }

    @Test
    fun detectsBinaryFiles() {
        val patch =
            "diff --git a/img.png b/img.png\n" +
                "new file mode 100644\n" +
                "index 0000000..abcdef1\n" +
                "Binary files /dev/null and b/img.png differ\n"
        val file = UnifiedDiffParser.parse(patch).files.single()
        assertTrue(file.isBinary)
        assertTrue(file.hunks.isEmpty())
    }
}
