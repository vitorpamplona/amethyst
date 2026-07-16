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

import android.Manifest
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.service.location.LocationState
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.ui.note.creators.location.LoadCityName
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashChannelLevel
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHash
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon as SymbolIcon

/**
 * Builder for joining a Bitchat-interoperable geohash location channel. Three
 * ways in: a level from the device's current location (region → building), a
 * geohash typed by hand, or (later) a point picked on the map. Joining adds the
 * cell to the kind-10081 geohash list ([AccountViewModel.followGeohash]) so it
 * shows up in Messages and the location-channels list, then opens the chat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGeohashChatScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    fun joinAndOpen(geohash: String) {
        val cell = geohash.trim().lowercase()
        if (GeoHash.decode(cell) == null) return
        accountViewModel.followGeohash(cell)
        nav.popBack()
        nav.nav(Route.GeohashChat(cell))
    }

    Scaffold(
        topBar = {
            TopBarExtensibleWithBackButton(
                title = { Text("New location channel", fontWeight = FontWeight.Bold) },
                popBack = nav::popBack,
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding())
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .imePadding(),
        ) {
            YourChannelsSection(accountViewModel, nav)
            ManualEntrySection(onOpen = ::joinAndOpen)
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            NearMeSection(onOpen = ::joinAndOpen)
        }
    }
}

@Composable
private fun YourChannelsSection(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val joined by accountViewModel.account.geohashList.flow
        .collectAsStateWithLifecycle()
    if (joined.isEmpty()) return

    Text("Your channels", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        joined.sorted().forEach { cell ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { nav.nav(Route.GeohashChat(cell)) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SymbolIcon(
                    symbol = MaterialSymbols.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    LoadCityName(geohashStr = cell) { cityName ->
                        Text(cityName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                    Text(
                        "#$cell",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = { accountViewModel.unfollowGeohash(cell) }) {
                    Text("Leave")
                }
            }
        }
    }
    HorizontalDivider(Modifier.padding(vertical = 16.dp))
}

@Composable
private fun ManualEntrySection(onOpen: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val valid = remember(text) { GeoHash.decode(text.trim().lowercase()) != null }

    Text("Enter a geohash", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Text(
        "Anyone in the same geohash cell shares the channel.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            modifier = Modifier.weight(1f),
            placeholder = { Text("e.g. u4pruyd") },
            isError = text.isNotBlank() && !valid,
        )
        OutlinedButton(
            enabled = valid,
            onClick = { onOpen(text) },
            modifier = Modifier.padding(start = 8.dp),
        ) {
            Text("Join")
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun NearMeSection(onOpen: (String) -> Unit) {
    Text("Near me", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Text(
        "Channels for your current area, from the whole region down to your block.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )

    val permission = rememberPermissionState(Manifest.permission.ACCESS_COARSE_LOCATION)
    LaunchedEffect(permission.status.isGranted) {
        Amethyst.instance.locationManager.setLocationPermission(permission.status.isGranted)
    }

    if (!permission.status.isGranted) {
        OutlinedButton(onClick = { permission.launchPermissionRequest() }) {
            Text("Use my location")
        }
        return
    }

    val location by Amethyst.instance.locationManager.preciseGeohashStateFlow
        .collectAsStateWithLifecycle()
    when (val loc = location) {
        is LocationState.LocationResult.Success -> {
            val fix = loc.geoHash.toString()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GeohashChannelLevel.ordered.forEach { level ->
                    level.cellFor(fix)?.let { cell ->
                        LevelRow(level = level, cell = cell, onOpen = onOpen)
                    }
                }
            }
        }

        LocationState.LocationResult.Loading ->
            Text("Locating…", color = MaterialTheme.colorScheme.onSurfaceVariant)

        LocationState.LocationResult.LackPermission ->
            Text("Location unavailable.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LevelRow(
    level: GeohashChannelLevel,
    cell: String,
    onOpen: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(cell) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SymbolIcon(
            symbol = MaterialSymbols.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(level.label(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            LoadCityName(geohashStr = cell) { cityName ->
                Text(
                    "$cityName · #$cell",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun GeohashChannelLevel.label(): String =
    when (this) {
        GeohashChannelLevel.REGION -> "Region"
        GeohashChannelLevel.PROVINCE -> "Province"
        GeohashChannelLevel.CITY -> "City"
        GeohashChannelLevel.NEIGHBORHOOD -> "Neighborhood"
        GeohashChannelLevel.BLOCK -> "Block"
        GeohashChannelLevel.BUILDING -> "Building"
    }
