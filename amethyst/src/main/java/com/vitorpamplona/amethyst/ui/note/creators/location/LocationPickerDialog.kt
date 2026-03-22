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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fonfon.kgeohash.GeoHash
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeohashPrecision
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

@Composable
fun LocationPickerDialog(
    initialLatitude: Double?,
    initialLongitude: Double?,
    onLocationSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val defaultLat = initialLatitude ?: 0.0
    val defaultLng = initialLongitude ?: 0.0

    var selectedLat by remember { mutableDoubleStateOf(defaultLat) }
    var selectedLng by remember { mutableDoubleStateOf(defaultLng) }

    val geohash =
        remember(selectedLat, selectedLng) {
            GeoHash(selectedLat, selectedLng, GeohashPrecision.KM_5_X_5.digits).toString()
        }

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
            ),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                Text(
                    text = stringRes(R.string.location_picker_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                Text(
                    text = stringRes(R.string.location_picker_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                OsmMapView(
                    initialLat = defaultLat,
                    initialLng = defaultLng,
                    hasInitialLocation = initialLatitude != null,
                    onLocationPicked = { lat, lng ->
                        selectedLat = lat
                        selectedLng = lng
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp),
                )

                LoadCityName(
                    geohashStr = geohash,
                    onLoading = {
                        Text(
                            text = geohash,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    },
                ) { cityName ->
                    Text(
                        text = cityName,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringRes(R.string.cancel))
                    }
                    TextButton(
                        onClick = { onLocationSelected(geohash) },
                    ) {
                        Text(stringRes(R.string.location_picker_confirm))
                    }
                }
            }
        }
    }
}

@Composable
private fun OsmMapView(
    initialLat: Double,
    initialLng: Double,
    hasInitialLocation: Boolean,
    onLocationPicked: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val mapView =
        remember {
            Configuration.getInstance().userAgentValue = context.packageName
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(if (hasInitialLocation) 12.0 else 3.0)
                controller.setCenter(GeoPoint(initialLat, initialLng))
            }
        }

    val marker =
        remember {
            Marker(mapView).apply {
                position = GeoPoint(initialLat, initialLng)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = context.getString(R.string.location_picker_selected)
            }
        }

    DisposableEffect(Unit) {
        val eventsOverlay =
            MapEventsOverlay(
                object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                        marker.position = p
                        mapView.invalidate()
                        onLocationPicked(p.latitude, p.longitude)
                        return true
                    }

                    override fun longPressHelper(p: GeoPoint): Boolean = false
                },
            )
        mapView.overlays.add(0, eventsOverlay)
        if (hasInitialLocation) {
            mapView.overlays.add(marker)
        }

        onDispose {
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
    )
}
