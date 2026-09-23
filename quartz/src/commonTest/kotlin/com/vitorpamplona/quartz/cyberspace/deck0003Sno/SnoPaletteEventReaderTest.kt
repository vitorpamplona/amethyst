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
package com.vitorpamplona.quartz.cyberspace.deck0003Sno

import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Reading a palette out of the event an object's reference named (§1.3b).
 *
 * The `c`-tag shapes here are taken from real `kind 3367` colour moments
 * sampled off relay.damus.io and nos.lol: four to six `#rrggbb` values in
 * document order, an emoji in `content`, and `layout` / `alt` / `client` /
 * `name` alongside. A survey of five relays found 205 such events from 51
 * pubkeys and every one of them is shaped this way, so the tag form is not a
 * hypothetical.
 */
class SnoPaletteEventReaderTest {
    private fun event(
        tags: Array<Array<String>>,
        content: String = "",
        kind: Int = 3367,
    ) = Event("00".repeat(32), "11".repeat(32), 0L, kind, tags, content, "22".repeat(64))

    @Test
    fun theCTagsAreThePaletteAndTheirOrderIsTheIndex() {
        val palette =
            SnoPaletteEventReader.read(
                event(
                    arrayOf(
                        arrayOf("name", "dusk"),
                        arrayOf("c", "#93A8D7"),
                        arrayOf("c", "#49413A"),
                        arrayOf("c", "#534E46"),
                        arrayOf("c", "#D2D1BF"),
                        arrayOf("alt", "a colour moment"),
                        arrayOf("client", "somewhere"),
                    ),
                    content = "\uD83C\uDF05",
                ),
            )

        assertNotNull(palette)
        assertEquals(4, palette.size)
        assertEquals(0xFF93A8D7.toInt(), palette[0])
        assertEquals(0xFF49413A.toInt(), palette[1])
        assertEquals(0xFFD2D1BF.toInt(), palette[3])
    }

    @Test
    fun lowercaseHexReadsTheSame() {
        val palette = SnoPaletteEventReader.read(event(arrayOf(arrayOf("c", "#ff0000"), arrayOf("c", "#00ff00"))))
        assertNotNull(palette)
        assertEquals(0xFFFF0000.toInt(), palette[0])
    }

    @Test
    fun aMalformedColourDisqualifiesTheTagForm() {
        assertNull(SnoPaletteEventReader.read(event(arrayOf(arrayOf("c", "#ff000"), arrayOf("c", "#00ff00")))))
        assertNull(SnoPaletteEventReader.read(event(arrayOf(arrayOf("c", "ff0000"), arrayOf("c", "#00ff00")))))
        assertNull(SnoPaletteEventReader.read(event(arrayOf(arrayOf("c", "#gg0000"), arrayOf("c", "#00ff00")))))
        assertNull(SnoPaletteEventReader.read(event(arrayOf(arrayOf("c", "#ff000000"), arrayOf("c", "#00ff00")))))
    }

    @Test
    fun oneColourIsNotAPalette() {
        assertNull(SnoPaletteEventReader.read(event(arrayOf(arrayOf("c", "#ff0000")))))
    }

    @Test
    fun theContentFormIsAcceptedAsLegacy() {
        // Accepted only because an earlier draft of §1.3b described it; nothing
        // should write it now, and no event on the network does.
        val triples = SnoPaletteEventReader.read(event(arrayOf(), content = "[[255,0,0],[0,255,0]]"))
        assertNotNull(triples)
        assertEquals(0xFFFF0000.toInt(), triples[0])

        val strings = SnoPaletteEventReader.read(event(arrayOf(), content = """["#ff0000","#00ff00"]"""))
        assertNotNull(strings)
        assertEquals(0xFF00FF00.toInt(), strings[1])
    }

    @Test
    fun theTagsWinOverTheContent() {
        val palette =
            SnoPaletteEventReader.read(
                event(
                    arrayOf(arrayOf("c", "#ff0000"), arrayOf("c", "#00ff00")),
                    content = "[[0,0,255],[0,0,255],[0,0,255]]",
                ),
            )
        assertNotNull(palette)
        assertEquals(2, palette.size)
        assertEquals(0xFFFF0000.toInt(), palette[0])
    }

    @Test
    fun anEventThatIsNotAPaletteReadsAsNothing() {
        // Which counts as a failed fetch, which means the built-in — never a
        // refusal to draw the object that named it.
        assertNull(SnoPaletteEventReader.read(event(arrayOf(arrayOf("p", "ff")), content = "hello")))
        assertNull(SnoPaletteEventReader.read(event(arrayOf(), content = "")))
    }

    @Test
    fun theKindIsNotConstrained() {
        // "This format does not define a palette kind and does not want one."
        val palette = SnoPaletteEventReader.read(event(arrayOf(arrayOf("c", "#ff0000"), arrayOf("c", "#00ff00")), kind = 1))
        assertNotNull(palette)
        assertEquals(2, palette.size)
    }
}
