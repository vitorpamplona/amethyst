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
package com.vitorpamplona.amethyst.desktop.ui.relay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.nip65RelayList.Nip65RelayListState
import com.vitorpamplona.amethyst.desktop.model.DesktopAccountRelays
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.network.Nip11Fetcher
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner

enum class DashboardTab(
    val label: String,
) {
    MONITOR("Monitor"),
    CONFIGURE("Configure"),
}

@Composable
fun RelayDashboardScreen(
    relayManager: DesktopRelayConnectionManager,
    nip11Fetcher: Nip11Fetcher,
    nip65State: Nip65RelayListState,
    accountRelays: DesktopAccountRelays,
    signer: NostrSigner,
    onPublish: (Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableStateOf(DashboardTab.MONITOR) }

    // Hoisted state — survives Monitor ↔ Configure tab switches
    // Synced from accountRelays flows (persistence + bootstrap + per-screen picker)
    val searchRelayState = remember { mutableStateListOf<NormalizedRelayUrl>() }
    val blockedRelayState = remember { mutableStateListOf<NormalizedRelayUrl>() }
    val currentSearchRelays by accountRelays.searchRelayList.collectAsState()
    val currentBlockedRelays by accountRelays.blockedRelayList.collectAsState()
    LaunchedEffect(currentSearchRelays) {
        searchRelayState.clear()
        searchRelayState.addAll(currentSearchRelays.sortedBy { it.url })
    }
    LaunchedEffect(currentBlockedRelays) {
        blockedRelayState.clear()
        blockedRelayState.addAll(currentBlockedRelays.sortedBy { it.url })
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Header — Messages-style tabs-first: selected chip acts as the screen title.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DashboardTab.entries.forEach { tab ->
                FilterChip(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    label = { Text(tab.label) },
                )
            }
        }

        when (selectedTab) {
            DashboardTab.MONITOR -> {
                RelayMetricsTab(relayManager, nip11Fetcher)
            }

            DashboardTab.CONFIGURE -> {
                RelayConfigTab(
                    relayManager = relayManager,
                    nip65State = nip65State,
                    accountRelays = accountRelays,
                    signer = signer,
                    onPublish = onPublish,
                    searchRelayState = searchRelayState,
                    blockedRelayState = blockedRelayState,
                )
            }
        }
    }
}
