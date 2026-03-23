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
package com.vitorpamplona.amethyst.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.service.namecoin.LocalNamecoinService
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.IRequestListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip02FollowList.tags.ContactTag
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * State machine for the import follow list flow.
 */
private sealed class ImportState {
    data object Idle : ImportState()
    data object ResolvingIdentifier : ImportState()
    data class IdentifierResolved(val pubkey: String) : ImportState()
    data object FetchingFollowList : ImportState()
    data class FollowListLoaded(val sourcePubkey: String) : ImportState()
    data class Error(val message: String) : ImportState()
    data object Publishing : ImportState()
    data object Done : ImportState()
}

/**
 * A follow entry with mutable selection state.
 */
data class FollowEntry(
    val pubkey: String,
    val displayName: String? = null,
    val selected: Boolean = true,
)

/**
 * Resolves a NIP-05 identifier (user@domain.com) to a hex pubkey via HTTP.
 */
private suspend fun resolveNip05Http(identifier: String): String? {
    if (!identifier.contains("@") || identifier.endsWith(".bit")) return null
    val parts = identifier.split("@", limit = 2)
    if (parts.size != 2) return null
    val (name, domain) = parts
    if (name.isBlank() || domain.isBlank()) return null
    val encodedName = URLEncoder.encode(name, "UTF-8")
    val url = "https://$domain/.well-known/nostr.json?name=$encodedName"
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body.string()
                val json = jacksonObjectMapper().readTree(body)
                json.get("names")?.get(name)?.asText()
            }
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Import Follow List dialog for Desktop.
 *
 * Lets users enter an identifier (npub, hex, NIP-05 HTTP, or Namecoin),
 * resolves it to a pubkey, fetches their kind 3 (ContactListEvent) from
 * relays, displays the follow list with select/deselect toggles, and
 * publishes a new kind 3 with the selected follows.
 */
