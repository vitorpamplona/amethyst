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
package com.vitorpamplona.amethyst.commons.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance

/**
 * Determines if the color scheme is light mode, from the background's luminance
 * (near-white in light themes, black in dark themes).
 *
 * NOTE: this used to test `primary.luminance() < 0.5f`, which was wrong — the
 * default purple accent is a deep purple in the light theme AND a light purple
 * in the dark theme, both below 0.5, so it reported "light" in *both* modes.
 * The Android app keys the same decision off the background (`background !=
 * Color.Black`); background luminance is the multiplatform-safe equivalent.
 */
val ColorScheme.isLight: Boolean
    get() = background.luminance() > 0.5f

/**
 * Color filter for onBackground color (for tinting icons/images).
 */
val ColorScheme.onBackgroundColorFilter: ColorFilter
    get() = ColorFilter.tint(onBackground)

/**
 * De-emphasized text/icon color for secondary markers and hints. Matches the
 * Android app's placeholderText, which is the palette's onSurface at 42%.
 */
val ColorScheme.placeholderText: Color
    get() = onSurface.copy(alpha = 0.42f)

/**
 * Slightly stronger de-emphasized text color than [placeholderText]. Matches the
 * Android app's grayText, the palette's onSurface at 52%.
 */
val ColorScheme.grayText: Color
    get() = onSurface.copy(alpha = 0.52f)

/** Green "all good / healthy" status color. Mirrors the Android app's allGoodColor. */
val ColorScheme.allGoodColor: Color
    get() = if (isLight) Color(0xFF339900) else Color(0xFF99CC33)

/** Amber "warning / degraded" status color. Mirrors the Android app's warningColor. */
val ColorScheme.warningColor: Color
    get() = if (isLight) Color(0xFFFFCC00) else Color(0xFFF8DE22)

/**
 * Highlighter-pen wash painted behind NIP-84 highlighted text.
 *
 * Light themes get the classic near-opaque yellow marker: dark glyphs read straight
 * through it, exactly like a pen on paper. Dark themes cannot do that — a bright
 * yellow slab behind light text is unreadable, and inverting to dark glyphs makes the
 * quote fight the card it sits on — so they get a translucent amber that glows behind
 * the text instead of covering it, paired with [highlightMarkerText].
 *
 * Deliberately NOT derived from the user's accent color: a highlighter reads as yellow
 * regardless of theme, and re-tinting it to (say) a teal accent loses the metaphor.
 */
val ColorScheme.highlightMarker: Color
    get() = if (isLight) Color(0xFFFFE066).copy(alpha = 0.88f) else Color(0xFFFFD24A).copy(alpha = 0.33f)

/** Glyph color for text sitting on the [highlightMarker] wash. */
val ColorScheme.highlightMarkerText: Color
    get() = if (isLight) Color(0xFF1A1400) else Color(0xFFFFEFC2)

/**
 * The un-highlighted text surrounding a NIP-84 highlight. Dimmed so the marked span
 * carries the eye without needing bold, which is what the quote used to lean on.
 */
val ColorScheme.highlightContextText: Color
    get() = onSurface.copy(alpha = if (isLight) 0.55f else 0.50f)

/** Left rule marking the block as quoted from somewhere else. */
val ColorScheme.highlightQuoteBar: Color
    get() = onSurface.copy(alpha = if (isLight) 0.16f else 0.22f)
