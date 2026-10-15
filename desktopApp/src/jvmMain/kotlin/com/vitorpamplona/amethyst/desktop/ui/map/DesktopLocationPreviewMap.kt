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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.map_osm_attribution
import com.vitorpamplona.amethyst.commons.resources.map_zoom_in
import com.vitorpamplona.amethyst.commons.resources.map_zoom_out
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.isLight
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.tan

/** OpenStreetMap's standard tiles, the ones osmdroid's MAPNIK source draws on Android. */
internal fun osmTileUrl(
    zoom: Int,
    x: Int,
    y: Int,
) = "https://tile.openstreetmap.org/$zoom/$x/$y.png"

private const val TILE_PIXELS = 256
private const val MIN_ZOOM = 3
private const val MAX_ZOOM = 18
private const val DEFAULT_ZOOM = 16.0

/** The point as a fraction of the Web Mercator world, 0..1 on both axes (y grows south). */
internal fun mercatorX(lon: Double) = (lon + 180.0) / 360.0

internal fun mercatorY(lat: Double): Double {
    val clamped = lat.coerceIn(-85.05112878, 85.05112878)
    return (1.0 - asinh(tan(clamped * PI / 180.0)) / PI) / 2.0
}

/**
 * Android's tile filters (`LocationPreviewMap.kt` in `amethyst`): dark mode inverts the tiles'
 * lightness keeping their hue, light mode mutes the busy MAPNIK colours.
 */
private val NightTileFilter =
    ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0.574f,
                -1.430f,
                -0.144f,
                0f,
                255f,
                -0.426f,
                -0.430f,
                -0.144f,
                0f,
                255f,
                -0.426f,
                -1.430f,
                0.856f,
                0f,
                255f,
                0f,
                0f,
                0f,
                1f,
                0f,
            ),
        ),
    )

private val MutedTileFilter =
    ColorFilter.colorMatrix(
        ColorMatrix().apply {
            setToSaturation(0.55f)
            timesAssign(
                ColorMatrix(
                    floatArrayOf(
                        0.92f,
                        0f,
                        0f,
                        0f,
                        16f,
                        0f,
                        0.92f,
                        0f,
                        0f,
                        16f,
                        0f,
                        0f,
                        0.92f,
                        0f,
                        16f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f,
                    ),
                ),
            )
        },
    )

