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
package com.vitorpamplona.quartz.nip30CustomEmoji.pack

import com.vitorpamplona.quartz.nip50Search.IndexableFields
import com.vitorpamplona.quartz.nip50Search.SearchFieldExtractor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class EmojiPackSearchTest {
    private val pk = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val other = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val third = "7d7ffd720b907fe597a7f454afe02f2dc1eca440baa029e9117b1c3209839377"
    private val eventId = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val eventId2 = "b1a2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90"
    private val addressId = "30023:$other:my-article"
    private val relay = "wss://relay.damus.io/"
    private val id = "00".repeat(32)
    private val sig = "00".repeat(64)

    // What a PrivateTagArrayEvent's content looks like: NIP-44 ciphertext of the private tags.
    private val ciphertext = "AnSuPeRsEcReTnIp44PaYlOaDbAsE64"

    private val pack =
        EmojiPackEvent(
            id,
            pk,
            1L,
            arrayOf(
                arrayOf("d", "cats"),
                arrayOf("title", "Cats"),
                arrayOf("description", "Cat emojis"),
                arrayOf("emoji", "catjam", "https://example.com/catjam.gif"),
                arrayOf("emoji", "blob_cat", "https://example.com/blob.png"),
            ),
            ciphertext,
            sig,
        )

    @Test
    fun indexesPublicShortcodesNeverTheCiphertext() {
        assertEquals("Cats\nCat emojis\ncatjam\nblob_cat", pack.indexableContent())
        assertFalse(pack.indexableContent().contains(ciphertext))
        assertEquals(pack.indexableContent(), pack.rejoined())
    }

    @Test
    fun extractorPutsShortcodesInTheSecondaryTier() {
        val fields = SearchFieldExtractor.extract(pack) as IndexableFields.Tiered
        assertEquals(listOf("Cats"), fields.primary)
        assertEquals(listOf("Cat emojis", "catjam", "blob_cat"), fields.secondary)
        assertNull(fields.text)
    }
}

private fun SearchableEvent.rejoined(): String =
    buildList {
        forEachIndexableField { f ->
            if (f != null) add(f)
            true
        }
    }.joinToString(indexableSeparator())
