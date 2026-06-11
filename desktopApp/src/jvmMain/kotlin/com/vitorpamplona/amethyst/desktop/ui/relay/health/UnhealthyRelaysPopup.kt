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
package com.vitorpamplona.amethyst.desktop.ui.relay.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.vitorpamplona.amethyst.commons.relays.health.RelayHealthStore
import com.vitorpamplona.amethyst.commons.relays.health.RelayListKind
import com.vitorpamplona.amethyst.commons.relays.health.RelayListMutator
import com.vitorpamplona.amethyst.commons.relays.health.RelayRemovalResult
import com.vitorpamplona.amethyst.commons.relays.health.ui.UnhealthyRelayRow
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.launch

/**
 * Desktop-style popup that lists currently-unhealthy relays. Anchored at top-center,
 * dismissable on outside click; per-row Remove / Open / Snooze plus a footer "Snooze all".
 */
@Composable
fun UnhealthyRelaysPopup(
    store: RelayHealthStore,
    mutator: RelayListMutator,
    onDismiss: () -> Unit,
    onOpenDashboard: () -> Unit,
    onShowMessage: (String) -> Unit = {},
) {
    val unhealthy by store.unhealthy.collectAsState()
    val coScope = rememberCoroutineScope()

    Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(0, 32),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        ElevatedCard(
            modifier =
                Modifier
                    .widthIn(min = 420.dp, max = 560.dp)
                    .heightIn(max = 560.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Unresponsive relays",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        store.snoozeAllCurrent()
                        onShowMessage("Snoozed all for 7 days")
                        onDismiss()
                    }) {
                        Text("Snooze all 7d", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Text(
                    text = "These relays haven't responded in over 7 days. Removing them publishes new relay-list events.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                unhealthy.forEach { relay ->
                    UnhealthyRelayRow(
                        relay = relay,
                        lastSeenLabel = lastSeenLabel(relay.lastIncomingAt, relay.lastConnectAt),
                        listKindLabel = ::desktopListKindLabel,
                        removeLabel = "Remove",
                        openLabel = "Dashboard",
                        snoozeLabel = "Snooze 7d",
                        onRemove = {
                            val url = relay.url
                            coScope.launch {
                                val result = mutator.removeFromAllUserLists(url)
                                when (result) {
                                    is RelayRemovalResult.Success ->
                                        onShowMessage("Removed ${url.url}")
                                    is RelayRemovalResult.Partial ->
                                        onShowMessage("Removed from some lists; ${result.failedLists.joinToString { it.name }} failed")
                                    is RelayRemovalResult.Failure ->
                                        onShowMessage("Remove failed: ${result.message ?: "unknown"}")
                                }
                            }
                        },
                        onOpenDashboard = {
                            onDismiss()
                            onOpenDashboard()
                        },
                        onSnooze = {
                            store.snooze(relay.url)
                            onShowMessage("Snoozed for 7 days")
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun desktopListKindLabel(kind: RelayListKind): String =
    when (kind) {
        RelayListKind.Nip65 -> "Read/Write"
        RelayListKind.DmInbox -> "DMs"
        RelayListKind.Search -> "Search"
        RelayListKind.Blocked -> "Blocked"
    }

private fun lastSeenLabel(
    lastIncomingAt: Long,
    lastConnectAt: Long,
): String {
    val ts = maxOf(lastIncomingAt, lastConnectAt)
    if (ts == 0L) return "Never seen"
    val gapSec = TimeUtils.now() - ts
    val days = gapSec / TimeUtils.ONE_DAY
    return when {
        days < 1 -> "Last seen <1d ago"
        else -> "Last seen ${days}d ago"
    }
}
