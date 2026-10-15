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
package com.vitorpamplona.amethyst.desktop.ui.map

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

private const val DEFAULT_ZOOM = 16.0

/**
 * The desktop's [LocationPreviewMap][com.vitorpamplona.amethyst.commons.ui.note.platform.NotePlatform.LocationPreviewMap]:
 * one pin, centred, in a box of [aspectRatio]. It sits inside scrolling pages (a place, a geocache,
 * a relay group), so the mouse wheel stays with the page, as on Brainstorm's place page.
 */
@Composable
fun DesktopLocationPreviewMap(
    latitude: Double,
    longitude: Double,
    modifier: Modifier,
    zoom: Double?,
    aspectRatio: Float,
    pinColor: Color?,
    pinEmoji: String?,
    pinAlpha: Float,
    tileUrl: (zoom: Int, x: Int, y: Int) -> String = ::osmTileUrl,
) {
    val state = rememberDesktopMapState(latitude, longitude, (zoom ?: DEFAULT_ZOOM).roundToInt())
    val color = pinColor ?: MaterialTheme.colorScheme.primary
    val markers = remember(latitude, longitude, color, pinEmoji, pinAlpha) { listOf(MapMarker("pin", latitude, longitude, color, pinEmoji, pinAlpha)) }
    DesktopTileMap(
        state = state,
        markers = markers,
        modifier = modifier.fillMaxWidth().aspectRatio(aspectRatio),
        tileUrl = tileUrl,
    )
}
