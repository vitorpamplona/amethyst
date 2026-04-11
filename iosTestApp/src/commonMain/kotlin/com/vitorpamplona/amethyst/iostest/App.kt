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
package com.vitorpamplona.amethyst.iostest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.account.AccountManager
import com.vitorpamplona.amethyst.commons.account.AccountPreferencesStorage
import com.vitorpamplona.amethyst.commons.account.AccountState
import com.vitorpamplona.amethyst.commons.keystorage.SecureKeyStorage
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@Composable
fun AmethystTestApp() {
    val nostrClient = remember { NostrClient(IosWebSocketBuilder()) }
    val accountManager =
        remember {
            AccountManager(
                secureStorage = SecureKeyStorage.create(null),
                prefsStorage = AccountPreferencesStorage.create(null),
                nostrClient = nostrClient,
            )
        }
    val accountState by accountManager.accountState.collectAsState()
    val scope = rememberCoroutineScope()

    MaterialTheme {
        when (val state = accountState) {
            is AccountState.LoggedOut -> {
                LoginScreen(
                    onLoginNsec = { nsec -> scope.launch { accountManager.loginWithNsec(nsec) } },
                    onGenerateKey = { scope.launch { accountManager.generateNewAccount() } },
                )
            }

            is AccountState.LoggedIn -> {
                FeedScreen(
                    pubkeyHex = state.pubKeyHex,
                    nostrClient = nostrClient,
                    onLogout = {
                        scope.launch {
                            nostrClient.close()
                            accountManager.logout()
                        }
                    },
                )
            }

            is AccountState.ConnectingRelays -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { Text("Connecting to relays...") }
            }

            is AccountState.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { scope.launch { accountManager.logout() } }) { Text("Back") }
                }
            }
        }
    }
}

@Composable
fun LoginScreen(
    onLoginNsec: (String) -> Unit,
    onGenerateKey: () -> Unit,
) {
    var nsecInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Amethyst iOS", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Live relay feed via NSURLSessionWebSocketTask",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = nsecInput,
            onValueChange = { nsecInput = it },
            label = { Text("Enter nsec") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { if (nsecInput.isNotBlank()) onLoginNsec(nsecInput) },
            modifier = Modifier.fillMaxWidth(),
            enabled = nsecInput.isNotBlank(),
        ) { Text("Login with nsec") }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(onClick = onGenerateKey, modifier = Modifier.fillMaxWidth()) {
            Text("Generate New Account")
        }
    }
}

@Composable
fun FeedScreen(
    pubkeyHex: String,
    nostrClient: NostrClient,
    onLogout: () -> Unit,
) {
    val notesFlow = remember { MutableStateFlow<List<Event>>(emptyList()) }
    val notes by notesFlow.collectAsState()
    val relayStatus = remember { mutableStateOf("Connecting...") }

    DisposableEffect(nostrClient) {
        val defaultRelays =
            listOf(
                "wss://relay.damus.io",
                "wss://relay.nostr.band",
                "wss://nos.lol",
            )

        val normalizedRelays =
            defaultRelays.mapNotNull {
                try {
                    RelayUrlNormalizer.normalizeOrNull(it)
                } catch (_: Exception) {
                    null
                }
            }

        // Connect
        nostrClient.connect()

        // Subscribe to recent kind:1 notes — filters keyed by relay
        val subId = "feed-${pubkeyHex.take(8)}"
        val filter = Filter(kinds = listOf(1), limit = 50)
        val filtersByRelay: Map<NormalizedRelayUrl, List<Filter>> = normalizedRelays.associateWith { listOf(filter) }

        nostrClient.subscribe(
            subId = subId,
            filters = filtersByRelay,
            listener =
                object : SubscriptionListener {
                    override fun onEvent(
                        event: Event,
                        isLive: Boolean,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        val current = notesFlow.value
                        if (current.none { it.id == event.id }) {
                            val updated = (listOf(event) + current).take(200)
                            notesFlow.value = updated
                            relayStatus.value = "${updated.size} notes from ${normalizedRelays.size} relays"
                        }
                    }

                    override fun onEose(
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        relayStatus.value = "${notesFlow.value.size} notes — EOSE from $relay"
                    }
                },
        )

        relayStatus.value = "Subscribing to ${normalizedRelays.size} relays..."

        onDispose {
            nostrClient.unsubscribe(subId)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Amethyst", style = MaterialTheme.typography.headlineSmall)
                Text(
                    relayStatus.value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onLogout) { Text("Logout") }
        }

        Text(
            "Logged in: ${pubkeyHex.take(8)}...${pubkeyHex.takeLast(4)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))

        if (notes.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { Text("Loading notes from relays...") }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(notes, key = { it.id }) { event ->
                    NoteCard(event)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun NoteCard(event: Event) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "${event.pubKey.take(8)}...${event.pubKey.takeLast(4)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                event.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 10,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
