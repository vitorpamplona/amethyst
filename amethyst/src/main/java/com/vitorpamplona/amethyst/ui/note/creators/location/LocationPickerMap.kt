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

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vitorpamplona.amethyst.ui.theme.isLight
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

/**
 * An interactive OpenStreetMap (osmdroid) picker: tapping the map reports the
 * tapped coordinate via [onPick], and [pickedLatitude]/[pickedLongitude] (when
 * set) show a marker there. Used to "teleport" into a remote geohash cell.
 *
 * Shares the tile/User-Agent/lifecycle setup with [LocationPreviewMap]; unlike
 * that display-only map, this one installs a [MapEventsOverlay] for tap picking.
 *
 * Two optional hooks power a "move the map under a fixed center pin" experience
 * (the modern picker style) without breaking the tap-to-drop callers:
 * - [onCenterChanged] fires whenever the map is scrolled or zoomed, reporting the
 *   new map center — pair it with a Compose crosshair drawn over the map's center.
 * - [recenter] animates the map to a new point when its value changes (e.g. after
 *   a place search or a "use my location" tap). Passing the same value twice is a
 *   no-op, so it is safe to hoist in state.
 */
@Composable
fun LocationPickerMap(
    latitude: Double,
    longitude: Double,
    pickedLatitude: Double?,
    pickedLongitude: Double?,
    modifier: Modifier = Modifier,
    zoom: Double = 4.0,
    recenter: GeoPoint? = null,
    recenterZoom: Double? = null,
    onCenterChanged: ((Double, Double) -> Unit)? = null,
    onPick: (Double, Double) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnPick by rememberUpdatedState(onPick)
    val currentOnCenterChanged by rememberUpdatedState(onCenterChanged)
    val darkTheme = !MaterialTheme.colorScheme.isLight

    // Tracks the last point we animated to, so a recomposition that re-supplies the
    // same [recenter] doesn't yank the map back while the user is panning.
    val lastRecenter = remember { arrayOfNulls<GeoPoint>(1) }

    val mapView =
        remember(context) {
            Configuration.getInstance().userAgentValue = context.packageName

            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                controller.setZoom(zoom)
                controller.setCenter(GeoPoint(latitude, longitude))

                val receiver =
                    object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                            currentOnPick(p.latitude, p.longitude)
                            return true
                        }

                        override fun longPressHelper(p: GeoPoint): Boolean {
                            currentOnPick(p.latitude, p.longitude)
                            return true
                        }
                    }
                overlays.add(0, MapEventsOverlay(receiver))

                addMapListener(
                    object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            mapCenter.let { currentOnCenterChanged?.invoke(it.latitude, it.longitude) }
                            return false
                        }

                        override fun onZoom(event: ZoomEvent?): Boolean {
                            mapCenter.let { currentOnCenterChanged?.invoke(it.latitude, it.longitude) }
                            return false
                        }
                    },
                )
            }
        }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = { map ->
            if (recenter != null && recenter != lastRecenter[0]) {
                lastRecenter[0] = recenter
                if (recenterZoom != null) {
                    map.controller.animateTo(recenter, recenterZoom, 800L)
                } else {
                    map.controller.animateTo(recenter)
                }
            }

            // Follow the app theme: dim the bright MAPNIK tiles in dark mode, matching
            // the display-only LocationPreviewMap.
            map.overlayManager.tilesOverlay.setColorFilter(if (darkTheme) NIGHT_TILE_FILTER else null)

            map.overlays.removeAll { it is Marker }
            if (pickedLatitude != null && pickedLongitude != null) {
                val point = GeoPoint(pickedLatitude, pickedLongitude)
                val marker =
                    Marker(map).apply {
                        position = point
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        setInfoWindow(null)
                    }
                map.overlays.add(marker)
            }
            map.invalidate()
        },
    )
}
