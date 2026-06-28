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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.favorites.FavoriteAppIcon
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentity
import com.vitorpamplona.amethyst.commons.napplet.permissions.GrantState
import com.vitorpamplona.amethyst.commons.napplet.permissions.NappletPermissionLedger
import com.vitorpamplona.amethyst.commons.napplet.signers.AppSignerPolicy
import com.vitorpamplona.amethyst.commons.napplet.signers.NostrOpDecision
import com.vitorpamplona.amethyst.commons.napplet.signers.NostrSignerOp
import com.vitorpamplona.amethyst.commons.napplet.signers.NostrSignerPermissionLedger
import com.vitorpamplona.amethyst.napplet.descriptionRes
import com.vitorpamplona.amethyst.napplet.labelRes
import com.vitorpamplona.amethyst.napplet.resolveNappletMeta
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.vitorpamplona.amethyst.commons.R as CommonsR

private data class ConnectedAppDetailState(
    val title: String,
    val coordinate: String,
    val iconUrl: String?,
    val signerPolicy: AppSignerPolicy?,
    val opOverrides: Map<String, NostrOpDecision>,
    val capabilities: List<Pair<NappletCapability, GrantState>>,
)

@Composable
fun ConnectedAppDetailScreen(
    coordinate: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val capabilityLedger = remember { NappletPermissionLedger(Amethyst.instance.nappletPermissionStore) }
    val signerLedger = remember { NostrSignerPermissionLedger(Amethyst.instance.signerPermissionStore) }
    val untitled = stringResource(CommonsR.string.napplet_untitled)

    var state by remember { mutableStateOf<ConnectedAppDetailState?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(coordinate, reload) {
        state =
            withContext(Dispatchers.Default) {
                loadDetailState(coordinate, capabilityLedger, signerLedger, untitled)
            }
    }

    fun mutate(block: suspend () -> Unit) {
        scope.launch {
            block()
            reload++
        }
    }

    val identity =
        remember(coordinate) {
            NappletIdentity(
                authorPubKey = coordinate.substringBefore(':'),
                identifier = coordinate.substringAfter(':', ""),
            )
        }

    Scaffold(
        topBar = { TopBarWithBackButton(state?.title ?: coordinate.substringAfter(':', "").ifBlank { coordinate.take(12) + "…" }, nav) },
    ) { padding ->
        val current = state
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // App identity header
            AppIdentityHeader(current)

            // Signing trust level section
            if (current.signerPolicy != null) {
                SectionHeader(stringResource(R.string.napplet_connected_app_trust_level))
                PolicyPicker(
                    selected = current.signerPolicy,
                    onSelect = { newPolicy ->
                        mutate { signerLedger.setPolicy(coordinate, newPolicy) }
                    },
                )
            }

            // Signing operation overrides section
            if (current.opOverrides.isNotEmpty()) {
                SectionHeader(stringResource(R.string.napplet_connected_app_op_overrides))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(4.dp)) {
                        current.opOverrides.entries.forEachIndexed { index, (opKey, decision) ->
                            if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                            OpOverrideRow(
                                opKey = opKey,
                                decision = decision,
                                onRevoke = { mutate { signerLedger.revokeOpDecision(coordinate, NostrSignerOp.fromKey(opKey) ?: return@mutate) } },
                            )
                        }
                    }
                }
            }

            // Capabilities section
            if (current.capabilities.isNotEmpty()) {
                SectionHeader(stringResource(R.string.napplet_connected_app_capabilities))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(4.dp)) {
                        current.capabilities.forEachIndexed { index, (cap, grant) ->
                            if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                            CapabilityDetailRow(
                                capability = cap,
                                grant = grant,
                                onSetAllowed = { allowed ->
                                    mutate { capabilityLedger.record(identity, cap, if (allowed) GrantState.ALLOW_ALWAYS else GrantState.DENY) }
                                },
                                onRevoke = { mutate { capabilityLedger.revoke(identity, cap) } },
                            )
                        }
                    }
                }
            }

            // Forget button
            Spacer(Modifier.size(8.dp))
            Button(
                onClick = {
                    mutate {
                        signerLedger.revokeAll(coordinate)
                        capabilityLedger.revokeAll(identity)
                    }
                    nav.popBack()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
            ) {
                Icon(MaterialSymbols.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.napplet_connected_app_forget))
            }
        }
    }
}

