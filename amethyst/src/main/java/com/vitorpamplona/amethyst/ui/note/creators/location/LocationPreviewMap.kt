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

import android.view.MotionEvent
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/** Default close-up zoom for a single pinned location (street-level). */
private const val DEFAULT_ZOOM = 16.0

/**
 * A small OpenStreetMap (osmdroid) preview centered on [latitude]/[longitude]
 * with a single pin at that point.
 *
 * Used by the Roadstr road event cards (kind 1315/1316) in place of a
 * reverse-geocoded city name. Tiles are fetched from the public OSM tile
 * servers, which requires the app's package name as the User-Agent (set on
 * [Configuration] below) — without it OSM returns HTTP 403.
 *
 * Pan/zoom stay enabled, but a touch listener asks the parent to stop
 * intercepting gestures while the finger is on the map, so dragging the map
 * pans it instead of scrolling the surrounding feed.
 */
@Composable
fun LocationPreviewMap(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
    zoom: Double = DEFAULT_ZOOM,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView =
        remember(context) {
            // Must be set before the MapView is created so OSM tile requests
            // carry a valid User-Agent (the default "osmdroid" is rejected).
            Configuration.getInstance().userAgentValue = context.packageName

            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                setOnTouchListener { view, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> view.parent?.requestDisallowInterceptTouchEvent(true)
                        MotionEvent.ACTION_UP -> view.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    // Returning false lets the MapView still handle the pan/zoom.
                    false
                }
            }
        }

    // osmdroid's MapView is a plain Android View with its own lifecycle: it
    // needs onResume/onPause to (re)start its tile threads, and onDetach to
    // free the tile cache when the composable leaves the tree.
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
        modifier = modifier.fillMaxWidth().aspectRatio(1f),
        factory = { mapView },
        update = { map ->
            val point = GeoPoint(latitude, longitude)
            map.controller.setZoom(zoom)
            map.controller.setCenter(point)

            map.overlays.removeAll { it is Marker }
            val marker =
                Marker(map).apply {
                    position = point
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    setInfoWindow(null)
                }
            map.overlays.add(marker)
            map.invalidate()
        },
    )
}
