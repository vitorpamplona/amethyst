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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.napplets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentity
import com.vitorpamplona.amethyst.commons.napplet.permissions.GrantState
import com.vitorpamplona.amethyst.commons.napplet.permissions.NappletPermissionLedger
import com.vitorpamplona.amethyst.napplet.DataStoreNappletPermissionStore
import com.vitorpamplona.amethyst.napplet.descriptionRes
import com.vitorpamplona.amethyst.napplet.labelRes
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip5dNapplets.NamedNappletEvent
import com.vitorpamplona.quartz.nip5dNapplets.NappletManifest
import com.vitorpamplona.quartz.nip5dNapplets.RootNappletEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.vitorpamplona.amethyst.commons.R as CommonsR

/** One napplet's persisted permission grants, ready to render. */
private data class NappletGrantsUi(
    val identity: NappletIdentity,
    val title: String,
    val capabilities: List<Pair<NappletCapability, GrantState>>,
)

@Composable
fun NappletPermissionsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val context = LocalContext.current
    val ledger = remember { NappletPermissionLedger(DataStoreNappletPermissionStore(context)) }
    val untitled = stringResource(CommonsR.string.napplet_untitled)

    var items by remember { mutableStateOf<List<NappletGrantsUi>?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        items = withContext(Dispatchers.Default) { loadGrants(ledger, untitled) }
    }

    val scope = rememberCoroutineScope()

    fun mutate(block: suspend () -> Unit) {
        scope.launch {
            block()
            reload++
        }
    }

    Scaffold(
        topBar = { TopBarWithBackButton(stringResource(R.string.napplet_permissions), nav) },
    ) { padding ->
        val current = items
        when {
            current == null ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            current.isEmpty() -> EmptyState(Modifier.fillMaxSize().padding(padding))

            else ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(current, key = { it.identity.coordinate }) { napplet ->
                        NappletPermissionCard(
                            napplet = napplet,
                            onSetAllowed = { cap, allowed ->
                                mutate { ledger.record(napplet.identity, cap, if (allowed) GrantState.ALLOW_ALWAYS else GrantState.DENY) }
                            },
                            onRevoke = { cap -> mutate { ledger.revoke(napplet.identity, cap) } },
                            onForget = { mutate { ledger.revokeAll(napplet.identity) } },
                        )
                    }
                }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                MaterialSymbols.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Text(
                stringResource(R.string.napplet_permissions_empty),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.napplet_permissions_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NappletPermissionCard(
    napplet: NappletGrantsUi,
    onSetAllowed: (NappletCapability, Boolean) -> Unit,
    onRevoke: (NappletCapability) -> Unit,
    onForget: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            MaterialSymbols.Apps,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        napplet.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        napplet.identity.authorPubKey.take(12) + "…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            napplet.capabilities.forEach { (cap, grant) ->
                CapabilityRow(
                    capability = cap,
                    grant = grant,
                    onSetAllowed = { onSetAllowed(cap, it) },
                    onRevoke = { onRevoke(cap) },
                )
            }

            TextButton(
                onClick = onForget,
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(MaterialSymbols.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.napplet_permissions_forget))
            }
        }
    }
}

@Composable
private fun CapabilityRow(
    capability: NappletCapability,
    grant: GrantState,
    onSetAllowed: (Boolean) -> Unit,
    onRevoke: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Icon(
            capability.symbol(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(capability.labelRes()), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(capability.descriptionRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (capability.requiresPerUseConsent) {
            // Payments only ever persist a DENY; the user can clear it to allow per-payment prompts again.
            Text(
                stringResource(R.string.napplet_permissions_blocked),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            Switch(
                checked = grant == GrantState.ALLOW_ALWAYS,
                onCheckedChange = onSetAllowed,
            )
        }

        Spacer(Modifier.size(4.dp))
        IconButton(onClick = onRevoke) {
            Icon(
                MaterialSymbols.Block,
                contentDescription = stringResource(R.string.napplet_permissions_revoke),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private suspend fun loadGrants(
    ledger: NappletPermissionLedger,
    untitled: String,
): List<NappletGrantsUi> =
    ledger
        .allPersistedGrants()
        .map { (coordinate, caps) ->
            val author = coordinate.substringBefore(':')
            val identifier = coordinate.substringAfter(':', "")
            NappletGrantsUi(
                identity = NappletIdentity(authorPubKey = author, identifier = identifier),
                title = resolveTitle(author, identifier, untitled),
                capabilities = caps.entries.sortedBy { it.key.ordinal }.map { it.key to it.value },
            )
        }.sortedBy { it.title.lowercase() }

/** Best-effort human title from a cached manifest; falls back to the d-identifier or [untitled]. */
private fun resolveTitle(
    author: String,
    identifier: String,
    untitled: String,
): String {
    val events =
        Amethyst.instance.cache
            .filter(Filter(kinds = listOf(RootNappletEvent.KIND, NamedNappletEvent.KIND), authors = listOf(author)))
            .mapNotNull { it.event }
    val match =
        events.firstOrNull { ev ->
            when (ev) {
                is NamedNappletEvent -> ev.identifier() == identifier
                is RootNappletEvent -> identifier.isEmpty()
                else -> false
            }
        }
    return (match as? NappletManifest)?.title()?.ifBlank { null }
        ?: identifier.ifBlank { untitled }
}

private fun NappletCapability.symbol(): MaterialSymbol =
    when (this) {
        NappletCapability.SHELL -> MaterialSymbols.Tune
        NappletCapability.IDENTITY -> MaterialSymbols.AccountCircle
        NappletCapability.KEYS -> MaterialSymbols.Key
        NappletCapability.RELAY -> MaterialSymbols.Public
        NappletCapability.STORAGE -> MaterialSymbols.Storage
        NappletCapability.VALUE -> MaterialSymbols.Bolt
        NappletCapability.RESOURCE -> MaterialSymbols.Language
        NappletCapability.UPLOAD -> MaterialSymbols.Upload
        NappletCapability.THEME -> MaterialSymbols.Image
        NappletCapability.NOTIFY -> MaterialSymbols.Notifications
        NappletCapability.INC -> MaterialSymbols.SwapHoriz
    }
