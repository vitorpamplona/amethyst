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
package com.vitorpamplona.amethyst.commons.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.location.DeviceLocation
import com.vitorpamplona.amethyst.commons.model.location.LocationResult
import com.vitorpamplona.amethyst.commons.model.location.PlaceHit
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.clear
import com.vitorpamplona.amethyst.commons.resources.location_picker_area
import com.vitorpamplona.amethyst.commons.resources.location_picker_confirm
import com.vitorpamplona.amethyst.commons.resources.location_picker_hint_map_only
import com.vitorpamplona.amethyst.commons.resources.location_picker_search_empty
import com.vitorpamplona.amethyst.commons.resources.location_picker_search_hint
import com.vitorpamplona.amethyst.commons.resources.location_picker_title
import com.vitorpamplona.amethyst.commons.resources.location_picker_use_mine
import com.vitorpamplona.amethyst.commons.resources.location_picker_zoom_in
import com.vitorpamplona.amethyst.commons.ui.components.rememberCoarseLocationPermission
import com.vitorpamplona.amethyst.commons.ui.note.creators.location.GeohashPickerDefaults
import com.vitorpamplona.amethyst.commons.ui.note.creators.location.areaSize
import com.vitorpamplona.amethyst.commons.ui.note.platform.LoadCityName
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.geohashChat.label
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashChannelLevel
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHash
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * The desktop's and iOS's [GeohashLocationPickerDialog][com.vitorpamplona.amethyst.commons.ui.platform.AppPlatform.GeohashLocationPickerDialog]:
 * the picker in a dialog that leaves a margin of the window around it, the way a desktop dialog
 * sits over its app (on a phone the margin is a thin border of the screen).
 */
