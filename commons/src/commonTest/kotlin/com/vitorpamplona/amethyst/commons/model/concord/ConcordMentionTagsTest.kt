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
package com.vitorpamplona.amethyst.commons.model.concord

import com.vitorpamplona.amethyst.commons.actions.ConcordActions
import com.vitorpamplona.amethyst.commons.model.cache.EventCache
import com.vitorpamplona.amethyst.commons.model.composer.NewMessageTagger
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

/**
 * A Concord @-mention used to go out as the picker's raw `@npub1…` text with no `p` tag, so the
 * named member was never notified (Notifications and Armada both find a mention by its `p`) and
 * Armada showed the bech32 string verbatim. The send path now runs the composer's tagger and
 * [ConcordActions.mentionTags] over the text.
 */
class ConcordMentionTagsTest {
    private val me = "aa".repeat(32)
    private val mary = "bb".repeat(32)
    private val will = "cc".repeat(32)

    @Test
    fun anAtMentionBecomesANip27ReferenceAndAPTag() =
        runTest {
            val tagger = NewMessageTagger("hi @${mary.hexToNpub()} and @${mary.hexToNpub()}", dao = EventCache()).also { it.run() }
            val tags = ConcordActions.mentionTags(tagger.pTags.orEmpty().map { it.pubkeyHex }, me)

            assertTrue(tagger.message.contains("nostr:nprofile1"), tagger.message)
            assertTrue(!tagger.message.contains("@npub1"), tagger.message)
            assertContentEquals(listOf(listOf("p", mary)), tags.map { it.toList() })
        }

    @Test
    fun neverTagsTheAuthorOrAnAlreadyCreditedParent() {
        val tags = ConcordActions.mentionTags(listOf(me, mary.uppercase(), will), me, alreadyTagged = setOf(will))
        assertContentEquals(listOf(listOf("p", mary)), tags.map { it.toList() })
    }

    private fun String.hexToNpub() = hexToByteArray().toNpub()
}
