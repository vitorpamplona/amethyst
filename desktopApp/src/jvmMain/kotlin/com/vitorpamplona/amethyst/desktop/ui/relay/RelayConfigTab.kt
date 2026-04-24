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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.nip65RelayList.Nip65RelayListState
import com.vitorpamplona.amethyst.desktop.model.DesktopAccountRelays
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import kotlinx.coroutines.launch

@Composable
fun RelayConfigTab(
    relayManager: DesktopRelayConnectionManager,
    nip65State: Nip65RelayListState,
    accountRelays: DesktopAccountRelays,
    signer: NostrSigner,
    onPublish: (Event) -> Unit,
    searchRelayState: SnapshotStateList<NormalizedRelayUrl>,
    blockedRelayState: SnapshotStateList<NormalizedRelayUrl>,
    modifier: Modifier = Modifier,
) {
    val statuses by relayManager.relayStatuses.collectAsState()
    val connectedRelays by relayManager.connectedRelays.collectAsState()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // 1. Connected Relays (collapsed by default to show other sections)
        CollapsibleSection(
            title = "Connected Relays",
            description = "Relays your client connects to — ${connectedRelays.size} of ${statuses.size} connected",
            initiallyExpanded = false,
        ) {
            RelayListEditor(
                relays = statuses.keys.sortedBy { it.url },
                connectedRelays = connectedRelays,
                onAdd = { url -> relayManager.addRelay(url) },
                onRemove = { url -> relayManager.removeRelay(url) },
            )
        }

        Spacer(Modifier.height(16.dp))

        // 2. NIP-65 Inbox/Outbox
        CollapsibleSection(
            title = "NIP-65 Inbox/Outbox",
            description = "Relay list metadata (kind 10002) — tells other clients where to find your notes",
        ) {
            Nip65RelayEditor(
                nip65State = nip65State,
                signer = signer,
                onPublish = { event ->
                    onPublish(event)
                    // Consume locally so nip65State updates immediately
                    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
                    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        nip65State.cache.justConsumeMyOwnEvent(event)
                    }
                    // Also persist relay event for restart survival
                    accountRelays.consumePublishedEvent(event)
                },
            )
        }

        Spacer(Modifier.height(16.dp))

        // 3. DM Relays
        CollapsibleSection(
            title = "DM Relays",
            description = "Kind 10050 — where others send you encrypted messages",
        ) {
            DmRelayEditor(
                dmRelays = accountRelays.dmRelayList,
                signer = signer,
                onPublish = onPublish,
                onDmRelaysUpdated = { accountRelays.setDmRelays(it) },
            )
        }

        Spacer(Modifier.height(16.dp))

        // 4. Search Relays
        CollapsibleSection(
            title = "Search Relays",
            description = "Kind 10007 — relays used for NIP-50 full-text search",
        ) {
            SearchRelayEditor(
                localRelays = searchRelayState,
                signer = signer,
                onPublish = { event ->
                    onPublish(event)
                    accountRelays.consumePublishedEvent(event)
                    accountRelays.setSearchRelays(searchRelayState.toSet())
                },
            )
        }

        Spacer(Modifier.height(16.dp))

        // 5. Blocked Relays
        CollapsibleSection(
            title = "Blocked Relays",
            description = "Kind 10006 — relays you want to avoid",
        ) {
            BlockedRelayEditor(
                localRelays = blockedRelayState,
                signer = signer,
                onPublish = { event ->
                    onPublish(event)
                    // Don't call consumePublishedEvent — blocked relays use private tags,
                    // publicRelays() returns empty and would overwrite the correct value
                    accountRelays.setBlockedRelays(blockedRelayState.toSet())
                },
            )
        }
    }
}

@Composable
private fun CollapsibleSection(
    title: String,
    description: String,
    initiallyExpanded: Boolean = true,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (expanded) MaterialSymbols.ExpandMore else MaterialSymbols.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    AnimatedVisibility(expanded) {
        Column(modifier = Modifier.padding(start = 8.dp, top = 8.dp)) {
            content()
        }
    }
}
