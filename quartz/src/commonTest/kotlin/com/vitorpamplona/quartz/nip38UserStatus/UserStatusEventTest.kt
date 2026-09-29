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
package com.vitorpamplona.quartz.nip38UserStatus

import com.vitorpamplona.quartz.nip30CustomEmoji.taggedEmojis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserStatusEventTest {
    private fun status(
        content: String,
        vararg tags: Array<String>,
    ) = UserStatusEvent("00", "00", 0L, arrayOf(arrayOf("d", "general"), *tags), content, "00")

    /** Buzz's `build_user_status` writes a plain emoji as a 2-element `["emoji", <emoji>]`. */
    @Test
    fun readsBuzzTwoElementStatusEmoji() {
        val event = status("In a meeting", arrayOf("emoji", "📅"))
        assertEquals("📅", event.statusEmoji())
        // It is not a NIP-30 custom emoji, so it must not leak into that list.
        assertTrue(event.taggedEmojis().isEmpty())
        assertFalse(event.isCleared())
    }

    @Test
    fun nip30CustomEmojiIsNotAStatusEmoji() {
        val event = status("hi :soapbox:", arrayOf("emoji", "soapbox", "https://example.com/soapbox.png"))
        assertNull(event.statusEmoji())
        assertEquals(listOf("soapbox"), event.taggedEmojis().map { it.code })
    }

    @Test
    fun bothShapesCanCoexist() {
        val event =
            status(
                "hi :soapbox:",
                arrayOf("emoji", "soapbox", "https://example.com/soapbox.png"),
                arrayOf("emoji", "🌴"),
            )
        assertEquals("🌴", event.statusEmoji())
        assertEquals(listOf("soapbox"), event.taggedEmojis().map { it.code })
    }

    @Test
    fun anEmojiOnlyStatusIsNotCleared() {
        assertFalse(status("", arrayOf("emoji", "🌴")).isCleared())
        assertTrue(status("").isCleared())
        assertTrue(status("  ", arrayOf("emoji", " ")).isCleared())
    }
}
