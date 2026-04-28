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
package com.vitorpamplona.quartz.nip30CustomEmoji

import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.description
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.image
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmojiPackEventBuildTest {
    @Test
    fun buildIncludesTitleDTagAltAndEmojis() {
        val template =
            EmojiPackEvent.build(name = "My Pack", dTag = "my-pack") {
                description("A test pack")
                image("https://example.com/cover.jpg")
                emoji("smile", "https://example.com/smile.png")
            }

        assertEquals(EmojiPackEvent.KIND, template.kind)
        assertEquals("", template.content)

        val tagsByName = template.tags.groupBy { it[0] }
        assertTrue(tagsByName.containsKey("d"))
        assertEquals("my-pack", tagsByName["d"]!!.first()[1])
        assertTrue(tagsByName.containsKey("title"))
        assertEquals("My Pack", tagsByName["title"]!!.first()[1])
        assertTrue(tagsByName.containsKey("description"))
        assertEquals("A test pack", tagsByName["description"]!!.first()[1])
        assertTrue(tagsByName.containsKey("image"))
        assertEquals("https://example.com/cover.jpg", tagsByName["image"]!!.first()[1])
        assertTrue(tagsByName.containsKey("alt"))
        assertTrue(tagsByName.containsKey("emoji"))
        assertEquals("smile", tagsByName["emoji"]!!.first()[1])
        assertEquals("https://example.com/smile.png", tagsByName["emoji"]!!.first()[2])
    }

    @Test
    fun emojiTagUsesEventTypedBuilder() {
        // Regression: EmojiPackEvent.build previously used TagArrayBuilder<GitRepositoryEvent>,
        // which caused callers to get a template of the wrong type. This test locks in the fix.
        val template = EmojiPackEvent.build(name = "test")
        assertEquals(EmojiPackEvent.KIND, template.kind)
    }

    @Test
    fun titleDescriptionImageAccessorsReadTags() {
        val event =
            EmojiPackEvent(
                id = "00",
                pubKey = "00",
                createdAt = 0L,
                tags =
                    arrayOf(
                        arrayOf("d", "mypack"),
                        arrayOf("title", "My Pack"),
                        arrayOf("description", "desc"),
                        arrayOf("image", "https://example.com/cover.png"),
                        arrayOf("emoji", "smile", "https://example.com/smile.png"),
                    ),
                content = "",
                sig = "00",
            )

        assertEquals("My Pack", event.title())
        assertEquals("desc", event.description())
        assertEquals("https://example.com/cover.png", event.image())
        assertEquals("My Pack", event.titleOrName())
        assertEquals(1, event.taggedEmojis().size)
        assertEquals("smile", event.taggedEmojis().first().code)
    }
}
