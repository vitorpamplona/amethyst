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
package com.vitorpamplona.amethyst.commons.model.cordn

import com.vitorpamplona.cordn.spec02Envelopes.CordnCustomEmoji
import com.vitorpamplona.cordn.spec02Envelopes.CordnMessageKinds
import com.vitorpamplona.cordn.spec02Envelopes.CordnMessageReferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The app's `:name:url` palette entry becomes the reaction cordn-web reads; the rest goes as written. */
class CordnReactionOutboundTest {
    private val target = CordnMessageReferences.Target(id = "a".repeat(64), pubKey = "b".repeat(64), kind = CordnMessageKinds.TEXT)

    @Test
    fun aCustomEmojiIsSentAsItsShortcodeAndEmojiTag() {
        val out = CordnReactionOutbound.of(":blob:https://example.com/blob.png", target)

        assertEquals(CordnMessageKinds.REACTION, out.kind)
        assertEquals(":blob:", out.content)
        val emoji = CordnCustomEmoji.ofReaction(out.content, out.tags)
        assertEquals("blob", emoji?.name)
        assertEquals("https://example.com/blob.png", emoji?.url)
    }

    @Test
    fun aUnicodeEmojiGoesAsWritten() {
        val out = CordnReactionOutbound.of("🔥", target)

        assertEquals("🔥", out.content)
        assertNull(CordnCustomEmoji.ofReaction(out.content, out.tags))
    }

    @Test
    fun aShortcodeWithoutAPictureGoesAsWritten() {
        assertEquals(":blob:", CordnReactionOutbound.of(":blob:", target).content)
    }
}
