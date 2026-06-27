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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.favorites.FavoriteAppIcon
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.napplet.permissions.NappletPermissionLedger
import com.vitorpamplona.amethyst.commons.napplet.signers.AppSignerPolicy
import com.vitorpamplona.amethyst.commons.napplet.signers.NostrSignerPermissionLedger
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.napplet.resolveNappletMeta
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip5dNapplets.NamedNappletEvent
import com.vitorpamplona.quartz.nip5dNapplets.RootNappletEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.vitorpamplona.amethyst.commons.R as CommonsR

private data class ConnectedAppEntry(
    val coordinate: String,
    val title: String,
    val domain: String,
    val authorPubKey: String,
    val iconUrl: String?,
    val signerPolicy: AppSignerPolicy?,
    val capabilityCount: Int,
)

@Composable
fun ConnectedAppsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val capabilityLedger = remember { NappletPermissionLedger(Amethyst.instance.nappletPermissionStore) }
    val signerLedger = remember { NostrSignerPermissionLedger(Amethyst.instance.signerPermissionStore) }
    val untitled = stringResource(CommonsR.string.napplet_untitled)

    var items by remember { mutableStateOf<List<ConnectedAppEntry>?>(null) }

    LaunchedEffect(Unit) {
        // Load initial list; items is set before child coroutines start so the
        // observeEvents first-emission and the relay fetch both see a non-null list.
        val initial =
            withContext(Dispatchers.Default) {
                loadConnectedApps(capabilityLedger, signerLedger, untitled)
            }
        items = initial

        val manifestFilter = Filter(kinds = listOf(RootNappletEvent.KIND, NamedNappletEvent.KIND))

        // Re-resolve metadata whenever a napplet manifest arrives in LocalCache.
        launch(Dispatchers.Default) {
            LocalCache.observeEvents<Event>(manifestFilter).collect {
                items =
                    items?.map { entry ->
                        val author = entry.coordinate.substringBefore(':')
                        val identifier = entry.coordinate.substringAfter(':', "")
                        val (title, iconUrl) = resolveNappletMeta(author, identifier, untitled)
                        entry.copy(title = title, iconUrl = iconUrl)
                    }
            }
        }

        // Fetch manifests for apps whose metadata isn't in cache yet.
        launch(Dispatchers.IO) {
            val authorsToFetch =
                initial
                    .filter { it.iconUrl == null }
                    .map { it.coordinate.substringBefore(':') }
                    .toSet()
            if (authorsToFetch.isEmpty()) return@launch

            val filter =
                Filter(
                    kinds = listOf(RootNappletEvent.KIND, NamedNappletEvent.KIND),
                    authors = authorsToFetch.toList(),
                )
            val relays = accountViewModel.account.homeRelays.flow.value
            if (relays.isEmpty()) return@launch

            runCatching {
                val events =
                    accountViewModel.account.client.fetchAll(
                        filters = relays.associateWith { listOf(filter) },
                        timeoutMs = 15_000L,
                    )
                events.forEach { LocalCache.justConsume(it, null, false) }
            }
        }
    }

    Scaffold(
        topBar = { TopBarWithBackButton(stringResource(R.string.napplet_permissions_title), nav) },
    ) { padding ->
        val current = items
        when {
            current == null ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            current.isEmpty() ->
                Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            MaterialSymbols.Apps,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp),
                        )
                        Text(
                            stringResource(R.string.napplet_connected_app_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

            else ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(current, key = { it.coordinate }) { entry ->
                        ConnectedAppCard(
                            entry = entry,
                            onClick = { nav.nav(Route.ConnectedAppDetail(entry.coordinate)) },
                        )
                    }
                }
        }
    }
}

@Composable
private fun ConnectedAppCard(
    entry: ConnectedAppEntry,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FavoriteAppIcon(
                app = FavoriteApp.NostrApp(entry.coordinate, entry.title, 0L, entry.iconUrl),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    entry.domain,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    entry.authorPubKey,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (entry.signerPolicy != null) {
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                entry.signerPolicy.shortLabel(),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
                Icon(
                    MaterialSymbols.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun AppSignerPolicy.shortLabel(): String =
    when (this) {
        AppSignerPolicy.FULL_TRUST -> stringResource(R.string.napplet_policy_full_trust)
        AppSignerPolicy.REASONABLE -> stringResource(R.string.napplet_policy_reasonable)
        AppSignerPolicy.PARANOID -> stringResource(R.string.napplet_policy_paranoid)
    }

private suspend fun loadConnectedApps(
    capabilityLedger: NappletPermissionLedger,
    signerLedger: NostrSignerPermissionLedger,
    untitled: String,
): List<ConnectedAppEntry> {
    val capGrants = capabilityLedger.allPersistedGrants()
    val signerPolicies = signerLedger.store.allPolicies()

    val allCoordinates = (capGrants.keys + signerPolicies.keys).toSet()

    return allCoordinates
        .map { coordinate ->
            val author = coordinate.substringBefore(':')
            val identifier = coordinate.substringAfter(':', "")
            val (title, iconUrl) = resolveNappletMeta(author, identifier, untitled)
            val npub = runCatching { NPub.create(author) }.getOrDefault(author.take(12) + "…")
            ConnectedAppEntry(
                coordinate = coordinate,
                title = title,
                domain = identifier.ifBlank { author.take(12) + "…" },
                authorPubKey = npub,
                iconUrl = iconUrl,
                signerPolicy = signerPolicies[coordinate],
                capabilityCount = capGrants[coordinate]?.size ?: 0,
            )
        }.sortedBy { it.title.lowercase() }
}