/**
 * The desktop's [LocationPreviewMap][com.vitorpamplona.amethyst.commons.ui.note.platform.NotePlatform.LocationPreviewMap]:
 * OpenStreetMap tiles laid out in Compose, centred on the point with a pin on it. Tiles load
 * through the app's Coil loader, so they carry the app's User-Agent (the OSM tile policy asks for
 * one), share the image disk cache and follow the Tor setting like every other image.
 *
 * Drag pans; the buttons zoom. The mouse wheel is left to the page, so scrolling a feed past the
 * map keeps scrolling, as on Brainstorm's place page.
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
    val dark = !MaterialTheme.colorScheme.isLight
    val tileFilter = if (dark) NightTileFilter else MutedTileFilter
    val pin = pinColor ?: MaterialTheme.colorScheme.primary

    // The view's centre as a world fraction, so zooming keeps the same spot in the middle.
    var zoomLevel by remember(latitude, longitude, zoom) { mutableIntStateOf((zoom ?: DEFAULT_ZOOM).roundToInt().coerceIn(MIN_ZOOM, MAX_ZOOM)) }
    var centerX by remember(latitude, longitude) { mutableDoubleStateOf(mercatorX(longitude)) }
    var centerY by remember(latitude, longitude) { mutableDoubleStateOf(mercatorY(latitude)) }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .clipToBounds()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        val density = LocalDensity.current
        val tilePx = with(density) { TILE_PIXELS.dp.toPx() }
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val tiles = 1 shl zoomLevel
        val worldPx = tiles * tilePx.toDouble()

        // The world pixel at the view's top left corner.
        val left = centerX * worldPx - widthPx / 2
        val top = centerY * worldPx - heightPx / 2

        Box(
            Modifier
                .matchParentSize()
                .pointerInput(zoomLevel) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        val world = (1 shl zoomLevel) * tilePx.toDouble()
                        centerX = (centerX - drag.x / world).mod(1.0)
                        centerY = (centerY - drag.y / world).coerceIn(0.0, 1.0)
                    }
                },
        ) {
            val firstX = floor(left / tilePx).toInt()
            val lastX = floor((left + widthPx) / tilePx).toInt()
            val firstY = floor(top / tilePx).toInt().coerceAtLeast(0)
            val lastY = floor((top + heightPx) / tilePx).toInt().coerceAtMost(tiles - 1)
            for (ty in firstY..lastY) {
                for (tx in firstX..lastX) {
                    // The world repeats east and west.
                    val wrappedX = tx.mod(tiles)
                    val url = tileUrl(zoomLevel, wrappedX, ty)
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        colorFilter = tileFilter,
                        modifier =
                            Modifier
                                .offset { IntOffset((tx * tilePx - left).roundToInt(), (ty * tilePx - top).roundToInt()) }
                                .size(TILE_PIXELS.dp),
                    )
                }
            }

            // The pin, its point on the place.
            val pinX = (mercatorX(longitude) * worldPx - left).toFloat()
            val pinY = (mercatorY(latitude) * worldPx - top).toFloat()
            Pin(pin, pinEmoji, pinAlpha, Modifier.offset { IntOffset((pinX - PIN_WIDTH.toPx() / 2).roundToInt(), (pinY - PIN_HEIGHT.toPx()).roundToInt()) })
        }

        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FilledTonalIconButton(onClick = { if (zoomLevel < MAX_ZOOM) zoomLevel++ }, modifier = Modifier.size(32.dp)) {
                Icon(symbol = MaterialSymbols.Add, contentDescription = stringRes(Res.string.map_zoom_in), modifier = Modifier.size(18.dp))
            }
            FilledTonalIconButton(onClick = { if (zoomLevel > MIN_ZOOM) zoomLevel-- }, modifier = Modifier.size(32.dp)) {
                Icon(symbol = MaterialSymbols.Remove, contentDescription = stringRes(Res.string.map_zoom_out), modifier = Modifier.size(18.dp))
            }
        }

        // The tile policy asks for the attribution on the map itself.
        Text(
            text = stringRes(Res.string.map_osm_attribution),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f), RoundedCornerShape(topEnd = 4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

private val PIN_WIDTH = 34.dp
private val PIN_HEIGHT = 46.dp

/** Android's pin (`MapPinIcon.kt`): a coloured teardrop with a white ring, the emoji on its head when there is one. */
@Composable
private fun Pin(
    color: Color,
    emoji: String?,
    alpha: Float,
    modifier: Modifier,
) {
    Box(modifier.size(PIN_WIDTH, PIN_HEIGHT).alpha(alpha), contentAlignment = Alignment.TopCenter) {
        Canvas(Modifier.size(PIN_WIDTH, PIN_HEIGHT)) {
            val radius = size.width / 2f * 0.9f
            val cx = size.width / 2f
            val cy = radius + 1.dp.toPx()
            val head = Path().apply { addOval(Rect(Offset(cx, cy), radius)) }
            val mouth = radius * 0.7f
            val tail =
                Path().apply {
                    moveTo(cx - mouth, cy + radius * 0.55f)
                    lineTo(cx + mouth, cy + radius * 0.55f)
                    lineTo(cx, size.height)
                    close()
                }
            // One outline, so the ring doesn't cross the head where the tail joins it.
            val body = Path().apply { op(head, tail, PathOperation.Union) }
            drawPath(body, color)
            drawPath(body, Color.White, style = Stroke(width = 2.5.dp.toPx()))
            if (emoji == null) drawCircle(Color.White, radius * 0.35f, Offset(cx, cy))
        }
        if (emoji != null) {
            Text(text = emoji, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
