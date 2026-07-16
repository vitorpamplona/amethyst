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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.geohashChat

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.ui.note.creators.location.LoadCityName
import com.vitorpamplona.amethyst.ui.note.creators.location.LocationPickerMap
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashChannelLevel
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHash

/**
 * Teleport: tap a point on the map to join a remote geohash cell (at a chosen
 * precision level) you are not physically in. Joining follows the cell (kind
 * 10081) and opens the chat with the teleport flag set, so outgoing messages
 * carry the ["t","teleport"] marker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeohashTeleportScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var pickedLat by remember { mutableStateOf<Double?>(null) }
    var pickedLon by remember { mutableStateOf<Double?>(null) }
    var level by remember { mutableStateOf(GeohashChannelLevel.CITY) }

    val cell =
        remember(pickedLat, pickedLon, level) {
            val lat = pickedLat
            val lon = pickedLon
            if (lat != null && lon != null) GeoHash.encode(lat, lon, level.chars).toString() else null
        }

    Scaffold(
        topBar = {
            TopBarExtensibleWithBackButton(
                title = { Text("Teleport", fontWeight = FontWeight.Bold) },
                popBack = nav::popBack,
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding()),
        ) {
            LocationPickerMap(
                latitude = 20.0,
                longitude = 0.0,
                pickedLatitude = pickedLat,
                pickedLongitude = pickedLon,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                onPick = { lat, lon ->
                    pickedLat = lat
                    pickedLon = lon
                },
            )

            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    if (cell == null) "Tap the map to pick a spot." else "Precision",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GeohashChannelLevel.ordered.forEach { lvl ->
                        FilterChip(
                            selected = lvl == level,
                            onClick = { level = lvl },
                            label = { Text(lvl.label()) },
                        )
                    }
                }

                if (cell != null) {
                    LoadCityName(geohashStr = cell) { cityName ->
                        Text(
                            "$cityName · #$cell",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                        )
                    }
                    Button(
                        onClick = {
                            accountViewModel.followGeohash(cell)
                            nav.popBack()
                            nav.nav(Route.GeohashChat(cell, teleported = true))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Teleport here")
                    }
                }
            }
        }
    }
}
