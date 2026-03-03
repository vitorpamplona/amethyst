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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chess

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.chess.ChessConfig
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import kotlinx.coroutines.flow.map

/**
 * Bottom sheet showing relay settings and debug info for chess subscriptions
 */
@Composable
fun ChessRelaySettingsSheet(
    chessViewModel: ChessViewModelNew,
    accountViewModel: AccountViewModel,
) {
    // Get relay information for settings display
    val inboxRelays by accountViewModel.account.notificationRelays.flow
        .collectAsState()
    val outboxRelays by accountViewModel.account.outboxRelays.flow
        .collectAsState()
    val globalRelays by accountViewModel.account.defaultGlobalRelays.flow
        .collectAsState()

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "Chess Relay Settings",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Stats section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatRow(chessViewModel)
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // Chess relay info
        Text(
            text = "Active Chess Relays",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Chess uses 3 dedicated relays for fast, reliable queries",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Show chess relays and their connection status
        ChessConfig.CHESS_RELAY_NAMES.forEach { relay ->
            val isConnected =
                inboxRelays.contains(relay) ||
                    outboxRelays.contains(relay) ||
                    globalRelays.contains(relay)
            ChessRelayRow(relay = relay, isConnected = isConnected)
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // Inbox relays (where challenges TO you are fetched)
        Text(
            text = "Inbox Relays (${inboxRelays.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Personal challenges are fetched from here",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (inboxRelays.isEmpty()) {
            Text(
                text = "No inbox relays configured",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            inboxRelays.take(5).forEach { relay ->
                RelayRow(relayUrl = relay)
            }
            if (inboxRelays.size > 5) {
                Text(
                    text = "+${inboxRelays.size - 5} more",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Global relays (where open challenges are fetched)
        Text(
            text = "Global Relays (${globalRelays.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Open challenges and public games are fetched from here",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (globalRelays.isEmpty()) {
            Text(
                text = "No global relays configured - open challenges won't load!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            globalRelays.take(5).forEach { relay ->
                RelayRow(relayUrl = relay)
            }
            if (globalRelays.size > 5) {
                Text(
                    text = "+${globalRelays.size - 5} more",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Outbox relays (where your challenges are published)
        Text(
            text = "Outbox Relays (${outboxRelays.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Your challenges and moves are published here",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (outboxRelays.isEmpty()) {
            Text(
                text = "No outbox relays configured",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            outboxRelays.take(5).forEach { relay ->
                RelayRow(relayUrl = relay)
            }
            if (outboxRelays.size > 5) {
                Text(
                    text = "+${outboxRelays.size - 5} more",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@SuppressLint("StateFlowValueCalledInComposition")
@Composable
fun StatRow(chessViewModel: ChessViewModelNew) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val challengeCount by remember {
            chessViewModel.challenges.map { it.size }
        }.collectAsStateWithLifecycle(chessViewModel.challenges.value.size)

        Text(
            text = "$challengeCount",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Challenges",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val publicGameCount by remember {
            chessViewModel.publicGames.map { it.size }
        }.collectAsStateWithLifecycle(chessViewModel.publicGames.value.size)

        Text(
            text = "$publicGameCount",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Live Games",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RelayRow(
    relayUrl: NormalizedRelayUrl,
    isPreferred: Boolean = false,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = stringRes(R.string.connected),
            tint =
                if (isPreferred) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.primary
                },
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = relayUrl.displayUrl(),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isPreferred) FontWeight.Medium else FontWeight.Normal,
        )
        if (isPreferred) {
            Text(
                text = "preferred",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun ChessRelayRow(
    relay: NormalizedRelayUrl,
    isConnected: Boolean,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector =
                if (isConnected) {
                    Icons.Default.CheckCircle
                } else {
                    Icons.Default.Close
                },
            contentDescription = if (isConnected) "Connected" else "Not connected",
            tint =
                if (isConnected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = relay.displayUrl(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = if (isConnected) "connected" else "not connected",
            style = MaterialTheme.typography.labelSmall,
            color =
                if (isConnected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
        )
    }
}