@Composable
private fun AppIdentityHeader(state: ConnectedAppDetailState) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FavoriteAppIcon(
                app = FavoriteApp.NostrApp(state.coordinate, state.title, 0L, state.iconUrl),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    state.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val domain = state.coordinate.substringAfter(':', "").ifBlank { state.coordinate.substringBefore(':').take(12) + "…" }
                Text(
                    domain,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun PolicyPicker(
    selected: AppSignerPolicy,
    onSelect: (AppSignerPolicy) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PolicyCard(
            selected = selected == AppSignerPolicy.FULL_TRUST,
            symbol = MaterialSymbols.Favorite,
            label = stringResource(R.string.napplet_policy_full_trust),
            description = stringResource(R.string.napplet_policy_full_trust_desc),
            onClick = { onSelect(AppSignerPolicy.FULL_TRUST) },
        )
        PolicyCard(
            selected = selected == AppSignerPolicy.REASONABLE,
            symbol = MaterialSymbols.Shield,
            label = stringResource(R.string.napplet_policy_reasonable),
            description = stringResource(R.string.napplet_policy_reasonable_desc),
            onClick = { onSelect(AppSignerPolicy.REASONABLE) },
        )
        PolicyCard(
            selected = selected == AppSignerPolicy.PARANOID,
            symbol = MaterialSymbols.Lock,
            label = stringResource(R.string.napplet_policy_paranoid),
            description = stringResource(R.string.napplet_policy_paranoid_desc),
            onClick = { onSelect(AppSignerPolicy.PARANOID) },
        )
    }
}

@Composable
private fun OpOverrideRow(
    opKey: String,
    decision: NostrOpDecision,
    onRevoke: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                NostrSignerOp.fromKey(opKey)?.opLabel() ?: opKey,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            decision.decisionLabel(),
            style = MaterialTheme.typography.labelSmall,
            color =
                when (decision) {
                    NostrOpDecision.DENY -> MaterialTheme.colorScheme.error
                    NostrOpDecision.ALLOW -> MaterialTheme.colorScheme.primary
                    NostrOpDecision.ASK -> MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
        IconButton(onClick = onRevoke) {
            Icon(
                MaterialSymbols.Delete,
                contentDescription = stringResource(R.string.napplet_signer_permissions_revoke_all),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun CapabilityDetailRow(
    capability: NappletCapability,
    grant: GrantState,
    onSetAllowed: (Boolean) -> Unit,
    onRevoke: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(
            capability.symbol(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(capability.labelRes()), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(capability.descriptionRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (capability.requiresPerUseConsent) {
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

@Composable
private fun NostrSignerOp.opLabel(): String =
    when (this) {
        is NostrSignerOp.SignKind -> stringResource(R.string.napplet_op_sign_kind, kind)
        NostrSignerOp.Encrypt -> stringResource(R.string.napplet_op_encrypt)
        NostrSignerOp.Decrypt -> stringResource(R.string.napplet_op_decrypt)
    }

@Composable
private fun NostrOpDecision.decisionLabel(): String =
    when (this) {
        NostrOpDecision.ALLOW -> stringResource(R.string.napplet_decision_allow)
        NostrOpDecision.ASK -> stringResource(R.string.napplet_decision_ask)
        NostrOpDecision.DENY -> stringResource(R.string.napplet_decision_deny)
    }

private suspend fun loadDetailState(
    coordinate: String,
    capabilityLedger: NappletPermissionLedger,
    signerLedger: NostrSignerPermissionLedger,
    untitled: String,
): ConnectedAppDetailState {
    val author = coordinate.substringBefore(':')
    val identifier = coordinate.substringAfter(':', "")
    val identity = NappletIdentity(authorPubKey = author, identifier = identifier)

    val allGrants = capabilityLedger.allPersistedGrants()
    val capGrants =
        allGrants[coordinate]
            ?.entries
            ?.sortedBy { it.key.ordinal }
            ?.map { it.key to it.value }
            ?: emptyList()
    val signerPolicy = signerLedger.store.loadPolicy(coordinate)
    val opOverrides = signerLedger.store.allOpDecisions(coordinate)

    val (title, iconUrl) = resolveNappletMeta(author, identifier, untitled)
    return ConnectedAppDetailState(
        title = title,
        coordinate = coordinate,
        iconUrl = iconUrl,
        signerPolicy = signerPolicy,
        opOverrides = opOverrides,
        capabilities = capGrants,
    )
}
