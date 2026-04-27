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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import coil3.asDrawable
import coil3.compose.AsyncImage
import coil3.imageLoader
import com.vitorpamplona.amethyst.commons.viewmodels.RoomTheme
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Apply a [RoomTheme] (NIP-53 `c` / `bg` tags) to the room body
 * — overrides [MaterialTheme.colorScheme] for the inner content
 * and renders the optional background image behind it.
 *
 * Each color override is independent: a room that ships a primary
 * tint but no background gets the primary swap, default surface,
 * etc. The renderer fails OPEN — when [theme] is
 * [RoomTheme.Empty] or all fields are null, the wrapper is just a
 * passthrough Box.
 */
@Composable
internal fun NestThemedScope(
    theme: RoomTheme,
    accountViewModel: AccountViewModel,
    content: @Composable () -> Unit,
) {
    val original = MaterialTheme.colorScheme
    val themed =
        remember(theme, original) {
            // RoomTheme stores `0xFFRRGGBB` directly — Compose's
            // Color constructor accepts that exact packing.
            fun argbToColor(argb: Long?): Color? = argb?.let { Color(it) }
            original.copy(
                background = argbToColor(theme.backgroundArgb) ?: original.background,
                onBackground = argbToColor(theme.textArgb) ?: original.onBackground,
                primary = argbToColor(theme.primaryArgb) ?: original.primary,
                surface = argbToColor(theme.backgroundArgb) ?: original.surface,
                onSurface = argbToColor(theme.textArgb) ?: original.onSurface,
            )
        }

    val originalTypography = MaterialTheme.typography

    // URL-based font loading. produceState fetches + caches on
    // Dispatchers.IO; until the download completes (or if it
    // fails), we fall back to the system-font-name mapping below.
    // Same URL → same SHA-256 cache key → no repeat downloads
    // across launches.
    val context = LocalContext.current
    val urlFont by produceState<FontFamily?>(null, theme.fontUrl) {
        val url = theme.fontUrl
        value =
            if (url.isNullOrBlank()) {
                null
            } else {
                RoomFontLoader.load(url, context, accountViewModel.httpClientBuilder::okHttpClientForImage)
            }
    }
    val themedTypography =
        remember(theme.fontFamily, urlFont, originalTypography) {
            // Prefer the URL-loaded font when available; fall back
            // to the system-font-name mapping ("sans-serif" etc.)
            // while the download is in flight or after a failure.
            val family = urlFont ?: systemFontFamilyFor(theme.fontFamily)
            if (family == null) {
                originalTypography
            } else {
                applyFontFamily(originalTypography, family)
            }
        }

    MaterialTheme(colorScheme = themed, typography = themedTypography) {
        Box(modifier = Modifier.fillMaxSize()) {
            theme.backgroundImageUrl?.let { url ->
                when (theme.backgroundMode) {
                    RoomTheme.BackgroundMode.COVER -> {
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }

                    RoomTheme.BackgroundMode.TILE -> {
                        TiledRoomBackground(url = url)
                    }
                }
            }
            content()
        }
    }
}

/**
 * Repeating-tile background. Compose's [AsyncImage] doesn't have a
 * tile-mode option, so we fetch the bitmap via Coil into a
 * [ImageBitmap] and wrap it in an [ImageShader]+[ShaderBrush] —
 * that's the canonical way to get the platform's native repeat
 * sampler. While the load is in flight (or on failure) the scope
 * paints nothing so the underlying material surface shows through.
 */
@Composable
private fun TiledRoomBackground(url: String) {
    val context = LocalContext.current
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, url) {
        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    val request =
                        coil3.request.ImageRequest
                            .Builder(context)
                            .data(url)
                            .build()
                    val drawable =
                        context
                            .imageLoader
                            .execute(request)
                            .image
                            ?.asDrawable(context.resources) as? android.graphics.drawable.BitmapDrawable
                    drawable?.bitmap?.asImageBitmap()
                }.getOrNull()
            }
    }
    bitmap?.let { bmp ->
        val brush =
            remember(bmp) {
                androidx.compose.ui.graphics.ShaderBrush(
                    androidx.compose.ui.graphics.ImageShader(
                        image = bmp,
                        tileModeX = androidx.compose.ui.graphics.TileMode.Repeated,
                        tileModeY = androidx.compose.ui.graphics.TileMode.Repeated,
                    ),
                )
            }
        Box(modifier = Modifier.fillMaxSize().background(brush))
    }
}

/**
 * Map a kind-30312 `["f", family]` value to a Compose [FontFamily].
 * Only the four CSS-style generic-family names are honoured —
 * arbitrary names (e.g. "Inter") fall back to platform default
 * unless the room ships a [RoomTheme.fontUrl] (handled by
 * [RoomFontLoader] above).
 */
private fun systemFontFamilyFor(name: String?): FontFamily? =
    when (name?.trim()?.lowercase()) {
        null -> null
        "" -> null
        "sans-serif", "sans serif", "sansserif" -> FontFamily.SansSerif
        "serif" -> FontFamily.Serif
        "monospace", "mono" -> FontFamily.Monospace
        "cursive" -> FontFamily.Cursive
        else -> null
    }

private fun applyFontFamily(
    typography: Typography,
    family: FontFamily,
): Typography =
    typography.copy(
        displayLarge = typography.displayLarge.copy(fontFamily = family),
        displayMedium = typography.displayMedium.copy(fontFamily = family),
        displaySmall = typography.displaySmall.copy(fontFamily = family),
        headlineLarge = typography.headlineLarge.copy(fontFamily = family),
        headlineMedium = typography.headlineMedium.copy(fontFamily = family),
        headlineSmall = typography.headlineSmall.copy(fontFamily = family),
        titleLarge = typography.titleLarge.copy(fontFamily = family),
        titleMedium = typography.titleMedium.copy(fontFamily = family),
        titleSmall = typography.titleSmall.copy(fontFamily = family),
        bodyLarge = typography.bodyLarge.copy(fontFamily = family),
        bodyMedium = typography.bodyMedium.copy(fontFamily = family),
        bodySmall = typography.bodySmall.copy(fontFamily = family),
        labelLarge = typography.labelLarge.copy(fontFamily = family),
        labelMedium = typography.labelMedium.copy(fontFamily = family),
        labelSmall = typography.labelSmall.copy(fontFamily = family),
    )
