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
package com.vitorpamplona.amethyst.commons.richtext

import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A multi-cart note: the carts other than the one in the text are in its `imeta` tags only. */
class RichTextParserCartLinksTest {
    private val front = "https://example.com/front.png"
    private val main = "https://example.com/main.png"
    private val x = "a".repeat(64)

    private fun parse(
        content: String,
        vararg imetas: Array<String>,
    ) = RichTextParser().parseText(content, ImmutableListOfLists(arrayOf(*imetas)), null)

    private val mainTag = arrayOf("imeta", "url $main", "x $x", "m image/png", "dim 160x205", "cart #game_main-7")

    @Test
    fun anExtraCartIsNotShownInTheNote() {
        val state = parse("Into Ruins $front", arrayOf("imeta", "url $front", "m image/png", "cart #game"), mainTag)

        assertEquals(setOf(front), state.mediaForPager.keys)
        assertEquals(listOf(front), state.mediaList.map { it.url })
    }

    @Test
    fun theEntryCarriesItsLinks() {
        val state = parse("Into Ruins $front", arrayOf("imeta", "url $front", "m image/png", "cart #game"), mainTag)

        val links = assertNotNull(assertIs<MediaUrlImage>(state.mediaForPager[front]).cartLinks)
        assertEquals(listOf("#game"), links.ownNames)
        assertEquals(listOf("#game_main-7"), links.extras.single().names)
    }

    @Test
    fun anEntryWithoutAnImetaStillCarriesItsLinks() {
        val state = parse("Into Ruins $front", mainTag)

        assertEquals(1, assertIs<MediaUrlImage>(state.mediaForPager[front]).cartLinks?.extras?.size)
    }

    @Test
    fun anOrdinaryImageHasNoLinks() {
        val state = parse("A photo $front", arrayOf("imeta", "url $front", "m image/png"))

        assertNull(assertIs<MediaUrlImage>(state.mediaForPager[front]).cartLinks)
    }

    @Test
    fun aBlossomEntryCarriesItsLinksToo() {
        val blossom = "blossom:${"b".repeat(64)}.png?xs=cdn.example.com"
        val state = parse("Into Ruins $blossom", arrayOf("imeta", "url $blossom", "m image/png", "cart #game"), mainTag)

        val links = assertNotNull(assertIs<MediaUrlImage>(state.mediaForPager[blossom]).cartLinks)
        assertEquals(listOf("#game"), links.ownNames)
        assertEquals(1, links.extras.size)
        assertFalse(main in state.mediaForPager)
    }

    @Test
    fun aBlossomImageWithNoImetaIsTheSameAsBefore() {
        val blossom = "blossom:${"b".repeat(64)}.png?xs=cdn.example.com"
        val image = assertIs<MediaUrlImage>(parse("A photo $blossom").mediaForPager[blossom])

        assertNull(image.cartLinks)
        assertNull(image.hash)
        assertNull(image.dim)
        assertTrue(image.url == blossom)
    }

    @Test
    fun aBlossomImageSeesItsImeta() {
        val blossom = "blossom:${"b".repeat(64)}.png?xs=cdn.example.com"
        val state = parse("A photo $blossom", arrayOf("imeta", "url $blossom", "dim 100x200", "alt a cat", "x $x"))

        val image = assertIs<MediaUrlImage>(state.mediaForPager[blossom])
        assertEquals(100, image.dim?.width)
        assertEquals(x, image.hash)
    }
}
