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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
import com.vitorpamplona.amethyst.model.AccentColorType
import com.vitorpamplona.amethyst.model.ThemeType
import kotlin.math.max
import kotlin.math.min

// Living reference + side-by-side "before / after" of the mobile theme, rendered with the real
// color schemes so editing Theme.kt re-renders both. Each row is one app surface (Save button,
// checked switch, settings tile, card, unchecked switch, …) shown four ways:
//   Old·Dark | New·Dark | Old·Light | New·Light
// so the effect of the accent-normalization (+ the new filledAccent role) is directly comparable
// within each theme. "Old" reconstructs the pre-normalization scheme: purple primary + teal
// secondary with Material's default violet-tinted surfaces/containers and the deep default
// onPrimary — i.e. the theme before container-derivation, surface-neutralization and onPrimary=White.

// ----- reconstructed pre-normalization scheme (default purple accent) -----
private fun oldDarkColors(): ColorScheme =
    darkColorScheme(
        primary = Purple200,
        secondary = Teal200,
        tertiary = Teal200,
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceVariant = Color(0xFF1D1A22),
    )

private fun oldLightColors(): ColorScheme =
    lightColorScheme(
        primary = Purple500,
        secondary = Teal200,
        tertiary = Teal200,
        surfaceContainerHighest = Color(0xFFECE6F0),
        surfaceVariant = Color(0xFFFAF5FC),
    )

// One app surface and how to resolve its fill/content from a scheme. `isOld` lets a surface pick a
// different role for the two eras — the Save button + switch used primary/onPrimary before and
// filledAccent/onFilledAccent now; everything else keeps its role and just shows the value drift.
private class StyleEntry(
    val name: String,
    val usage: String,
    val bg: (ColorScheme, Boolean) -> Color,
    val fg: (ColorScheme, Boolean) -> Color,
    val highlight: Boolean = false,
)

private val STYLE_ENTRIES =
    listOf(
        StyleEntry(
            "Save button · checked switch",
            "Old: primary/onPrimary (washed out). New: the dedicated filledAccent fill.",
            bg = { s, old -> if (old) s.primary else s.filledAccent },
            fg = { s, old -> if (old) s.onPrimary else s.onFilledAccent },
            highlight = true,
        ),
        StyleEntry(
            "FAB · filled button",
            "primary / onPrimary — unchanged; still the pastel pairing on dark.",
            bg = { s, _ -> s.primary },
            fg = { s, _ -> s.onPrimary },
        ),
        StyleEntry(
            "Settings tile · tonal card",
            "primaryContainer / onPrimaryContainer — old = Material violet, new = accent-derived.",
            bg = { s, _ -> s.primaryContainer },
            fg = { s, _ -> s.onPrimaryContainer },
        ),
        StyleEntry(
            "Selected reaction · chip",
            "secondaryContainer / onSecondaryContainer.",
            bg = { s, _ -> s.secondaryContainer },
            fg = { s, _ -> s.onSecondaryContainer },
        ),
        StyleEntry(
            "Teal accent",
            "secondary / onSecondary (purple theme).",
            bg = { s, _ -> s.secondary },
            fg = { s, _ -> s.onSecondary },
        ),
        StyleEntry(
            "App background · body text",
            "surface / onSurface — old carried Material's violet tint, new is neutral grey.",
            bg = { s, _ -> s.surface },
            fg = { s, _ -> s.onSurface },
        ),
        StyleEntry(
            "Secondary text",
            "surface / onSurfaceVariant.",
            bg = { s, _ -> s.surface },
            fg = { s, _ -> s.onSurfaceVariant },
        ),
        StyleEntry(
            "Card · sheet",
            "surfaceContainer / onSurface — old = Material default, new = neutral ramp.",
            bg = { s, _ -> s.surfaceContainer },
            fg = { s, _ -> s.onSurface },
        ),
        StyleEntry(
            "Unchecked switch",
            "surfaceContainerHighest / outline (off-track + thumb).",
            bg = { s, _ -> s.surfaceContainerHighest },
            fg = { s, _ -> s.outline },
        ),
        StyleEntry(
            "Error",
            "error / onError — Material baseline in both.",
            bg = { s, _ -> s.error },
            fg = { s, _ -> s.onError },
        ),
    )

private fun Color.hex(): String = "#%06X".format(0xFFFFFF and toArgb())

private fun contrastRatio(
    a: Color,
    b: Color,
): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
}

private const val CELL_WIDTH = 116
private const val LABEL_WIDTH = 196

