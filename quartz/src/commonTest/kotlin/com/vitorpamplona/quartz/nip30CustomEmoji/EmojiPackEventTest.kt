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

import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import com.vitorpamplona.quartz.nip51Lists.encryption.PrivateTagsInContent
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.nsecToKeyPair
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmojiPackEventTest {
    private val authorSigner =
        NostrSignerInternal(
            "nsec10g0wheggqn9dawlc0yuv6adnat6n09anr7eyykevw2dm8xa5fffs0wsdsr".nsecToKeyPair(),
        )
    private val foreignSigner =
        NostrSignerInternal(
            "nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5".nsecToKeyPair(),
        )

    private suspend fun buildPack(
        publicEmojis: List<EmojiUrlTag> = emptyList(),
        privateEmojis: List<EmojiUrlTag> = emptyList(),
    ): EmojiPackEvent {
        val privateContent =
            if (privateEmojis.isEmpty()) {
                ""
            } else {
                PrivateTagsInContent.encryptNip44(
                    privateEmojis.map { it.toTagArray() }.toTypedArray(),
                    authorSigner,
                )
            }
        val publicTags =
            buildList {
                add(arrayOf("alt", EmojiPackEvent.ALT_DESCRIPTION))
                add(arrayOf("d", "test-pack"))
                add(arrayOf("title", "Test pack"))
                publicEmojis.forEach { add(it.toTagArray()) }
            }.toTypedArray()
        return authorSigner.sign(
            createdAt = TimeUtils.now(),
            kind = EmojiPackEvent.KIND,
            tags = publicTags,
            content = privateContent,
        )
    }

    @Test
    fun publicEmojisReturnsTaggedEntries() =
        runTest {
            val pub = EmojiUrlTag("smile", "https://example.com/smile.png")
            val pack = buildPack(publicEmojis = listOf(pub))
            assertEquals(listOf(pub), pack.publicEmojis())
        }

    @Test
    fun privateEmojisDecryptsForAuthor() =
        runTest {
            val priv = EmojiUrlTag("secret", "https://example.com/secret.png")
            val pack = buildPack(privateEmojis = listOf(priv))
            assertEquals(listOf(priv), pack.privateEmojis(authorSigner))
        }

    @Test
    fun privateEmojisRefusesForeignSigner() =
        runTest {
            val priv = EmojiUrlTag("secret", "https://example.com/secret.png")
            val pack = buildPack(privateEmojis = listOf(priv))
            assertNull(pack.privateEmojis(foreignSigner))
        }

    @Test
    fun allEmojisMergesPublicAndPrivateForAuthor() =
        runTest {
            val pub = EmojiUrlTag("smile", "https://example.com/smile.png")
            val priv = EmojiUrlTag("secret", "https://example.com/secret.png")
            val pack = buildPack(publicEmojis = listOf(pub), privateEmojis = listOf(priv))
            val merged = pack.allEmojis(authorSigner)
            assertEquals(2, merged.size)
            assertTrue(pub in merged)
            assertTrue(priv in merged)
        }

    @Test
    fun allEmojisYieldsPublicOnlyForForeignSigner() =
        runTest {
            val pub = EmojiUrlTag("smile", "https://example.com/smile.png")
            val priv = EmojiUrlTag("secret", "https://example.com/secret.png")
            val pack = buildPack(publicEmojis = listOf(pub), privateEmojis = listOf(priv))
            assertEquals(listOf(pub), pack.allEmojis(foreignSigner))
        }
}
