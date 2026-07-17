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
package com.vitorpamplona.amethyst.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

// A living reference of every foreground/background pair the mobile theme uses, rendered with the
// real AmethystTheme colors (no approximation). Edit Theme.kt and the two columns below re-render.
// Left column is the dark theme, right column is the light theme, each with the current accent.
//
// Each pair references the actual ColorScheme roles the app consumes for that surface, so tweaking a
// role here previews everywhere it lands. `usage` names the concrete in-app surfaces.
private class Pair(
    val name: String,
    val bg: (ColorScheme) -> Color,
    val fg: (ColorScheme) -> Color,
    val usage: String,
    val highlight: Boolean = false,
)

private val COLOR_PAIRS =
    listOf(
        Pair(
            "filledAccent / onFilledAccent",
            { it.filledAccent },
            { it.onFilledAccent },
            "Top-bar Save·Post·Send·Create button, standalone Save button, checked switch (track + thumb).",
            highlight = true,
        ),
        Pair(
            "primary / onPrimary",
            { it.primary },
            { it.onPrimary },
            "Floating action buttons (New Note, New Image…), generic filled buttons, sliders, progress.",
        ),
        Pair(
            "primaryContainer / onPrimaryContainer",
            { it.primaryContainer },
            { it.onPrimaryContainer },
            "Settings icon tiles, tonal cards (DVM, wallet, relay info), tonal chips.",
        ),
        Pair(
            "secondaryContainer / onSecondaryContainer",
            { it.secondaryContainer },
            { it.onSecondaryContainer },
            "Selected-reaction highlight, relay-group chips, chatroom header, reply-mode toggle.",
        ),
        Pair(
            "secondary / onSecondary",
            { it.secondary },
            { it.onSecondary },
            "Teal accent (purple theme only); tertiary mirrors it.",
        ),
        Pair(
            "tertiaryContainer / onTertiaryContainer",
            { it.tertiaryContainer },
            { it.onTertiaryContainer },
            "Tertiary tonal surfaces; derived from the accent alongside the other containers.",
        ),
        Pair(
            "error / onError",
            { it.error },
            { it.onError },
            "Destructive dialogs and error states. Material baseline — not themed to the accent.",
        ),
        Pair(
            "surface / onSurface",
            { it.surface },
            { it.onSurface },
            "App background and primary body text everywhere.",
        ),
        Pair(
            "surface / onSurfaceVariant",
            { it.surface },
            { it.onSurfaceVariant },
            "Secondary text, timestamps, subtitles and icons on the background.",
        ),
        Pair(
            "surfaceContainer / onSurface",
            { it.surfaceContainer },
            { it.onSurface },
            "Cards, bottom sheets and elevated rows, with body text on top.",
        ),
        Pair(
            "surfaceVariant / onSurfaceVariant",
            { it.surfaceVariant },
            { it.onSurfaceVariant },
            "Subtle filled areas — code blocks, inset panels, muted backgrounds.",
        ),
        Pair(
            "surfaceContainerHighest / outline",
            { it.surfaceContainerHighest },
            { it.outline },
            "The UNCHECKED switch (off-track fill + thumb) and other inactive controls.",
        ),
    )

private fun Color.hex(): String = "#%06X".format(0xFFFFFF and toArgb())

// WCAG contrast ratio between two opaque colors.
private fun contrastRatio(
    a: Color,
    b: Color,
): Float {
    val la = a.luminance()
    val lb = b.luminance()
    val hi = max(la, lb)
    val lo = min(la, lb)
    return (hi + 0.05f) / (lo + 0.05f)
}

private fun contrastLabel(ratio: Float): String =
    when {
        ratio >= 4.5f -> "AA"
        ratio >= 3f -> "AA-lg"
        else -> "low"
    }

@Composable
private fun ColorPairRow(pair: Pair) {
    val scheme = MaterialTheme.colorScheme
    val bg = pair.bg(scheme)
    val fg = pair.fg(scheme)
    val ratio = contrastRatio(bg, fg)

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(width = 92.dp, height = 58.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .then(
                        if (pair.highlight) {
                            Modifier.border(2.dp, scheme.onBackground, RoundedCornerShape(10.dp))
                        } else {
                            Modifier.border(1.dp, scheme.outlineVariant, RoundedCornerShape(10.dp))
                        },
                    ).padding(8.dp),
        ) {
            Text("Aa", color = fg, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(
                "${"%.1f".format(ratio)}:1 ${contrastLabel(ratio)}",
                color = fg,
                fontSize = 8.5.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                pair.name,
                color = scheme.onBackground,
                fontSize = 12.5.sp,
                fontWeight = if (pair.highlight) FontWeight.Bold else FontWeight.Medium,
            )
            Text(
                "${bg.hex()}  /  ${fg.hex()}",
                color = scheme.onSurfaceVariant,
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                pair.usage,
                color = scheme.onSurfaceVariant,
                fontSize = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ColorPairsList() {
    Column(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "fg / bg pairs · $ACCENT_HINT",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
        COLOR_PAIRS.forEach { ColorPairRow(it) }
    }
}

private const val ACCENT_HINT = "accent = Settings → Accent Color (default Purple)"

@Preview(name = "Theme color pairs · dark | light", widthDp = 820, showBackground = true)
@Composable
fun ThemeColorPairsPreview() {
    ThemeComparisonRow {
        ColorPairsList()
    }
}