@Composable
fun ImportFollowListDialog(
    onDismiss: () -> Unit,
    relayManager: DesktopRelayConnectionManager,
    account: AccountState.LoggedIn,
    localCache: DesktopLocalCache,
) {
    val namecoinService = LocalNamecoinService.current
    val scope = rememberCoroutineScope()

    var input by remember { mutableStateOf("") }
    var importState by remember { mutableStateOf<ImportState>(ImportState.Idle) }
    val followEntries = remember { mutableStateListOf<FollowEntry>() }

    // Track active subscription IDs for cleanup
    val activeSubscriptions = remember { mutableStateListOf<String>() }

    // Clean up all subscriptions when the dialog is dismissed
    DisposableEffect(Unit) {
        onDispose {
            activeSubscriptions.forEach { subId ->
                try {
                    relayManager.unsubscribe(subId)
                } catch (_: Exception) {
                    // Ignore cleanup errors
                }
            }
        }
    }

    // When identifier is resolved, auto-start fetching the follow list
    LaunchedEffect(importState) {
        val state = importState
        if (state is ImportState.IdentifierResolved) {
            importState = ImportState.FetchingFollowList
            val pubkey = state.pubkey
            followEntries.clear()

            val subId = "import-follows-${System.currentTimeMillis()}"
            activeSubscriptions.add(subId)
            var receivedContactList = false

            // Use connected relays only — available relays may include disconnected ones
            val relays = relayManager.connectedRelays.value

            if (relays.isEmpty()) {
                importState = ImportState.Error("No relays connected. Check your relay settings.")
                return@LaunchedEffect
            }

            relayManager.subscribe(
                subId = subId,
                filters = listOf(
                    Filter(
                        kinds = listOf(ContactListEvent.KIND),
                        authors = listOf(pubkey),
                        limit = 1,
                    ),
                ),
                relays = relays,
                listener = object : IRequestListener {
                    override fun onEvent(
                        event: Event,
                        isLive: Boolean,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        if (event.kind == ContactListEvent.KIND && !receivedContactList) {
                            receivedContactList = true
                            val contactList = ContactListEvent(
                                event.id,
                                event.pubKey,
                                event.createdAt,
                                event.tags,
                                event.content,
                                event.sig,
                            )
                            val follows = contactList.unverifiedFollowKeySet()

                            // Dispatch state updates to main thread (relay callbacks
                            // run on arbitrary threads; Compose state is not thread-safe)
                            scope.launch(Dispatchers.Main) {
                                followEntries.clear()
                                followEntries.addAll(
                                    follows.map { FollowEntry(pubkey = it, selected = true) },
                                )
                                importState = ImportState.FollowListLoaded(sourcePubkey = pubkey)
                            }

                            // Clean up the contact list subscription
                            try {
                                relayManager.unsubscribe(subId)
                            } catch (_: Exception) {}
                            activeSubscriptions.remove(subId)

                            // Start fetching metadata for display names
                            if (follows.isNotEmpty()) {
                                val metaSubId = "import-meta-${System.currentTimeMillis()}"
                                activeSubscriptions.add(metaSubId)
                                relayManager.subscribe(
                                    subId = metaSubId,
                                    filters = listOf(
                                        Filter(
                                            kinds = listOf(MetadataEvent.KIND),
                                            authors = follows,
                                            limit = follows.size,
                                        ),
                                    ),
                                    listener = object : IRequestListener {
                                        override fun onEvent(
                                            event: Event,
                                            isLive: Boolean,
                                            relay: NormalizedRelayUrl,
                                            forFilters: List<Filter>?,
                                        ) {
                                            if (event.kind == MetadataEvent.KIND) {
                                                val bestName = try {
                                                    val metaJson = jacksonObjectMapper().readTree(event.content)
                                                    metaJson.get("display_name")?.asText()?.takeIf { it.isNotBlank() }
                                                        ?: metaJson.get("name")?.asText()?.takeIf { it.isNotBlank() }
                                                } catch (_: Exception) {
                                                    null
                                                }
                                                if (bestName != null) {
                                                    // Dispatch to main thread
                                                    scope.launch(Dispatchers.Main) {
                                                        val idx = followEntries.indexOfFirst { it.pubkey == event.pubKey }
                                                        if (idx >= 0) {
                                                            followEntries[idx] = followEntries[idx].copy(displayName = bestName)
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        override fun onEose(
                                            relay: NormalizedRelayUrl,
                                            forFilters: List<Filter>?,
                                        ) {
                                            // Metadata is best-effort; don't close early
                                            // as multiple relays may have different metadata
                                        }
                                    },
                                )

                                // Clean up metadata subscription after 20s
                                scope.launch {
                                    delay(20_000)
                                    if (metaSubId in activeSubscriptions) {
                                        try {
                                            relayManager.unsubscribe(metaSubId)
                                        } catch (_: Exception) {}
                                        activeSubscriptions.remove(metaSubId)
                                    }
                                }
                            }
                        }
                    }

                    override fun onEose(
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                    }
                },
            )

            // Timeout: if no contact list received after 15s, show error
            scope.launch {
                delay(15_000)
                if (importState is ImportState.FetchingFollowList) {
                    try {
                        relayManager.unsubscribe(subId)
                    } catch (_: Exception) {}
                    activeSubscriptions.remove(subId)
                    if (followEntries.isEmpty()) {
                        importState = ImportState.Error("No follow list found for this user. They may not have published a contact list.")
                    }
                }
            }
        }
    }

    // Auto-dismiss after Done
    LaunchedEffect(importState) {
        if (importState is ImportState.Done) {
            delay(1500)
            onDismiss()
        }
    }

    fun resolveIdentifier() {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            importState = ImportState.Error("Enter an identifier")
            return
        }

        importState = ImportState.ResolvingIdentifier
        followEntries.clear()

        scope.launch {
            try {
                // Try raw hex pubkey first (exact 64-char hex)
                if (trimmed.length == 64 && trimmed.matches(Regex("^[0-9a-fA-F]{64}$"))) {
                    importState = ImportState.IdentifierResolved(trimmed.lowercase())
                    return@launch
                }

                // Try bech32 (npub/nprofile) — only accept if it starts with known prefixes
                if (trimmed.startsWith("npub1") || trimmed.startsWith("nprofile1") || trimmed.startsWith("nsec1")) {
                    val bech32Result = decodePublicKeyAsHexOrNull(trimmed)
                    if (bech32Result != null && bech32Result.length == 64) {
                        importState = ImportState.IdentifierResolved(bech32Result)
                        return@launch
                    }
                    importState = ImportState.Error("Invalid npub/nprofile — could not decode")
                    return@launch
                }

                // Try Namecoin (.bit, d/, id/)
                if (NamecoinNameResolver.isNamecoinIdentifier(trimmed) && namecoinService != null) {
                    val result = namecoinService.resolvePubkey(trimmed)
                    if (result != null && result.length == 64) {
                        importState = ImportState.IdentifierResolved(result)
                        return@launch
                    }
                    importState = ImportState.Error("Namecoin name not found or has no Nostr pubkey")
                    return@launch
                }

                // Try NIP-05 HTTP (user@domain)
                if (trimmed.contains("@")) {
                    val result = resolveNip05Http(trimmed)
                    if (result != null && result.length == 64) {
                        importState = ImportState.IdentifierResolved(result)
                        return@launch
                    }
                    importState = ImportState.Error("NIP-05 lookup failed — user not found at that domain")
                    return@launch
                }

                importState = ImportState.Error("Unrecognized identifier format. Use npub1..., hex pubkey, user@domain, or .bit/d//id/")
            } catch (e: Exception) {
                importState = ImportState.Error(e.message ?: "Resolution failed")
            }
        }
    }

    fun toggleAll(selected: Boolean) {
        val updated = followEntries.map { it.copy(selected = selected) }
        followEntries.clear()
        followEntries.addAll(updated)
    }

    fun toggleEntry(index: Int) {
        if (index in followEntries.indices) {
            followEntries[index] = followEntries[index].copy(selected = !followEntries[index].selected)
        }
    }

    fun publishFollows() {
        val selected = followEntries.filter { it.selected }
        if (selected.isEmpty()) return

        importState = ImportState.Publishing
        scope.launch {
            try {
                val contactTags = selected.map { ContactTag(it.pubkey) }
                val newContactList = ContactListEvent.createFromScratch(
                    followUsers = contactTags,
                    relayUse = null,
                    signer = account.signer,
                )
                relayManager.broadcastToAll(newContactList)
                importState = ImportState.Done
            } catch (e: Exception) {
                importState = ImportState.Error("Failed to publish: ${e.message}")
            }
        }
    }

    val selectedCount = followEntries.count { it.selected }
    val totalCount = followEntries.size

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
            ) {
                Text(
                    "Import Follow List",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Enter an npub, hex pubkey, NIP-05 (user@domain), or Namecoin identifier " +
                        "(.bit, d/, id/) to import their follow list.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))

                // Input field + Resolve button (shown in Idle and Error states)
                val showInput = importState is ImportState.Idle ||
                    importState is ImportState.Error ||
                    importState is ImportState.ResolvingIdentifier
                if (showInput) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = {
                                input = it
                                if (importState is ImportState.Error) {
                                    importState = ImportState.Idle
                                }
                            },
                            label = { Text("Identifier") },
                            placeholder = { Text("npub1..., alice@example.com, d/alice") },
                            singleLine = true,
                            isError = importState is ImportState.Error,
                            supportingText = (importState as? ImportState.Error)?.let { err ->
                                { Text(err.message, color = MaterialTheme.colorScheme.error) }
                            },
                            modifier = Modifier.weight(1f).onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                    resolveIdentifier()
                                    true
                                } else {
                                    false
                                }
                            },
                        )
                        Button(
                            onClick = { resolveIdentifier() },
                            enabled = input.isNotBlank() && importState !is ImportState.ResolvingIdentifier,
                        ) {
                            if (importState is ImportState.ResolvingIdentifier) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text("Resolve")
                            }
                        }
                    }
                }

                // Fetching follow list state
                if (importState is ImportState.FetchingFollowList) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                            "Fetching follow list from relays…",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                // Follow list loaded — show entries with checkboxes
                if (importState is ImportState.FollowListLoaded && followEntries.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))

                    // Header with count and select/deselect controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "$totalCount follows found",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { toggleAll(true) }) {
                                Text("Select All", style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(onClick = { toggleAll(false) }) {
                                Text("Deselect All", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // Scrollable follow list
                    LazyColumn(
                        modifier = Modifier.height(300.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(followEntries.size) { index ->
                            val entry = followEntries[index]
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            ) {
                                Checkbox(
                                    checked = entry.selected,
                                    onCheckedChange = { toggleEntry(index) },
                                )
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(6.dp))
                                Column {
                                    if (entry.displayName != null) {
                                        Text(
                                            entry.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                    Text(
                                        "${entry.pubkey.take(12)}…${entry.pubkey.takeLast(8)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (entry.displayName != null) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                // Publishing state
                if (importState is ImportState.Publishing) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                            "Publishing contact list…",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                // Done state
                if (importState is ImportState.Done) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            "Successfully published! Following $selectedCount accounts.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (importState !is ImportState.Done) {
                        OutlinedButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                    }
                    if (importState is ImportState.FollowListLoaded && selectedCount > 0) {
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { publishFollows() },
                        ) {
                            Text("Follow $selectedCount account${if (selectedCount != 1) "s" else ""}")
                        }
                    }
                    if (importState is ImportState.Error) {
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            importState = ImportState.Idle
                            followEntries.clear()
                        }) {
                            Text("Try Again")
                        }
                    }
                }
            }
        }
    }
}
