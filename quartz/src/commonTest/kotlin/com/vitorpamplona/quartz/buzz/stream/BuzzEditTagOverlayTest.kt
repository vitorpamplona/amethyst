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
package com.vitorpamplona.quartz.buzz.stream

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Ports the cases in Buzz's `applyEditTagOverlay.test.mjs`. */
class BuzzEditTagOverlayTest {
    private fun imeta(url: String) = arrayOf("imeta", "url $url", "m image/png", "x x", "size 1")

    private fun emoji(
        shortcode: String,
        url: String,
    ) = arrayOf("emoji", shortcode, url)

    private fun overlay(
        original: Array<Array<String>>,
        edit: Array<Array<String>>,
    ) = BuzzEditTagOverlay.apply(original, edit).map { it.toList() }

    private fun List<List<String>>.named(name: String) = filter { it[0] == name }

    @Test
    fun noEditIsAPassThrough() {
        val tags = arrayOf(arrayOf("h", "uuid"), imeta("https://b/a.png"))
        assertSame(tags, BuzzEditTagOverlay.apply(tags, null))
    }

    @Test
    fun editReplacesImetaAndKeepsOriginalNonImeta() {
        val out =
            overlay(
                arrayOf(arrayOf("h", "uuid"), arrayOf("p", "mention1"), imeta("https://b/a.png"), imeta("https://b/b.png")),
                arrayOf(arrayOf("h", "uuid"), arrayOf("e", "orig"), imeta("https://b/a.png"), imeta("https://b/c.png")),
            )
        assertEquals(listOf(listOf("h", "uuid"), listOf("p", "mention1")), out.filter { it[0] != "imeta" })
        assertEquals(listOf("url https://b/a.png", "url https://b/c.png"), out.named("imeta").map { it[1] })
    }

    @Test
    fun editWithoutImetaStripsAttachments() {
        val out = overlay(arrayOf(arrayOf("h", "uuid"), imeta("https://b/a.png")), arrayOf(arrayOf("h", "uuid"), arrayOf("e", "x")))
        assertTrue(out.named("imeta").isEmpty())
        assertEquals(1, out.named("h").size)
    }

    @Test
    fun editsOwnChannelAndTargetTagsNeverLeak() {
        val out =
            overlay(
                arrayOf(arrayOf("h", "uuid-original"), arrayOf("p", "mention1")),
                arrayOf(arrayOf("h", "uuid-from-edit"), arrayOf("e", "target"), imeta("https://b/a.png")),
            )
        assertEquals(listOf(listOf("h", "uuid-original")), out.named("h"))
        assertTrue(out.named("e").isEmpty())
        assertEquals(1, out.named("imeta").size)
    }

    @Test
    fun addedMentionsJoinTheOriginalOnes() {
        val out = overlay(arrayOf(arrayOf("h", "uuid"), arrayOf("p", "original")), arrayOf(arrayOf("h", "uuid"), arrayOf("e", "x"), arrayOf("p", "added")))
        assertEquals(listOf(listOf("p", "original"), listOf("p", "added")), out.named("p"))
    }

    @Test
    fun mentionSnapshotReplacesReferencesButKeepsAgentAddress() {
        val original = arrayOf(arrayOf("h", "uuid"), arrayOf("mention", "agent", "agent-address"), arrayOf("mention", "old"))
        val out = overlay(original, arrayOf(arrayOf("buzz:mention-snapshot"), arrayOf("mention", "agent", "agent-address"), arrayOf("mention", "new")))
        assertEquals(listOf(listOf("mention", "agent", "agent-address"), listOf("mention", "new")), out.named("mention"))

        val removed = overlay(arrayOf(arrayOf("h", "uuid"), arrayOf("mention", "old")), arrayOf(arrayOf("buzz:mention-snapshot")))
        assertTrue(removed.named("mention").isEmpty())

        val legacy = overlay(arrayOf(arrayOf("h", "uuid"), arrayOf("mention", "old")), arrayOf(arrayOf("h", "uuid"), arrayOf("e", "x")))
        assertEquals(listOf(listOf("mention", "old")), legacy.named("mention"))
    }

    @Test
    fun emojiComesFromTheEditOnlyWhenItHasSome() {
        val original = arrayOf(arrayOf("h", "uuid"), imeta("https://b/a.png"), emoji("catjam", "https://b/catjam.gif"))

        val replaced = overlay(original, arrayOf(arrayOf("e", "x"), emoji("catjam", "https://b/catjam.gif"), emoji("rickroll", "https://b/rr.gif")))
        assertEquals(listOf("catjam", "rickroll"), replaced.named("emoji").map { it[1] })

        // A tag-less edit keeps the emoji but still clears the attachments.
        val tagless = overlay(original, arrayOf(arrayOf("h", "uuid"), arrayOf("e", "x")))
        assertEquals(listOf(listOf("emoji", "catjam", "https://b/catjam.gif")), tagless.named("emoji"))
        assertTrue(tagless.named("imeta").isEmpty())
    }
}
