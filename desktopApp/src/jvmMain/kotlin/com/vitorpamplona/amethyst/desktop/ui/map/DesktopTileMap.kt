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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
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
import kotlin.math.abs
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.tan

/*
 * The desktop's map engine: OpenStreetMap tiles laid out in Compose, with pins on top. Tiles load
 * through the app's Coil loader, so they carry the app's User-Agent (the OSM tile policy asks for
 * one), share the image disk cache and follow the Tor setting like every other image. Android draws
 * the same maps with osmdroid; the tile filters and the pin are Android's.
 */

/** OpenStreetMap's standard tiles, the ones osmdroid's MAPNIK source draws on Android. */
internal fun osmTileUrl(
    zoom: Int,
    x: Int,
    y: Int,
) = "https://tile.openstreetmap.org/$zoom/$x/$y.png"

private const val TILE_PIXELS = 256
internal const val MIN_ZOOM = 2
internal const val MAX_ZOOM = 18
private const val WEB_MERCATOR_MAX_LAT = 85.05112878

/** The point as a fraction of the Web Mercator world, 0..1 on both axes (y grows south). */
internal fun mercatorX(lon: Double) = ((lon + 180.0) / 360.0).mod(1.0)

internal fun mercatorY(lat: Double): Double {
    val clamped = lat.coerceIn(-WEB_MERCATOR_MAX_LAT, WEB_MERCATOR_MAX_LAT)
    return (1.0 - asinh(tan(clamped * PI / 180.0)) / PI) / 2.0
}

internal fun longitudeOf(x: Double) = x * 360.0 - 180.0

internal fun latitudeOf(y: Double) = atan(sinh(PI * (1.0 - 2.0 * y))) * 180.0 / PI

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
 * Where the map looks: its centre as a world fraction, so a zoom keeps the same spot in the middle.
 * A point that isn't a number (an event's `NaN` coordinates, say) is no place to look at: the map
 * stays where it was, or at 0,0.
 */
@Stable
class DesktopMapState(
    lat: Double,
    lon: Double,
    zoom: Int,
) {
    var centerX by mutableDoubleStateOf(if (lon.isFinite()) mercatorX(lon) else 0.5)
    var centerY by mutableDoubleStateOf(if (lat.isFinite()) mercatorY(lat) else 0.5)
    var zoom by mutableIntStateOf(zoom.coerceIn(MIN_ZOOM, MAX_ZOOM))

    /** False until the map is placed on purpose: a caller centring it, or the reader moving it. */
    var positioned = false

    fun centerOn(
        lat: Double,
        lon: Double,
        zoom: Int = this.zoom,
    ) {
        if (!lat.isFinite() || !lon.isFinite()) return
        centerX = mercatorX(lon)
        centerY = mercatorY(lat)
        this.zoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        positioned = true
    }

    fun zoomBy(delta: Int) {
        zoom = (zoom + delta).coerceIn(MIN_ZOOM, MAX_ZOOM)
        positioned = true
    }

    /** Moves the view by a drag, east and west around the world, north and south no further than its edge. */
    internal fun panBy(
        dx: Double,
        dy: Double,
        worldPx: Double,
        viewHeightPx: Float,
    ) {
        centerX = (centerX - dx / worldPx).mod(1.0)
        val half = viewHeightPx / 2 / worldPx
        centerY = if (half >= 0.5) 0.5 else (centerY - dy / worldPx).coerceIn(half, 1.0 - half)
        positioned = true
    }

    /** The world pixel at the view's left edge, for a world [worldPx] wide and a view [viewWidthPx] wide. */
    internal fun leftPx(
        worldPx: Double,
        viewWidthPx: Float,
    ) = centerX * worldPx - viewWidthPx / 2

    internal fun topPx(
        worldPx: Double,
        viewHeightPx: Float,
    ) = centerY * worldPx - viewHeightPx / 2
}

@Composable
fun rememberDesktopMapState(
    lat: Double,
    lon: Double,
    zoom: Int,
): DesktopMapState = remember(lat, lon, zoom) { DesktopMapState(lat, lon, zoom) }

