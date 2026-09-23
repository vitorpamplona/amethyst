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

import androidx.compose.runtime.Immutable

/**
 * The 2 to 256 colours an object's indices name (DECK-0003 §1.3a), as opaque ARGB.
 */
@Immutable
class SnoPalette(
    val colors: IntArray,
) {
    val size: Int get() = colors.size

    operator fun get(index: Int): Int = colors[index]

    companion object {
        const val MIN_ENTRIES = 2
        const val MAX_ENTRIES = 256

        val BUILT_IN = SnoPalette(SnoBuiltInPalette.COLORS)
    }
}

/**
 * What an object's `palette` field said, kept after parsing so a caller can
 * decide whether to go looking for a referenced palette event.
 *
 * The rule that makes a reference safe is that it is never load-bearing
 * (§1.3b): an unresolved reference reads as the built-in, so the worst case is
 * an object drawn in the wrong colours and never one that cannot be drawn.
 */
@Immutable
sealed class SnoPaletteRef {
    /** No `palette` field, or the registered name of the built-in. */
    data object BuiltIn : SnoPaletteRef()

    /**
     * An `nevent1…` or `naddr1…` naming a palette published as its own event.
     * Pinned to the event it names: a reader MUST NOT follow the `e` chain
     * forward to a newer version (§1.3b).
     */
    data class Event(
        val bech32: String,
    ) : SnoPaletteRef()

    /** A palette carried in the object itself. */
    data class Inline(
        val palette: SnoPalette,
    ) : SnoPaletteRef()
}
