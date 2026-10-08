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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip30CustomEmoji.stickers.StickerPackEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.stickers.description
import com.vitorpamplona.quartz.nip30CustomEmoji.stickers.image
import com.vitorpamplona.quartz.nip30CustomEmoji.stickers.tags.StickerTag
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Kind 30031 sticker packs. Fixtures are synthetic, in the two layouts seen on relays: DEN Chat's
 * `[code, url, sfw|nsfw]` and Sonar's `[code, url, sha256, mime, "", description, emoji]`.
 */
class StickerPackEventTest {
    private val author = "6".repeat(64)
    private val hash = "680e6faf1e3554596e8674141c2474638344c8235f7c5d58795b07b3b1a9abd2"

    private fun pack(
        tags: Array<Array<String>>,
        content: String = "",
    ): Event = EventFactory.create("1".repeat(64), author, 1_791_417_751L, StickerPackEvent.KIND, tags, content, "00".repeat(64))

    private fun denPack() =
        pack(
            arrayOf(
                arrayOf("d", "73ccc479-9303-452e-84cc-49f077711b54"),
                arrayOf("title", "Cats"),
                arrayOf("sticker", "happy_cat", "https://blossom.example.com/$hash.png", "sfw"),
                arrayOf("sticker", "angry-cat", "https://blossom.example.com/b.gif", "nsfw"),
                arrayOf("client", "DEN Chat"),
            ),
        )

    private fun <T : Event> EventTemplate<T>.toEvent(): T = EventFactory.create("2".repeat(64), author, createdAt, kind, tags, content, "")

    @Test
    fun factoryBuildsStickerPackForKind30031() {
        assertIs<StickerPackEvent>(denPack())
        assertTrue(EventFactory.isKnownKind(StickerPackEvent.KIND))
    }

    @Test
    fun parsesDenChatLayout() {
        val event = assertIs<StickerPackEvent>(denPack())

        assertEquals("73ccc479-9303-452e-84cc-49f077711b54", event.dTag())
        assertEquals("Cats", event.title())
        assertNull(event.description())
        assertTrue(event.isStickerPack())

        val stickers = event.stickers()
        assertEquals(listOf("happy_cat", "angry-cat"), stickers.map { it.code })
        assertEquals("https://blossom.example.com/$hash.png", stickers[0].url)
        assertEquals("sfw", stickers[0].rating)
        assertFalse(stickers[0].isNsfw())
        assertTrue(stickers[1].isNsfw())
        assertNull(stickers[0].sha256)
    }

    @Test
    fun parsesSonarLayout() {
        val event =
            assertIs<StickerPackEvent>(
                pack(
                    arrayOf(
                        arrayOf("d", "signal-b676ec334ee2f771cadff5d095971e8c"),
                        arrayOf("title", "Dogs"),
                        arrayOf("pack_format", "sonar-sticker-pack-v1"),
                        arrayOf("description", "Imported from a Signal sticker pack."),
                        arrayOf("image", "https://push.example.com/$hash", hash),
                        arrayOf("sticker", "s0", "https://push.example.com/$hash", hash, "image/png", "", "Sticker 0", "😂"),
                    ),
                ),
            )

        assertEquals("Imported from a Signal sticker pack.", event.description())
        assertEquals("https://push.example.com/$hash", event.image())
        val sticker = event.stickers().single()
        assertEquals("s0", sticker.code)
        assertEquals(hash, sticker.sha256)
        assertEquals("image/png", sticker.mimeType)
        assertEquals("Sticker 0", sticker.description)
        assertEquals("😂", sticker.emoji)
        assertNull(sticker.rating)
    }

    @Test
    fun collidingAppsAndMalformedStickersReadAsEmptyPacks() {
        // A light-switch game state on the same kind: JSON content, only a `d`.
        val game = assertIs<StickerPackEvent>(pack(arrayOf(arrayOf("d", "lobby:orb")), """{"on":false,"at":1790872542}"""))
        assertFalse(game.isStickerPack())
        assertEquals(emptyList(), game.stickers())
        // The content is never indexed, so the JSON stays out of search.
        assertEquals("", game.indexableContent())

        val broken =
            assertIs<StickerPackEvent>(
                pack(arrayOf(arrayOf("sticker"), arrayOf("sticker", "code"), arrayOf("sticker", "", "https://x"), arrayOf("sticker", "code", ""))),
            )
        assertFalse(broken.isStickerPack())
        assertEquals(emptyList(), broken.stickers())
    }

    @Test
    fun hasNoGraphEdges() {
        val event = denPack()
        assertFalse(event is PubKeyHintProvider)
        assertFalse(event is EventHintProvider)
        assertFalse(event is AddressHintProvider)
    }

    @Test
    fun indexesTitleDescriptionAndShortcodesButNeverContent() {
        val event =
            assertIs<StickerPackEvent>(
                pack(
                    arrayOf(
                        arrayOf("title", "Cats"),
                        arrayOf("description", "Cat reactions"),
                        arrayOf("sticker", "happy_cat", "https://x.example/a.png"),
                        arrayOf("sticker", "sad_cat", "https://x.example/b.png"),
                    ),
                    content = "private-looking-ciphertext?iv=abc",
                ),
            )
        assertEquals("Cats\nCat reactions\nhappy_cat\nsad_cat", event.indexableContent())

        val visited = mutableListOf<String>()
        event.forEachIndexableField { field ->
            field?.let { visited.add(it) }
            true
        }
        assertEquals(event.indexableContent(), visited.joinToString(event.indexableSeparator()))

        var seen = 0
        event.forEachIndexableField {
            seen++
            false
        }
        assertEquals(1, seen)
    }

    @Test
    fun buildRoundTrips() {
        val stickers =
            listOf(
                StickerTag("happy_cat", "https://x.example/a.png", StickerTag.RATING_SFW),
                StickerTag("plain", "https://x.example/b.png"),
            )
        val event =
            StickerPackEvent
                .build("Cats", stickers, dTag = "cats", createdAt = 1_700_000_000L) {
                    description("Cat reactions")
                    image("https://x.example/cover.png")
                }.toEvent()

        assertEquals("cats", event.dTag())
        assertEquals("Cats", event.title())
        assertEquals("Cat reactions", event.description())
        assertEquals("https://x.example/cover.png", event.image())
        assertEquals(stickers, event.stickers())
        assertEquals(listOf("sticker", "happy_cat", "https://x.example/a.png", "sfw"), event.tags.first { it[0] == "sticker" }.toList())
        assertEquals(listOf("sticker", "plain", "https://x.example/b.png"), event.tags.last { it[0] == "sticker" }.toList())
    }

    @Test
    fun sonarStickersKeepTheirFieldsWhenWrittenBack() {
        val tag = arrayOf("sticker", "s0", "https://push.example.com/$hash", hash, "image/png", "", "Sticker 0", "\uD83D\uDE02")
        assertContentEquals(tag, StickerTag.assemble(StickerTag.parse(tag)!!))

        val bare = arrayOf("sticker", "s1", "https://push.example.com/$hash", hash, "", "", "", "")
        assertContentEquals(bare, StickerTag.parse(bare)!!.toTagArray())
    }
}
