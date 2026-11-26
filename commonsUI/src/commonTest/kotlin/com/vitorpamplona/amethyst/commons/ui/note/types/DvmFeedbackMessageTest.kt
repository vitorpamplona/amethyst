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

import com.vitorpamplona.quartz.nip90Dvms.status.DvmStatusEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What the DVM screens and the home banner print for a kind-7000 feedback: the `status` tag's
 * extra info, else the content when it is text. Quartz 2.29.0 reads only the tag's extra info
 * (`StatusTag.extraInfo`), so the content fallback is the app's.
 */
class DvmFeedbackMessageTest {
    private fun feedback(
        tags: Array<Array<String>>,
        content: String = "",
    ) = DvmStatusEvent("a".repeat(64), "b".repeat(64), 1L, tags, content, "c".repeat(128))

    @Test
    fun theStatusTagsExtraInfoWins() {
        val event = feedback(arrayOf(arrayOf("status", "processing", "Fetching your feed")), "Two of ten done")
        assertEquals("Fetching your feed", event.feedbackMessage())
        assertEquals("Fetching your feed", event.feedback().extraInfo)
        assertEquals("processing", event.feedback().status)
    }

    @Test
    fun textContentStandsInForMissingExtraInfo() {
        assertEquals("Out of credits", feedback(arrayOf(arrayOf("status", "error")), "Out of credits").feedbackMessage())
        assertEquals("Out of credits", feedback(arrayOf(arrayOf("status", "error", " ")), "Out of credits").feedbackMessage())
    }

    @Test
    fun encryptedOrJsonContentIsNeverTheMessage() {
        assertNull(feedback(arrayOf(arrayOf("status", "processing"), arrayOf("encrypted")), "Zm9vYmFy?iv=YmF6").feedbackMessage())
        assertNull(feedback(arrayOf(arrayOf("status", "partial")), """[["e","${"d".repeat(64)}"]]""").feedbackMessage())
    }

    @Test
    fun noStatusTagHasNoCode() {
        assertNull(feedback(emptyArray(), "").feedback().status)
        assertNull(feedback(arrayOf(arrayOf("status", "")), "").feedback().status)
    }
}