@Composable
fun TileGeohashLocationPickerDialog(
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
                TileGeohashLocationPickerContent(
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
 * The desktop's and iOS's [GeohashLocationPickerContent][com.vitorpamplona.amethyst.commons.ui.platform.AppPlatform.GeohashLocationPickerContent]:
 * the tile map with a fixed pin at its centre, the area-size chips, the chosen place, and a confirm
 * button labelled [confirmLabel] that hands [onConfirm] the geohash under the pin.
 *
 * Dragging the map, zooming it or pressing and holding a spot picks a place; the view the picker
 * opens on is not a pick unless it was seeded with [initialGeohash]. Where the platform has them,
 * as Android's picker does, a place search ([AppServices.hasPlaceSearch]) flies the map to a name,
 * and "use my location" ([AppServices.deviceLocation]) to the device's position; the desktop has
 * neither a geocoder nor a location provider, so there they are left out rather than shown dead.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TileGeohashLocationPickerContent(
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
                TileMapState(seed.centerLat, seed.centerLon, GeohashPickerDefaults.zoomForGeohashLength(seedText.length).roundToInt())
            } else {
                TileMapState(GeohashPickerDefaults.WORLD_CENTER_LAT, GeohashPickerDefaults.WORLD_CENTER_LON, GeohashPickerDefaults.WORLD_ZOOM.roundToInt())
            }
        }

    var level by remember(seed) { mutableStateOf(seedLevel ?: GeohashChannelLevel.CITY) }

    // Whether the view has moved since it opened: a drag, a zoom, a press and hold, or a new area
    // level reframing it. Watching the view itself, not [TileMapState.positioned], which is not
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

    val services = LocalAppServices.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    // A search hit or a fix flies in close enough to place a fixed-precision pin.
    val recenterZoom = maxOf(RECENTER_ZOOM, precision?.let { GeohashPickerDefaults.zoomForGeohashLength(it) } ?: RECENTER_ZOOM).roundToInt()

    // "Use my location": the device's position, where the platform has one. Asking for the
    // permission waits for the answer; a grant then fetches the fix.
    val deviceLocation = services.deviceLocation
    val canLocate = deviceLocation !== DeviceLocation.None
    val permission = rememberCoarseLocationPermission()
    var wantsMyLocation by remember { mutableStateOf(false) }
    var askedPermission by remember { mutableStateOf(false) }
    LaunchedEffect(permission.isGranted) {
        deviceLocation.setLocationPermission(permission.isGranted)
        if (askedPermission && permission.isGranted) {
            askedPermission = false
            wantsMyLocation = true
        }
    }
    // Opened with no seed and the permission already granted: start at the device's position
    // rather than the world view. Never prompts.
    var autoLocated by remember { mutableStateOf(false) }
    LaunchedEffect(permission.isGranted) {
        if (canLocate && !autoLocated && seed == null && permission.isGranted) {
            autoLocated = true
            wantsMyLocation = true
        }
    }
    LaunchedEffect(wantsMyLocation) {
        if (wantsMyLocation) {
            // Bail on a timeout so the button never spins forever (location off, indoors, no fix).
            val fix =
                withTimeoutOrNull(GPS_FIX_TIMEOUT_MS) {
                    deviceLocation.preciseGeohashStateFlow.first { it is LocationResult.Success }
                } as? LocationResult.Success
            if (fix != null) state.centerOn(fix.geoHash.centerLat, fix.geoHash.centerLon, recenterZoom)
            wantsMyLocation = false
        }
    }
    val onUseMyLocation = {
        if (permission.isGranted) {
            wantsMyLocation = true
        } else {
            askedPermission = true
            permission.launchRequest()
        }
    }

    // Place search by name, shown as a pick list; choosing a hit flies there.
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var searchMissed by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<PlaceHit>>(emptyList()) }
    val runSearch = {
        val q = query.trim()
        if (q.isNotEmpty()) {
            keyboard?.hide()
            searching = true
            searchMissed = false
            results = emptyList()
            scope.launch {
                val hits = runCatchingCancellable { services.searchPlaces(q) }.getOrDefault(emptyList())
                searching = false
                results = hits
                searchMissed = hits.isEmpty()
            }
        }
    }
    val selectResult: (PlaceHit) -> Unit = { hit ->
        state.centerOn(hit.latitude, hit.longitude, recenterZoom)
        results = emptyList()
        query = ""
        keyboard?.hide()
    }

    // A drag changes the cell every few pixels; name the place only once the map settles.
    var settledCell by remember(state) { mutableStateOf(cell) }
    LaunchedEffect(cell) {
        delay(450)
        settledCell = cell
    }

    Column(modifier) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            TileMap(
                state = state,
                markers = emptyList(),
                modifier = Modifier.fillMaxSize(),
                wheelZoom = true,
                // The search bar takes the top where there is one.
                controlsAlignment = if (services.hasPlaceSearch) Alignment.CenterEnd else Alignment.TopEnd,
                onLongPress = { lat, lon -> state.centerOn(lat, lon) },
            )
            CenterPin(Modifier.align(Alignment.Center))

            if (services.hasPlaceSearch) {
                Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp)) {
                    SearchField(
                        query = query,
                        onQueryChange = {
                            query = it
                            searchMissed = false
                        },
                        onSearch = runSearch,
                        searching = searching,
                        missed = searchMissed,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (results.isNotEmpty()) {
                        SearchResults(results = results, onSelect = selectResult, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    }
                }
            }

            if (canLocate) {
                MyLocationButton(
                    loading = wantsMyLocation,
                    onClick = onUseMyLocation,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                )
            }
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

/** Zoom the map flies to after a search hit or a "use my location" tap. */
private const val RECENTER_ZOOM = 14.0

/** Give up waiting for a location fix after this long, so the button never spins forever. */
private const val GPS_FIX_TIMEOUT_MS = 20_000L

/** The place search over the map, as a pill. */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    searching: Boolean,
    missed: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.shadow(6.dp, RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = { Text(stringRes(Res.string.location_picker_search_hint)) },
            leadingIcon = {
                Icon(
                    symbol = MaterialSymbols.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = {
                if (searching) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            symbol = MaterialSymbols.Close,
                            contentDescription = stringRes(Res.string.clear),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            isError = missed,
            supportingText = if (missed) ({ Text(stringRes(Res.string.location_picker_search_empty)) }) else null,
            // The pill comes from the Surface; the field's own rectangle would poke through it.
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    errorBorderColor = Color.Transparent,
                ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The search hits under the search field. */
@Composable
private fun SearchResults(
    results: List<PlaceHit>,
    onSelect: (PlaceHit) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.shadow(6.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
            results.forEachIndexed { index, hit ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(hit) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        symbol = MaterialSymbols.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(text = hit.label, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun MyLocationButton(
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledIconButton(
        onClick = onClick,
        enabled = !loading,
        colors =
            IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
            ),
        modifier = modifier.shadow(6.dp, CircleShape).size(52.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            Icon(
                symbol = MaterialSymbols.MyLocation,
                contentDescription = stringRes(Res.string.location_picker_use_mine),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