/**
 * A pin. [label] is the emoji (or a cluster's count) on its head, else a white dot. [centered]
 * stands it on its centre (a cluster) instead of on its point (a place).
 */
@Immutable
class MapMarker(
    val key: Any,
    val lat: Double,
    val lon: Double,
    val color: Color,
    val label: String? = null,
    val alpha: Float = 1f,
    val centered: Boolean = false,
    val onClick: (() -> Unit)? = null,
)

/**
 * The map. Drag pans; the buttons zoom, and so does the wheel when [wheelZoom] (a map that fills a
 * screen; a map inside a scrolling page leaves the wheel to the page). [onTap] and [onLongPress]
 * get taps on the map itself, not on a pin; a long press says where, in degrees.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DesktopTileMap(
    state: DesktopMapState,
    markers: List<MapMarker>,
    modifier: Modifier = Modifier,
    wheelZoom: Boolean = false,
    onTap: (() -> Unit)? = null,
    onLongPress: ((lat: Double, lon: Double) -> Unit)? = null,
    // Where the zoom buttons sit: out of the way of whatever the screen floats over the map.
    controlsAlignment: Alignment = Alignment.BottomEnd,
    tileUrl: (zoom: Int, x: Int, y: Int) -> String = ::osmTileUrl,
) {
    val dark = !MaterialTheme.colorScheme.isLight
    val tileFilter = if (dark) NightTileFilter else MutedTileFilter

    BoxWithConstraints(
        modifier =
            modifier
                .clipToBounds()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        val density = LocalDensity.current
        val tilePx = with(density) { TILE_PIXELS.dp.toPx() }
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val zoom = state.zoom
        val tiles = 1 shl zoom
        val worldPx = tiles * tilePx.toDouble()

        // The tiles in view. A drag moves the centre every frame, but this only changes when the
        // view crosses a tile's edge, so a drag recomposes the tiles that often and otherwise only
        // lays them out again (the offsets below read the centre in the layout phase).
        val range by remember(state, zoom, widthPx, heightPx, tilePx) {
            derivedStateOf {
                val left = state.leftPx(worldPx, widthPx)
                val top = state.topPx(worldPx, heightPx)
                IntRect(
                    left = floor(left / tilePx).toInt(),
                    top = floor(top / tilePx).toInt().coerceAtLeast(0),
                    right = floor((left + widthPx) / tilePx).toInt(),
                    bottom = floor((top + heightPx) / tilePx).toInt().coerceAtMost(tiles - 1),
                )
            }
        }
        // One wheel step per notch: a trackpad sends dozens of small deltas a swipe.
        val wheel = remember { WheelSteps() }

        Box(
            Modifier
                .matchParentSize()
                .pointerInput(state, tilePx) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        state.panBy(drag.x.toDouble(), drag.y.toDouble(), (1 shl state.zoom) * tilePx.toDouble(), size.height.toFloat())
                    }
                }.pointerInput(state, tilePx, onTap, onLongPress) {
                    detectTapGestures(
                        onTap = onTap?.let { tap -> { tap() } },
                        onLongPress =
                            onLongPress?.let { press ->
                                { at ->
                                    // The view's size now, not when the gesture began listening.
                                    val world = (1 shl state.zoom) * tilePx.toDouble()
                                    val x = (state.leftPx(world, size.width.toFloat()) + at.x) / world
                                    val y = (state.topPx(world, size.height.toFloat()) + at.y) / world
                                    press(latitudeOf(y.coerceIn(0.0, 1.0)), longitudeOf(x.mod(1.0)))
                                }
                            },
                    )
                }.then(
                    if (wheelZoom) {
                        Modifier.onPointerEvent(PointerEventType.Scroll) { event ->
                            val change = event.changes.firstOrNull() ?: return@onPointerEvent
                            wheel.step(change.scrollDelta.y, change.uptimeMillis)?.let { state.zoomBy(it) }
                            event.changes.forEach { it.consume() }
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            val shown = range
            for (ty in shown.top..shown.bottom) {
                for (tx in shown.left..shown.right) {
                    key(tx, ty) {
                        // The world repeats east and west.
                        AsyncImage(
                            model = tileUrl(zoom, tx.mod(tiles), ty),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            colorFilter = tileFilter,
                            modifier =
                                Modifier
                                    .offset {
                                        IntOffset(
                                            (tx * tilePx - state.leftPx(worldPx, widthPx)).roundToInt(),
                                            (ty * tilePx - state.topPx(worldPx, heightPx)).roundToInt(),
                                        )
                                    }.size(TILE_PIXELS.dp),
                        )
                    }
                }
            }

            markers.forEach { marker ->
                // A point that isn't a number has nowhere to stand.
                if (!marker.lat.isFinite() || !marker.lon.isFinite()) return@forEach
                key(marker.key) {
                    val anchorY = if (marker.centered) PIN_HEIGHT / 2 else PIN_HEIGHT
                    val worldX = mercatorX(marker.lon) * worldPx
                    val worldY = mercatorY(marker.lat) * worldPx
                    Pin(
                        color = marker.color,
                        label = marker.label,
                        alpha = marker.alpha,
                        onClick = marker.onClick,
                        modifier =
                            Modifier.offset {
                                // The copy of the world nearest the view's centre, so a pin across the
                                // antimeridian from it still shows.
                                val fromLeft = worldX - state.leftPx(worldPx, widthPx)
                                val x = widthPx / 2 + (fromLeft - widthPx / 2 + worldPx / 2).mod(worldPx) - worldPx / 2
                                val y = worldY - state.topPx(worldPx, heightPx)
                                IntOffset((x - PIN_WIDTH.toPx() / 2).roundToInt(), (y - anchorY.toPx()).roundToInt())
                            },
                    )
                }
            }
        }

        Column(
            modifier = Modifier.align(controlsAlignment).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FilledTonalIconButton(onClick = { state.zoomBy(1) }, modifier = Modifier.size(32.dp)) {
                Icon(symbol = MaterialSymbols.Add, contentDescription = stringRes(Res.string.map_zoom_in), modifier = Modifier.size(18.dp))
            }
            FilledTonalIconButton(onClick = { state.zoomBy(-1) }, modifier = Modifier.size(32.dp)) {
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

/**
 * Turns wheel deltas into zoom steps: one level once a notch's worth has gathered, no more than one
 * per [WHEEL_STEP_MILLIS], so a trackpad swipe zooms a level or two instead of flying to the end.
 */
