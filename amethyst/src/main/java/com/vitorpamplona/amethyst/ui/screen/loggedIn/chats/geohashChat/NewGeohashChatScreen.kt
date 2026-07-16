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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
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
 * Builder for joining a Bitchat-interoperable geohash location channel. Ways in: a
 * level from the device's current location (region → building), a geohash typed by
 * hand, or a point picked on the map (teleport). Joining adds the cell to the
 * kind-10081 geohash list ([AccountViewModel.followGeohash]) so it shows up in
 * Messages and the location-channels list, then opens the chat.
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
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(Modifier.padding(top = 8.dp))
            YourChannelsSection(accountViewModel, nav)
            NearMeSection(onOpen = ::joinAndOpen)
            ManualEntrySection(onOpen = ::joinAndOpen)
            TeleportCard(onClick = { nav.nav(Route.GeohashTeleport) })
            Box(Modifier.padding(bottom = 8.dp))
        }
    }
}

/** A section title with a small tinted icon, e.g. "Near me". */
@Composable
private fun SectionHeader(
    icon: MaterialSymbol,
    title: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 10.dp),
    ) {
        SymbolIcon(
            symbol = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** A location pin inside a soft tinted circle — the visual signature of a location channel. */
@Composable
private fun LocationChip(size: Int = 40) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(size.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            SymbolIcon(
                symbol = MaterialSymbols.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size((size * 0.55f).dp),
            )
        }
    }
}

/** A tappable location card: pin chip, a bold title, a city/geohash subtitle, and a chevron or trailing slot. */
@Composable
private fun LocationCard(
    title: String,
    subtitle: @Composable () -> Unit,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LocationChip()
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                subtitle()
            }
            if (trailing != null) {
                trailing()
            } else {
                SymbolIcon(
                    symbol = MaterialSymbols.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SubtitleText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
}

@Composable
private fun YourChannelsSection(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val joined by accountViewModel.account.geohashList.flow
        .collectAsStateWithLifecycle()
    if (joined.isEmpty()) return

    Column {
        SectionHeader(MaterialSymbols.Groups, "Your channels")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            joined.sorted().forEach { cell ->
                LocationCard(
                    title = "#$cell",
                    subtitle = { LoadCityName(geohashStr = cell) { SubtitleText(it) } },
                    onClick = { nav.nav(Route.GeohashChat(cell)) },
                    trailing = {
                        TextButton(onClick = { accountViewModel.unfollowGeohash(cell) }) { Text("Leave") }
                    },
                )
            }
        }
    }
}

@Composable
private fun ManualEntrySection(onOpen: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val valid = remember(text) { GeoHash.decode(text.trim().lowercase()) != null }

    Column {
        SectionHeader(MaterialSymbols.TravelExplore, "Enter a geohash")
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp)) {
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
                    FilledTonalButton(
                        enabled = valid,
                        onClick = { onOpen(text) },
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text("Join")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun NearMeSection(onOpen: (String) -> Unit) {
    Column {
        SectionHeader(MaterialSymbols.Explore, "Near me")

        val permission = rememberPermissionState(Manifest.permission.ACCESS_COARSE_LOCATION)
        LaunchedEffect(permission.status.isGranted) {
            Amethyst.instance.locationManager.setLocationPermission(permission.status.isGranted)
        }

        if (!permission.status.isGranted) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Find channels for your area — from the whole region down to your block.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    FilledTonalButton(onClick = { permission.launchPermissionRequest() }) {
                        SymbolIcon(symbol = MaterialSymbols.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Use my location")
                    }
                }
            }
            return
        }

        val location by Amethyst.instance.locationManager.preciseGeohashStateFlow
            .collectAsStateWithLifecycle()
        when (val loc = location) {
            is LocationState.LocationResult.Success -> {
                val fix = loc.geoHash.toString()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GeohashChannelLevel.ordered.forEach { level ->
                        level.cellFor(fix)?.let { cell ->
                            LocationCard(
                                title = level.label(),
                                subtitle = { LoadCityName(geohashStr = cell) { SubtitleText("$it · #$cell") } },
                                onClick = { onOpen(cell) },
                            )
                        }
                    }
                }
            }

            LocationState.LocationResult.Loading ->
                Text("Locating…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            LocationState.LocationResult.LackPermission ->
                Text("Location unavailable.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TeleportCard(onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(40.dp).clip(CircleShape),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    SymbolIcon(
                        symbol = MaterialSymbols.Public,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("Teleport", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "Pick any place on the map",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            SymbolIcon(
                symbol = MaterialSymbols.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

internal fun GeohashChannelLevel.label(): String =
    when (this) {
        GeohashChannelLevel.REGION -> "Region"
        GeohashChannelLevel.PROVINCE -> "Province"
        GeohashChannelLevel.CITY -> "City"
        GeohashChannelLevel.NEIGHBORHOOD -> "Neighborhood"
        GeohashChannelLevel.BLOCK -> "Block"
        GeohashChannelLevel.BUILDING -> "Building"
    }
