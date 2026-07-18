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
package com.vitorpamplona.amethyst.ui.note.creators.location

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.service.location.ForwardGeolocation
import com.vitorpamplona.amethyst.service.location.LocationState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.geohashChat.label
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashChannelLevel
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHash
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.osmdroid.util.GeoPoint

/** Zoom the map animates to after a search hit or a "use my location" tap. */
private const val RECENTER_ZOOM = 14.0

/** How close to zoom in when the picker opens already holding a location. */
private const val SEEDED_ZOOM = 13.0

/** How far out to start when the picker opens with nothing selected yet. */
private const val WORLD_ZOOM = 2.5

/**
 * A full-screen, map-first location picker that produces a geohash string.
 *
 * The user has three ways to land on a place — pan the map under the fixed center
 * pin, search for a place by name (forward geocoding), or tap "use my location"
 * (device GPS) — and never has to know what a geohash is. The chosen precision
 * ([GeohashChannelLevel]) controls how many characters the resulting geohash has,
 * i.e. how large an area the group claims. On confirm, [onConfirm] receives the
 * encoded geohash (e.g. `u4pruy`).
 *
 * Reuses [LocationPickerMap] (in center-pin mode), [LoadCityName] for the
 * human-readable place name, and the app-wide [LocationState] for GPS. Modeled on
 * the Geohash-chat Teleport screen, promoted here into a reusable dialog.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun GeohashLocationPickerDialog(
    initialGeohash: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val locationManager = Amethyst.instance.locationManager

    val seed = remember(initialGeohash) { initialGeohash?.takeIf { it.isNotBlank() }?.let { GeoHash.decode(it) } }
    val seedLen = initialGeohash?.trim()?.length ?: 0

    var pickedLat by remember { mutableStateOf(seed?.centerLat) }
    var pickedLon by remember { mutableStateOf(seed?.centerLon) }
    var level by remember {
        mutableStateOf(GeohashChannelLevel.forChars(seedLen) ?: GeohashChannelLevel.CITY)
    }
    var recenter by remember { mutableStateOf<GeoPoint?>(null) }

    val cell =
        remember(pickedLat, pickedLon, level) {
            val lat = pickedLat
            val lon = pickedLon
            if (lat != null && lon != null) GeoHash.encode(lat, lon, level.chars).toString() else null
        }

    // Debounce the reverse-geocode: panning changes [cell] constantly, and we don't
    // want to hammer the Geocoder — only resolve a place name once the map settles.
    var settledCell by remember { mutableStateOf(cell) }
    LaunchedEffect(cell) {
        delay(450)
        settledCell = cell
    }

    // "Use my location": tapping either fires the fetch (permission already granted) or
    // asks for it; [awaitingPermission] carries the intent across the system dialog so a
    // grant auto-starts the fetch, while a denial simply drops the request (no stuck spinner).
    val permission = rememberPermissionState(Manifest.permission.ACCESS_COARSE_LOCATION)
    var wantsMyLocation by remember { mutableStateOf(false) }
    var awaitingPermission by remember { mutableStateOf(false) }
    LaunchedEffect(permission.status.isGranted) {
        locationManager.setLocationPermission(permission.status.isGranted)
        if (permission.status.isGranted && awaitingPermission) {
            awaitingPermission = false
            wantsMyLocation = true
        } else if (!permission.status.isGranted) {
            awaitingPermission = false
        }
    }
    LaunchedEffect(wantsMyLocation) {
        if (wantsMyLocation) {
            val fix = locationManager.preciseGeohashStateFlow.first { it is LocationState.LocationResult.Success }
            val hash = (fix as LocationState.LocationResult.Success).geoHash
            recenter = GeoPoint(hash.centerLat, hash.centerLon)
            pickedLat = hash.centerLat
            pickedLon = hash.centerLon
            wantsMyLocation = false
        }
    }
    val onUseMyLocation = {
        if (permission.status.isGranted) {
            wantsMyLocation = true
        } else {
            awaitingPermission = true
            permission.launchPermissionRequest()
        }
    }

    // Forward-geocode search.
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var searchMissed by remember { mutableStateOf(false) }
    val runSearch = {
        val q = query.trim()
        if (q.isNotEmpty()) {
            keyboard?.hide()
            searching = true
            searchMissed = false
            ForwardGeolocation.execute(q, context) { addresses ->
                searching = false
                val hit = addresses?.firstOrNull()
                if (hit != null) {
                    recenter = GeoPoint(hit.latitude, hit.longitude)
                    pickedLat = hit.latitude
                    pickedLon = hit.longitude
                } else {
                    searchMissed = true
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                PickerHeader(onClose = onDismiss)

                Box(Modifier.fillMaxWidth().weight(1f)) {
                    LocationPickerMap(
                        latitude = seed?.centerLat ?: 20.0,
                        longitude = seed?.centerLon ?: 0.0,
                        pickedLatitude = null,
                        pickedLongitude = null,
                        zoom = if (seed != null) SEEDED_ZOOM else WORLD_ZOOM,
                        recenter = recenter,
                        recenterZoom = RECENTER_ZOOM,
                        onCenterChanged = { lat, lon ->
                            pickedLat = lat
                            pickedLon = lon
                        },
                        onPick = { lat, lon ->
                            recenter = GeoPoint(lat, lon)
                            pickedLat = lat
                            pickedLon = lon
                        },
                        modifier = Modifier.fillMaxSize(),
                    )

                    CenterPin(Modifier.align(Alignment.Center))

                    SearchField(
                        query = query,
                        onQueryChange = {
                            query = it
                            searchMissed = false
                        },
                        onSearch = runSearch,
                        searching = searching,
                        missed = searchMissed,
                        modifier =
                            Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .padding(12.dp),
                    )

                    MyLocationButton(
                        loading = wantsMyLocation || awaitingPermission,
                        onClick = onUseMyLocation,
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp),
                    )
                }

                PickerBottomBar(
                    cell = cell,
                    settledCell = settledCell,
                    level = level,
                    onLevel = { level = it },
                    onConfirm = { cell?.let(onConfirm) },
                )
            }
        }
    }
}

@Composable
private fun PickerHeader(onClose: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    symbol = MaterialSymbols.Close,
                    contentDescription = stringRes(R.string.cancel),
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = stringRes(R.string.relay_group_location_picker_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/** The fixed pin that hovers over the map center; the point under its tip is the selection. */
@Composable
private fun CenterPin(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(48.dp), contentAlignment = Alignment.Center) {
        // The pin's tip sits at the map center; lift the whole glyph up by half its height.
        Icon(
            symbol = MaterialSymbols.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier
                    .size(46.dp)
                    .offset(y = (-20).dp),
        )
        // A small anchor dot marking the exact center point.
        Box(
            Modifier
                .size(7.dp)
                .shadow(2.dp, CircleShape)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
    }
}

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
            placeholder = { Text(stringRes(R.string.relay_group_location_search_hint)) },
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
                            contentDescription = stringRes(R.string.clear),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            isError = missed,
            supportingText =
                if (missed) {
                    { Text(stringRes(R.string.relay_group_location_search_empty)) }
                } else {
                    null
                },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier.fillMaxWidth(),
        )
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
                contentDescription = stringRes(R.string.relay_group_location_use_mine),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun PickerBottomBar(
    cell: String?,
    settledCell: String?,
    level: GeohashChannelLevel,
    onLevel: (GeohashChannelLevel) -> Unit,
    onConfirm: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(16.dp),
        ) {
            Text(
                text = stringRes(R.string.relay_group_location_precision),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GeohashChannelLevel.ordered.forEach { lvl ->
                    FilterChip(
                        selected = lvl == level,
                        onClick = { onLevel(lvl) },
                        label = { Text(lvl.label()) },
                    )
                }
            }

            if (cell == null) {
                Text(
                    text = stringRes(R.string.relay_group_location_picker_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                symbol = MaterialSymbols.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        LoadCityName(geohashStr = settledCell ?: cell) { cityName ->
                            Text(
                                cityName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }
                        Text(
                            "#$cell",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Button(
                onClick = onConfirm,
                enabled = cell != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringRes(R.string.relay_group_location_confirm), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
