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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.location_picker_area
import com.vitorpamplona.amethyst.commons.resources.location_picker_confirm
import com.vitorpamplona.amethyst.commons.resources.location_picker_hint_map_only
import com.vitorpamplona.amethyst.commons.resources.location_picker_title
import com.vitorpamplona.amethyst.commons.resources.location_picker_zoom_in
import com.vitorpamplona.amethyst.commons.ui.note.creators.location.GeohashPickerDefaults
import com.vitorpamplona.amethyst.commons.ui.note.creators.location.areaSize
import com.vitorpamplona.amethyst.commons.ui.note.platform.LoadCityName
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.geohashChat.label
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashChannelLevel
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHash
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/**
 * The desktop's [GeohashLocationPickerDialog][com.vitorpamplona.amethyst.commons.ui.platform.AppPlatform.GeohashLocationPickerDialog]:
 * the picker in a dialog that leaves a margin of the window around it, the way a desktop dialog
 * sits over its app, rather than covering the whole window as Android's full-screen one does.
 */
@Composable
fun DesktopGeohashLocationPickerDialog(
    initialGeohash: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    precision: Int? = null,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier =
                Modifier
                    .widthIn(max = 960.dp)
                    .heightIn(max = 760.dp)
                    .fillMaxSize(0.92f),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringRes(Res.string.location_picker_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            symbol = MaterialSymbols.Close,
                            contentDescription = stringRes(Res.string.cancel),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                DesktopGeohashLocationPickerContent(
                    initialGeohash = initialGeohash,
                    confirmLabel = stringRes(Res.string.location_picker_confirm),
                    onConfirm = onConfirm,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    precision = precision,
                )
            }
        }
    }
}

/**
 * The desktop's [GeohashLocationPickerContent][com.vitorpamplona.amethyst.commons.ui.platform.AppPlatform.GeohashLocationPickerContent]:
 * the tile map with a fixed pin at its centre, the area-size chips, the chosen place, and a confirm
 * button labelled [confirmLabel] that hands [onConfirm] the geohash under the pin.
 *
 * Dragging the map, zooming it or pressing and holding a spot picks a place; the view the picker
 * opens on is not a pick unless it was seeded with [initialGeohash]. Android's place search and
 * "use my location" need a geocoder and a location provider the desktop doesn't have, so they are
 * left out rather than shown dead.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DesktopGeohashLocationPickerContent(
    initialGeohash: String?,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    modifier: Modifier = Modifier,
    precision: Int? = null,
) {
    // A geohash is lowercase; an uppercase or otherwise invalid seed decodes to nothing and the picker
    // opens unseeded.
    val seedText = remember(initialGeohash) { initialGeohash?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } }
    val seed = remember(seedText) { seedText?.let { GeoHash.decode(it) } }
    // A seed that matches no level exactly (3 characters, or a geocache's 9) shows the nearest one.
    val seedLevel = remember(seed) { if (seed != null && seedText != null) GeohashPickerDefaults.nearestLevel(seedText.length) else null }

    val state =
        remember(seed) {
            if (seed != null && seedText != null) {
                DesktopMapState(seed.centerLat, seed.centerLon, GeohashPickerDefaults.zoomForGeohashLength(seedText.length).roundToInt())
            } else {
                DesktopMapState(GeohashPickerDefaults.WORLD_CENTER_LAT, GeohashPickerDefaults.WORLD_CENTER_LON, GeohashPickerDefaults.WORLD_ZOOM.roundToInt())
            }
        }

    var level by remember(seed) { mutableStateOf(seedLevel ?: GeohashChannelLevel.CITY) }

    // Whether the view has moved since it opened: a drag, a zoom, a press and hold, or a new area
    // level reframing it. Watching the view itself, not [DesktopMapState.positioned], which is not
    // snapshot state and turns true even when a zoom at its limit moves nothing.
    var moved by remember(state) { mutableStateOf(false) }
    LaunchedEffect(state) {
        snapshotFlow { Triple(state.centerX, state.centerY, state.zoom) }.drop(1).first()
        moved = true
    }

    val cell by remember(state, seed, precision) {
        derivedStateOf {
            GeohashPickerDefaults.pickedCell(
                seedText = if (seed != null) seedText else null,
                seedLevel = seedLevel,
                level = level,
                precision = precision,
                moved = moved,
                // Untouched, the seed's own centre, not the map's, which Web Mercator clamps near the poles.
                lat = if (moved) latitudeOf(state.centerY) else seed?.centerLat,
                lon = if (moved) longitudeOf(state.centerX) else seed?.centerLon,
                zoom = state.zoom.toDouble(),
            )
        }
    }
    val needsZoomIn by remember(state, precision) {
        derivedStateOf { GeohashPickerDefaults.needsZoomIn(precision, moved, state.zoom.toDouble()) }
    }

    // Choosing a coarser or finer area frames the new cell, once there is a place to frame. Not on
    // open: the seed already opened at its own zoom.
    LaunchedEffect(state) {
        snapshotFlow { level }.drop(1).collect { newLevel ->
            if (seed != null || moved) {
                val zoom = GeohashPickerDefaults.zoomForGeohashLength(newLevel.chars).roundToInt()
                state.centerOn(latitudeOf(state.centerY), longitudeOf(state.centerX), zoom)
            }
        }
    }

    // A drag changes the cell every few pixels; name the place only once the map settles.
    var settledCell by remember(state) { mutableStateOf(cell) }
    LaunchedEffect(cell) {
        delay(450)
        settledCell = cell
    }

    Column(modifier) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            DesktopTileMap(
                state = state,
                markers = emptyList(),
                modifier = Modifier.fillMaxSize(),
                wheelZoom = true,
                controlsAlignment = Alignment.TopEnd,
                onLongPress = { lat, lon -> state.centerOn(lat, lon) },
            )
            CenterPin(Modifier.align(Alignment.Center))
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
            // A caller that fixes the precision (a geocache) picks a point, not an area.
            if (precision == null) {
                Text(
                    text = stringRes(Res.string.location_picker_area),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GeohashChannelLevel.ordered.forEach { option ->
                        FilterChip(
                            selected = option == level,
                            onClick = { level = option },
                            label = { Text("${option.label()} · ${option.areaSize()}") },
                        )
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    val current = cell
                    if (current == null) {
                        Text(
                            text = stringRes(if (needsZoomIn) Res.string.location_picker_zoom_in else Res.string.location_picker_hint_map_only),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        // Name only the settled cell, so a name never sits beside a newer geohash, and
                        // drop the geohash the lookup echoes back when it knows no name for it.
                        if (settledCell == current) {
                            LoadCityName(geohashStr = current) { cityName ->
                                if (cityName != current) {
                                    Text(cityName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                }
                            }
                        }
                        Text("#$current", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Button(onClick = { cell?.let(onConfirm) }, enabled = cell != null) {
                    Text(confirmLabel, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** The pin fixed over the map's centre: the point under its tip is the pick. */
@Composable
private fun CenterPin(modifier: Modifier = Modifier) {
    Box(modifier.size(56.dp), contentAlignment = Alignment.Center) {
        Icon(
            symbol = MaterialSymbols.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(46.dp).offset(y = (-20).dp),
        )
        Box(
            Modifier
                .size(7.dp)
                .shadow(2.dp, CircleShape)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
    }
}
