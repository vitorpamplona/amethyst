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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GitPatchEventSubjectTest {
    private fun patchWith(content: String) =
        GitPatchEvent(
            id = "00",
            pubKey = "00",
            createdAt = 0,
            tags = emptyArray(),
            content = content,
            sig = "00",
        )

    @Test
    fun stripsPatchSeriesPrefix() {
        val content =
            """
            From 9e8f7a6b Mon Sep 17 00:00:00 2001
            From: Alice <alice@example.com>
            Date: Mon, 1 Jan 2024 00:00:00 +0000
            Subject: [PATCH 2/3] Fix the broken thing

            The body of the patch goes here.
            """.trimIndent()

        assertEquals("Fix the broken thing", patchWith(content).subject())
    }

    @Test
    fun handlesPlainPatchPrefix() {
        val content =
            """
            From 9e8f7a6b Mon Sep 17 00:00:00 2001
            Subject: [PATCH] Add a feature

            diff --git a/x b/x
            """.trimIndent()

        assertEquals("Add a feature", patchWith(content).subject())
    }

    @Test
    fun unfoldsContinuationLines() {
        val content =
            """
            Subject: [PATCH] A very long subject line that the mail
             formatter folded across two physical lines

            body
            """.trimIndent()

        assertEquals(
            "A very long subject line that the mail formatter folded across two physical lines",
            patchWith(content).subject(),
        )
    }

    @Test
    fun keepsSubjectWithoutBracketPrefix() {
        val content =
            """
            Subject: Just a plain subject

            body
            """.trimIndent()

        assertEquals("Just a plain subject", patchWith(content).subject())
    }

    @Test
    fun returnsNullWhenNoSubjectHeader() {
        val content =
            """
            diff --git a/x b/x
            index 000..111
            """.trimIndent()

        assertNull(patchWith(content).subject())
    }

    @Test
    fun returnsNullWhenSubjectIsEmpty() {
        assertNull(patchWith("Subject: [PATCH]   ").subject())
    }
}
