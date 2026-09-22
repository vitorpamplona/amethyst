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
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.KotlinSerializationMapper
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Reads a palette out of an event an object's `palette` reference named
 * (DECK-0003 §1.3b).
 *
 * The kind is deliberately not constrained: this format does not define a
 * palette kind and does not want one. `kind 3367`, the colour-moment
 * convention, is what carries them today, and a reader that accepts the shape
 * below will read whatever convention wins without the deck being revised.
 *
 * Two shapes, in order, and the first that succeeds wins:
 *
 * 1. **The `c` tags**, in the order they appear, if there are 2 to 256 of them
 *    and every value is a well-formed `#rrggbb`. Their order is the palette's
 *    order. This is the form every palette on the network actually uses, and
 *    it is a form relays index: `{"#c": ["#FF0000"]}` finds every palette
 *    containing pure red, with no new index and no new kind.
 * 2. Otherwise **`content`**, if it parses as a JSON array of 2 to 256 entries
 *    that are each `[r, g, b]` integers or a `"#rrggbb"` string. A legacy form,
 *    accepted only because an earlier draft described it; nothing should write
 *    it now.
 *
 * Anything else is not a palette event, which counts as a failed fetch, which
 * means the built-in — never a refusal to draw the object that named it.
 */
object SnoPaletteEventReader {
    fun read(event: Event): SnoPalette? = fromTags(event) ?: fromContent(event.content)

    private fun fromTags(event: Event): SnoPalette? {
        val values = event.tags.mapNotNull { if (it.size > 1 && it[0] == "c") it[1] else null }
        if (values.size < SnoPalette.MIN_ENTRIES || values.size > SnoPalette.MAX_ENTRIES) return null
        val colors = IntArray(values.size)
        for (i in values.indices) {
            colors[i] = parseHexColor(values[i]) ?: return null
        }
        return SnoPalette(colors)
    }

    private fun fromContent(content: String): SnoPalette? {
        val entries =
            try {
                KotlinSerializationMapper.json.parseToJsonElement(content) as? JsonArray
            } catch (_: Exception) {
                null
            } ?: return null

        if (entries.size < SnoPalette.MIN_ENTRIES || entries.size > SnoPalette.MAX_ENTRIES) return null
        val colors = IntArray(entries.size)
        for (i in 0 until entries.size) {
            colors[i] =
                when (val entry = entries[i]) {
                    is JsonArray -> {
                        if (entry.size != 3) return null
                        val r = channel(entry[0]) ?: return null
                        val g = channel(entry[1]) ?: return null
                        val b = channel(entry[2]) ?: return null
                        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    }
                    is JsonPrimitive -> {
                        if (!entry.isString) return null
                        parseHexColor(entry.content) ?: return null
                    }
                    else -> return null
                }
        }
        return SnoPalette(colors)
    }

    private fun channel(element: JsonElement): Int? {
        val primitive = element as? JsonPrimitive ?: return null
        if (primitive.isString) return null
        val value = primitive.intOrNull ?: return null
        return if (value in 0..255) value else null
    }

    /** `#rrggbb`, and nothing else: no shorthand, no alpha, no bare hex. */
    private fun parseHexColor(value: String): Int? {
        if (value.length != 7 || value[0] != '#') return null
        var rgb = 0
        for (i in 1..6) {
            val digit =
                when (val c = value[i]) {
                    in '0'..'9' -> c - '0'
                    in 'a'..'f' -> c - 'a' + 10
                    in 'A'..'F' -> c - 'A' + 10
                    else -> return null
                }
            rgb = (rgb shl 4) or digit
        }
        return (0xFF shl 24) or rgb
    }
}
