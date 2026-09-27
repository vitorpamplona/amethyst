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
package com.vitorpamplona.amethyst.commons.cordn

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CordnMentionsTest {
    private val alice = KeyPair()
    private val aliceHex = alice.pubKey.toHexKey()
    private val aliceNpub = NPub.create(aliceHex)

    /** Every segment's original text, concatenated. */
    private fun rebuild(segments: List<CordnMentions.Segment>) =
        segments.joinToString("") {
            when (it) {
                is CordnMentions.Segment.Text -> it.value
                is CordnMentions.Segment.Mention -> it.raw
            }
        }

    @Test
    fun `a message with no mentions is one run of text`() {
        val segments = CordnMentions.segment("just talking")

        assertEquals(listOf(CordnMentions.Segment.Text("just talking")), segments)
    }

    @Test
    fun `a mention is split out of the text around it`() {
        val content = "hey nostr:$aliceNpub what do you think"
        val segments = CordnMentions.segment(content)

        assertEquals(3, segments.size, "text, mention, text")
        assertEquals(aliceHex, (segments[1] as CordnMentions.Segment.Mention).pubKey)
        assertEquals(content, rebuild(segments), "segmenting must not lose a character")
    }

    @Test
    fun `a message that is only a mention has no empty text runs around it`() {
        val segments = CordnMentions.segment("nostr:$aliceNpub")

        assertEquals(1, segments.size)
        assertTrue(segments.single() is CordnMentions.Segment.Mention)
    }

    @Test
    fun `a bare npub is left as text`() {
        // Without `nostr:` this is as likely to be someone quoting a key as
        // mentioning a person, and turning it into a name changes what they
        // wrote.
        val content = "my key is $aliceNpub, check it"
        val segments = CordnMentions.segment(content)

        assertEquals(listOf(CordnMentions.Segment.Text(content)), segments)
    }

    @Test
    fun `an unparseable nostr uri stays text rather than vanishing`() {
        // The regex can match something bech32 cannot decode. Dropping it
        // would delete part of the message on screen.
        val content = "look at nostr:npub1qqqqqqqqqq here"
        val segments = CordnMentions.segment(content)

        assertEquals(content, rebuild(segments))
    }

    @Test
    fun `punctuation right after a mention is not eaten`() {
        val content = "thanks nostr:$aliceNpub!"
        val segments = CordnMentions.segment(content)

        assertEquals(content, rebuild(segments))
        assertEquals(CordnMentions.Segment.Text("!"), segments.last())
    }

    @Test
    fun `the same person mentioned twice is listed once`() {
        val mentioned = CordnMentions.mentioned("nostr:$aliceNpub and again nostr:$aliceNpub")

        assertEquals(listOf(aliceHex), mentioned)
    }
}
