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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.material_symbols_outlined
import org.jetbrains.compose.resources.Font

// Held on CompositionLocal so every Material Symbol call site in the tree reuses one FontFamily
// instance. Without this, every Icon() composition allocates a new Font wrapper, breaks its own
// remember cache, and forces a fresh TextMeasurer per call site.
val LocalMaterialSymbolsFontFamily: ProvidableCompositionLocal<FontFamily?> = staticCompositionLocalOf { null }

// Shared TextMeasurer so its internal LRU cache is hit by every icon draw in the tree.
val LocalMaterialSymbolsTextMeasurer: ProvidableCompositionLocal<TextMeasurer?> = staticCompositionLocalOf { null }

private const val TEXT_MEASURER_CACHE_SIZE = 64

/**
 * Builds the Material Symbols FontFamily and a shared TextMeasurer once for the subtree and
 * exposes them via CompositionLocal. Wrap app roots (AmethystTheme, desktop MaterialTheme) in this.
 */
@Composable
fun ProvideMaterialSymbols(content: @Composable () -> Unit) {
    val font =
        Font(
            resource = Res.font.material_symbols_outlined,
            weight = FontWeight(MaterialSymbolsDefaults.WEIGHT),
            variationSettings =
                FontVariation.Settings(
                    FontVariation.weight(MaterialSymbolsDefaults.WEIGHT),
                    FontVariation.Setting("FILL", MaterialSymbolsDefaults.FILL),
                    FontVariation.Setting("opsz", MaterialSymbolsDefaults.OPTICAL_SIZE),
                    FontVariation.Setting("GRAD", MaterialSymbolsDefaults.GRADE),
                ),
        )
    // Keyless remember: the Font wrapper identity changes every composition but the underlying
    // resource is a compile-time constant, so one FontFamily for the lifetime of the subtree.
    val fontFamily = remember { FontFamily(font) }
    val textMeasurer = rememberTextMeasurer(cacheSize = TEXT_MEASURER_CACHE_SIZE)
    CompositionLocalProvider(
        LocalMaterialSymbolsFontFamily provides fontFamily,
        LocalMaterialSymbolsTextMeasurer provides textMeasurer,
        content = content,
    )
}

@Composable
internal fun materialSymbolsFontFamily(): FontFamily = LocalMaterialSymbolsFontFamily.current ?: localMaterialSymbolsFontFamilyFallback()

@Composable
internal fun materialSymbolsTextMeasurer(): TextMeasurer =
    LocalMaterialSymbolsTextMeasurer.current
        ?: rememberTextMeasurer(cacheSize = TEXT_MEASURER_CACHE_SIZE)

@Composable
private fun localMaterialSymbolsFontFamilyFallback(): FontFamily {
    val font =
        Font(
            resource = Res.font.material_symbols_outlined,
            weight = FontWeight(MaterialSymbolsDefaults.WEIGHT),
            variationSettings =
                FontVariation.Settings(
                    FontVariation.weight(MaterialSymbolsDefaults.WEIGHT),
                    FontVariation.Setting("FILL", MaterialSymbolsDefaults.FILL),
                    FontVariation.Setting("opsz", MaterialSymbolsDefaults.OPTICAL_SIZE),
                    FontVariation.Setting("GRAD", MaterialSymbolsDefaults.GRADE),
                ),
        )
    return remember { FontFamily(font) }
}
