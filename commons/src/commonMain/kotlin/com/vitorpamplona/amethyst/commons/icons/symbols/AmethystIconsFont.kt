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
package com.vitorpamplona.amethyst.commons.icons.symbols

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.amethyst_icons
import org.jetbrains.compose.resources.Font

/**
 * Amethyst's own icons, built into a font by `tools/icon-font/build_icon_font.py`.
 *
 * Unlike Material Symbols this is a static font — no FILL/opsz/GRAD axes — so it needs no
 * [androidx.compose.ui.text.font.FontVariation] settings. Drawing an icon as a glyph blits from
 * the shared text atlas instead of rasterising an ImageVector's paths into a per-instance cached
 * layer, which is what made the feed re-rasterise the same three glyphs once per card.
 */
val LocalAmethystIconsFontFamily: ProvidableCompositionLocal<FontFamily?> = staticCompositionLocalOf { null }

@Composable
fun amethystIconsFontFamily(): FontFamily =
    LocalAmethystIconsFontFamily.current ?: run {
        val font = Font(resource = Res.font.amethyst_icons)
        remember { FontFamily(font) }
    }