@Composable
private fun StyleCell(
    entry: StyleEntry,
    scheme: ColorScheme,
    isOld: Boolean,
) {
    val bg = entry.bg(scheme, isOld)
    val fg = entry.fg(scheme, isOld)
    val ratio = contrastRatio(bg, fg)
    val label = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = Modifier.width(CELL_WIDTH.dp).padding(horizontal = 4.dp)) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    .padding(6.dp),
        ) {
            Text("Aa", color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                "%.1f".format(ratio),
                color = fg,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
        Text(bg.hex(), color = label, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace)
        Text(fg.hex(), color = label, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun StyleRow(
    entry: StyleEntry,
    oldDark: ColorScheme,
    newDark: ColorScheme,
    oldLight: ColorScheme,
    newLight: ColorScheme,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.width(LABEL_WIDTH.dp).padding(end = 8.dp)) {
            Text(
                entry.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 11.5.sp,
                fontWeight = if (entry.highlight) FontWeight.Bold else FontWeight.Medium,
            )
            Text(
                entry.usage,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.5.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        StyleCell(entry, oldDark, isOld = true)
        StyleCell(entry, newDark, isOld = false)
        StyleCell(entry, oldLight, isOld = true)
        StyleCell(entry, newLight, isOld = false)
    }
}

@Composable
private fun ColumnHeader(text: String) {
    Text(
        text,
        modifier = Modifier.width(CELL_WIDTH.dp).padding(horizontal = 4.dp),
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
    )
}

@Preview(name = "Theme styleguide — old vs new", widthDp = 720, showBackground = true)
@Composable
fun ThemeStyleguideOldVsNewPreview() {
    val oldDark = oldDarkColors()
    val newDark = darkColors(AccentColorType.PURPLE)
    val oldLight = oldLightColors()
    val newLight = lightColors(AccentColorType.PURPLE)

    // Neutral dark chrome for the table itself; each cell paints its own scheme color.
    AmethystTheme(ThemeType.DARK) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    "Theme styleguide · before normalization → current",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Row {
                    Spacer(Modifier.width(LABEL_WIDTH.dp))
                    ColumnHeader("Old·Dark")
                    ColumnHeader("New·Dark")
                    ColumnHeader("Old·Light")
                    ColumnHeader("New·Light")
                }
                STYLE_ENTRIES.forEach {
                    StyleRow(it, oldDark, newDark, oldLight, newLight)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Role-oriented view: every fg/bg pair listed by its ColorScheme role name (not
// by the component using it), rendered for the CURRENT theme in dark | light.
// Answers "what is primaryContainer right now"; the table above answers "how did
// this surface change". Both read the live scheme, so Theme.kt edits re-render.
// ---------------------------------------------------------------------------

private class RolePair(
    val name: String,
    val bg: (ColorScheme) -> Color,
    val fg: (ColorScheme) -> Color,
    val usage: String,
    val highlight: Boolean = false,
)

private val ROLE_PAIRS =
    listOf(
        RolePair(
            "filledAccent / onFilledAccent",
            { it.filledAccent },
            { it.onFilledAccent },
            "Top-bar Save·Post·Send·Create button, standalone Save button, checked switch.",
            highlight = true,
        ),
        RolePair(
            "primary / onPrimary",
            { it.primary },
            { it.onPrimary },
            "Floating action buttons, generic filled buttons, sliders, progress.",
        ),
        RolePair(
            "primaryContainer / onPrimaryContainer",
            { it.primaryContainer },
            { it.onPrimaryContainer },
            "Settings icon tiles, tonal cards, tonal chips.",
        ),
        RolePair(
            "secondaryContainer / onSecondaryContainer",
            { it.secondaryContainer },
            { it.onSecondaryContainer },
            "Selected-reaction highlight, relay-group chips, chat header, reply toggle.",
        ),
        RolePair(
            "secondary / onSecondary",
            { it.secondary },
            { it.onSecondary },
            "Teal accent (purple theme); tertiary mirrors it.",
        ),
        RolePair(
            "tertiaryContainer / onTertiaryContainer",
            { it.tertiaryContainer },
            { it.onTertiaryContainer },
            "Tertiary tonal surfaces; derived from the accent.",
        ),
        RolePair(
            "error / onError",
            { it.error },
            { it.onError },
            "Destructive dialogs and error states. Material baseline.",
        ),
        RolePair(
            "surface / onSurface",
            { it.surface },
            { it.onSurface },
            "App background and primary body text.",
        ),
        RolePair(
            "surface / onSurfaceVariant",
            { it.surface },
            { it.onSurfaceVariant },
            "Secondary text, timestamps, subtitles, icons.",
        ),
        RolePair(
            "surfaceContainer / onSurface",
            { it.surfaceContainer },
            { it.onSurface },
            "Cards, bottom sheets, elevated rows.",
        ),
        RolePair(
            "surfaceVariant / onSurfaceVariant",
            { it.surfaceVariant },
            { it.onSurfaceVariant },
            "Subtle filled areas — code blocks, inset panels.",
        ),
        RolePair(
            "surfaceContainerHighest / outline",
            { it.surfaceContainerHighest },
            { it.outline },
            "Unchecked switch (off-track + thumb), inactive controls.",
        ),
    )

private fun contrastLabel(ratio: Float): String =
    when {
        ratio >= 4.5f -> "AA"
        ratio >= 3f -> "AA-lg"
        else -> "low"
    }

@Composable
private fun RolePairRow(pair: RolePair) {
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
                    .width(92.dp)
                    .height(58.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .border(
                        if (pair.highlight) 2.dp else 1.dp,
                        if (pair.highlight) scheme.onBackground else scheme.outlineVariant,
                        RoundedCornerShape(10.dp),
                    ).padding(8.dp),
        ) {
            Text("Aa", color = fg, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(
                "%.1f:1 %s".format(ratio, contrastLabel(ratio)),
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
private fun RolePairList() {
    Column(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "fg / bg pairs by role",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
        ROLE_PAIRS.forEach { RolePairRow(it) }
    }
}

@Preview(name = "Theme color pairs by role · dark | light", widthDp = 820, showBackground = true)
@Composable
fun ThemeColorPairsPreview() {
    ThemeComparisonRow {
        RolePairList()
    }
}
