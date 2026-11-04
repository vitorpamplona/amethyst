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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.geocache_map_long_press
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.geocachePoint
import com.vitorpamplona.amethyst.commons.ui.note.rememberGeocachePalette
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.map.GEOCACHE_CLUSTER_THRESHOLD
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.map.GEOCACHE_MAP_FIRST_CLUSTERED_ZOOM
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.map.GEOCACHE_MAP_FIRST_ZOOM
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.map.GEOCACHE_MAP_INITIAL_ZOOM
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.map.GeocacheMapMark
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.map.GeocachePeekSheet
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.map.geocacheMapMarks
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.map.geocachePin
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.map.rememberGeocacheListings
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.rememberMyFoundCacheIds
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHash
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheGeohash
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent
import kotlin.math.roundToInt

/**
 * The geocaches screen's Map tab on the desktop: the Nearby tab's own feed as pins, coloured by
 * state (the shared palette, so a cache is the same colour here as on its card), folded into counts
 * above the threshold, a tap raising the peek sheet and a long press starting a new cache there.
 * Android draws the same marks with osmdroid (`GeocacheMapTab` in `amethyst`); the marks, the peek
 * sheet and the clustering are shared in commonsUI.
 */
@Composable
fun DesktopGeocacheMapTab(
    feedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
    tileUrl: (zoom: Int, x: Int, y: Int) -> String = ::osmTileUrl,
) {
    val feedState by feedContentState.feedContent.collectAsStateWithLifecycle()
    val found = rememberMyFoundCacheIds(accountViewModel)
    val listings = rememberGeocacheListings(feedState)
    val palette = rememberGeocachePalette()

    var peek by remember { mutableStateOf<GeocacheListingEvent?>(null) }
    val state = remember { DesktopMapState(0.0, 0.0, GEOCACHE_MAP_INITIAL_ZOOM.roundToInt()) }

    // The cell size tracks zoom, so zooming in splits clusters apart.
    val marks = remember(listings, state.zoom) { geocacheMapMarks(listings, state.zoom.toDouble()) }

    // Settle on the first cache once, so a pan is not yanked back each time one arrives from a relay.
    LaunchedEffect(marks.isNotEmpty()) {
        if (!state.positioned) {
            marks.firstOrNull()?.let { first ->
                val clustered = listings.count { it.geocachePoint() != null } > GEOCACHE_CLUSTER_THRESHOLD
                state.centerOn(first.lat, first.lon, (if (clustered) GEOCACHE_MAP_FIRST_CLUSTERED_ZOOM else GEOCACHE_MAP_FIRST_ZOOM).roundToInt())
            }
        }
    }

    val markers =
        remember(marks, found, palette) {
            marks.map { mark ->
                when (mark) {
                    is GeocacheMapMark.Cache -> {
                        val pin = geocachePin(mark.listing, found, palette)
                        MapMarker(mark.listing.address(), mark.lat, mark.lon, pin.color, pin.emoji, pin.alpha) { peek = mark.listing }
                    }
                    // Zoom into the cluster rather than picking one of its caches arbitrarily.
                    is GeocacheMapMark.Cluster ->
                        MapMarker("cluster:${mark.lat},${mark.lon}", mark.lat, mark.lon, palette.live, mark.size.toString(), centered = true) {
                            state.centerOn(mark.lat, mark.lon, state.zoom + 2)
                        }
                }
            }
        }

    Box(Modifier.fillMaxSize()) {
        DesktopTileMap(
            state = state,
            markers = markers,
            modifier = Modifier.fillMaxSize(),
            wheelZoom = true,
            // The screen's new-cache button floats over the bottom right.
            controlsAlignment = Alignment.TopEnd,
            onTap = { peek = null },
            onLongPress = { lat, lon ->
                // A cache hidden where the pointer was held. The picker still opens inside the
                // composer, so this is a starting point, not a commit.
                nav.nav(Route.NewGeocache(geohash = GeoHash.encode(lat, lon, GeocacheGeohash.MAX_TAGGED).toString()))
            },
            tileUrl = tileUrl,
        )

        peek?.let { listing ->
            GeocachePeekSheet(
                listing = listing,
                onOpen = { nav.nav(Route.GeocacheDetail(listing.address())) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        if (peek == null) {
            Text(
                text = stringRes(Res.string.geocache_map_long_press),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            )
        }
    }
}