internal class WheelSteps {
    private var gathered = 0f
    private var lastStepAt: Long? = null

    fun step(
        deltaY: Float,
        atMillis: Long,
    ): Int? {
        gathered += deltaY
        if (abs(gathered) < 1f) return null
        val last = lastStepAt
        if (last != null && atMillis - last < WHEEL_STEP_MILLIS) return null
        val step = if (gathered < 0) 1 else -1
        gathered = 0f
        lastStepAt = atMillis
        return step
    }
}

private const val WHEEL_STEP_MILLIS = 120L

private val PIN_WIDTH: Dp = 34.dp
private val PIN_HEIGHT: Dp = 46.dp

/** Android's pin (`MapPinIcon.kt`): a coloured teardrop with a white ring, the label on its head when there is one. */
@Composable
private fun Pin(
    color: Color,
    label: String?,
    alpha: Float,
    onClick: (() -> Unit)?,
    modifier: Modifier,
) {
    Box(
        modifier
            .size(PIN_WIDTH, PIN_HEIGHT)
            .alpha(alpha)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.TopCenter,
    ) {
        Spacer(
            Modifier.size(PIN_WIDTH, PIN_HEIGHT).drawWithCache {
                // Built once per size, not on every frame a drag draws.
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
                val ring = Stroke(width = 2.5.dp.toPx())
                onDrawBehind {
                    drawPath(body, color)
                    drawPath(body, Color.White, style = ring)
                    if (label == null) drawCircle(Color.White, radius * 0.35f, Offset(cx, cy))
                }
            },
        )
        if (label != null) {
            Text(text = label, fontSize = 15.sp, color = Color.White, modifier = Modifier.padding(top = 7.dp))
        }
    }
}
