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
package com.vitorpamplona.amethyst.commons.model.marmotGroups

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.verifyId
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The send-side `p` tags for inline mentions, for the paths that have no composer tagger. */
class MarmotMentionTagsTest {
    private val carolKey = KeyPair().pubKey
    private val daveKey = KeyPair().pubKey
    private val carol = carolKey.toHexKey()
    private val dave = daveKey.toHexKey()

    @Test
    fun everyInlineFormIsTaggedOnce() {
        val text = "hi nostr:${carolKey.toNpub()} and @${daveKey.toNpub()}, again nostr:${carolKey.toNpub()}"
        assertEquals(listOf(carol, dave), MarmotMentionTags.of(text).map { it.pubKey })
    }

    @Test
    fun aCaptionRumorGainsItsMissingTagsAndANewId() {
        val rumor = RumorAssembler.assembleRumor<Event>(dave, 1_000, ChatEvent.KIND, arrayOf(arrayOf("p", dave)), "look nostr:${carolKey.toNpub()} nostr:${daveKey.toNpub()}")

        val tagged = MarmotMentionTags.added(rumor)

        assertEquals(listOf(dave, carol), tagged.tags.filter { it[0] == "p" }.map { it[1] })
        assertEquals(rumor.content, tagged.content)
        assertTrue(tagged.id != rumor.id && tagged.verifyId())
    }

    @Test
    fun aRumorWithNothingToAddIsKept() {
        val rumor = RumorAssembler.assembleRumor<Event>(dave, 1_000, ChatEvent.KIND, emptyArray(), "no one")
        assertSame(rumor, MarmotMentionTags.added(rumor))
    }
}
