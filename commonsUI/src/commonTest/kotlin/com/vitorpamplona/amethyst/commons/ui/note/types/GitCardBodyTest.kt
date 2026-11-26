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
package com.vitorpamplona.amethyst.commons.ui.note.types

import kotlin.test.Test
import kotlin.test.assertEquals

class GitCardBodyTest {
    @Test
    fun formatPatchBodyDropsMailHeaders() {
        val message =
            """
            From 4246e71c Mon Sep 17 00:00:00 2001
            From: Alice <alice@example.com>
            Date: Mon, 1 Jan 2024 00:00:00 +0000
            Subject: [PATCH] fix the thing

            The thing broke when the list was empty.
            """.trimIndent()

        assertEquals("The thing broke when the list was empty.", formatPatchBody(message))
    }

    @Test
    fun formatPatchBodyDropsAFoldedSubject() {
        val message = "Subject: [PATCH 1/2] a subject long enough\n to fold onto\n\tmore lines\n\nBody line.\n  Indented body."
        assertEquals("Body line.\n  Indented body.", formatPatchBody(message))
    }

    @Test
    fun formatPatchBodyKeepsAMessageWithoutHeaders() {
        assertEquals("Just a message.", formatPatchBody("Just a message.\n"))
    }

    @Test
    fun stripDuplicatedSubjectDropsAMatchingLeadingHeading() {
        assertEquals("Body text", stripDuplicatedSubject("# Fix the Thing\n\nBody text", "fix the thing"))
        assertEquals("Body text", stripDuplicatedSubject("  ## Fix the thing \nBody text", "Fix the thing"))
    }

    @Test
    fun stripDuplicatedSubjectKeepsOtherContent() {
        assertEquals("# Another title\n\nBody", stripDuplicatedSubject("# Another title\n\nBody", "Fix the thing"))
        assertEquals("Fix the thing\n\nBody", stripDuplicatedSubject("Fix the thing\n\nBody", "Fix the thing"))
        assertEquals("# Fix\n\nBody", stripDuplicatedSubject("# Fix\n\nBody", null))
        assertEquals("# Fix\n\nBody", stripDuplicatedSubject("# Fix\n\nBody", "  "))
    }

    @Test
    fun stripDuplicatedSubjectOfAHeadingOnlyBodyIsEmpty() {
        assertEquals("", stripDuplicatedSubject("# Fix the thing", "Fix the thing"))
    }
}
