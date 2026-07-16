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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.nip46

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46ClientInfo
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46PermissionAuthorizer
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppSignerPolicy
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.util.toTimeAgo
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One connected NIP-46 remote-signer client, with everything the row needs to render. */
private data class Nip46AppEntry(
    val coordinate: String,
    val clientPubKey: HexKey,
    val policy: AppSignerPolicy?,
    val info: Nip46ClientInfo?,
    val lastUsedSeconds: Long?,
)

/**
 * The remote-signer clients connected to this account. Kept separate from the shared Connected Apps
 * screen because — unlike napplets/nsites/browser origins — each NIP-46 app can carry its own relays
 * (from a `nostrconnect://` offer), which the signer subscribes to in the background for as long as
 * the app stays connected. Surfacing them here, with their relay footprint and last-used time, lets
 * the user prune the background relay connections they no longer need (idle apps are also auto-forgotten
 * after a week; see `Nip46SignerState.IDLE_PRUNE_SECONDS`).
 *
 * Tapping a row opens the shared [Route.ConnectedAppDetail], which already renders NIP-46 clients
 * (history + Forget).
 */
@Composable
fun Nip46ConnectedAppsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val account = accountViewModel.account
    val signerPubKey = remember { account.signer.pubKey }

    var items by remember { mutableStateOf<List<Nip46AppEntry>?>(null) }
    // Bumped on resume so a Forget performed on the detail screen is reflected when we return.
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshKey) {
        items = withContext(Dispatchers.Default) { loadNip46Apps(signerPubKey) }
    }

    Scaffold(
        topBar = { TopBarWithBackButton(stringResource(R.string.nip46_signer_manage_apps), nav) },
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
                            MaterialSymbols.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp),
                        )
                        Text(
                            stringResource(R.string.nip46_signer_apps_empty),
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
                        Nip46AppCard(
                            entry = entry,
                            onClick = { nav.nav(Route.ConnectedAppDetail(entry.coordinate)) },
                        )
                    }
                }
        }
    }
}

@Composable
private fun Nip46AppCard(
    entry: Nip46AppEntry,
    onClick: () -> Unit,
) {
    // Same identity line the detail screen (Nip46AppHeader) uses: the app's self-declared website
    // when it has one, npub otherwise — so a row and its detail never disagree.
    val subtitle = remember(entry.info?.url, entry.clientPubKey) { nip46ClientSubtitle(entry.info?.url, entry.clientPubKey) }
    val title = entry.info?.name?.ifBlank { null } ?: stringResource(R.string.nip46_signer_remote_app)
    val relayCount = entry.info?.relays?.size ?: 0

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                MaterialSymbols.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta =
                    buildList {
                        if (relayCount > 0) add(pluralStringResource(R.plurals.nip46_signer_app_relay_count, relayCount, relayCount))
                        entry.lastUsedSeconds?.let { add(stringResource(R.string.nip46_signer_app_last_used, it.toTimeAgo().trim())) }
                    }.joinToString("  ·  ")
                if (meta.isNotEmpty()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                entry.policy?.let { policy ->
                    SuggestionChip(
                        onClick = {},
                        label = { Text(policy.shortLabel(), style = MaterialTheme.typography.labelSmall) },
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

/**
 * The identity line shown for a NIP-46 client: its self-declared website (host only) when it
 * advertised one, otherwise its npub. Shared by the list card and the detail header so a row and
 * the screen it opens always agree.
 */
internal fun nip46ClientSubtitle(
    url: String?,
    clientPubKey: HexKey,
): String {
    val host =
        url
            ?.ifBlank { null }
            ?.removePrefix("https://")
            ?.removePrefix("http://")
            ?.substringBefore('/')
            ?.ifBlank { null }
    return host ?: runCatching { NPub.create(clientPubKey) }.getOrDefault(clientPubKey.take(12) + "…")
}

private suspend fun loadNip46Apps(signerPubKey: HexKey): List<Nip46AppEntry> {
    val store = Amethyst.instance.signerPermissionStore
    val clientStore = Amethyst.instance.nip46ClientStore
    return store
        .allPolicies()
        .keys
        .filter { Nip46PermissionAuthorizer.belongsTo(it, signerPubKey) }
        .mapNotNull { coordinate ->
            val clientPubKey = Nip46PermissionAuthorizer.clientPubKeyOf(coordinate) ?: return@mapNotNull null
            Nip46AppEntry(
                coordinate = coordinate,
                clientPubKey = clientPubKey,
                policy = store.loadPolicy(coordinate),
                info = clientStore.load(coordinate),
                lastUsedSeconds = store.loadLastUsed(coordinate),
            )
        }.sortedByDescending { it.lastUsedSeconds ?: 0L }
}
