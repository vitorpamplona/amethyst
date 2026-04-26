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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.audiorooms.room

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.viewmodels.RoomTheme

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
internal fun AudioRoomThemedScope(
    theme: RoomTheme,
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

    MaterialTheme(colorScheme = themed) {
        Box(modifier = Modifier.fillMaxSize()) {
            theme.backgroundImageUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale =
                        when (theme.backgroundMode) {
                            RoomTheme.BackgroundMode.COVER -> ContentScale.Crop

                            // True repeat-tile would need a custom Modifier;
                            // FillBounds is a safe v1 fallback that doesn't
                            // crop and visually approximates a tile when the
                            // image is small + repeating.
                            RoomTheme.BackgroundMode.TILE -> ContentScale.FillBounds
                        },
                )
            }
            content()
        }
    }
}
