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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.relayauth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.relayauth.AuthPurposeKind
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthDecision
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPolicy
import com.vitorpamplona.amethyst.service.relayClient.authCommand.compose.LoadRelayAuthUser
import com.vitorpamplona.amethyst.service.relayClient.authCommand.compose.relayAuthReasonRes
import com.vitorpamplona.amethyst.service.relayClient.authCommand.model.DataStoreRelayAuthPermissionStore
import com.vitorpamplona.amethyst.service.relayClient.authCommand.model.RelayAuthPermissionLedger
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.note.timeAgo
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.napplets.PolicyCard
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_RATIONALE_ROWS = 8

@Composable
fun RelayAuthSettingsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val account = accountViewModel.account
    val store: DataStoreRelayAuthPermissionStore = Amethyst.instance.relayAuthPermissionStore
    val ledger = remember { RelayAuthPermissionLedger(store, { account.settings.defaultRelayAuthPolicy.value }) }
    val scope = rememberCoroutineScope()

    val globalPolicy by account.settings.defaultRelayAuthPolicy.collectAsState()

    var perRelayOverrides by remember { mutableStateOf<Map<String, RelayAuthDecision>>(emptyMap()) }
    var rationales by remember { mutableStateOf<Map<String, Map<AuthPurposeKind, Set<HexKey>>>>(emptyMap()) }
    var lastUsed by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadKey) {
        withContext(Dispatchers.IO) {
            perRelayOverrides = store.allDecisions()
            rationales = store.allRationales()
            lastUsed = store.allLastUsed()
        }
    }

    Scaffold(
        topBar = { TopBarWithBackButton(stringResource(R.string.relay_auth_settings_title), nav) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.relay_auth_global_policy),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RelayAuthPolicy.entries.forEach { policy ->
                    val (titleRes, descRes, symbol) =
                        when (policy) {
                            RelayAuthPolicy.ALWAYS ->
                                Triple(
                                    R.string.relay_auth_policy_always,
                                    R.string.relay_auth_policy_always_desc,
                                    MaterialSymbols.LockOpen,
                                )
                            RelayAuthPolicy.NEVER ->
                                Triple(
                                    R.string.relay_auth_policy_never,
                                    R.string.relay_auth_policy_never_desc,
                                    MaterialSymbols.Lock,
                                )
                            RelayAuthPolicy.IF_IN_MY_LIST ->
                                Triple(
                                    R.string.relay_auth_policy_if_in_my_list,
                                    R.string.relay_auth_policy_if_in_my_list_desc,
                                    MaterialSymbols.PrivacyTip,
                                )
                            RelayAuthPolicy.TRUSTED_FOLLOWS ->
                                Triple(
                                    R.string.relay_auth_policy_trusted_follows,
                                    R.string.relay_auth_policy_trusted_follows_desc,
                                    MaterialSymbols.Group,
                                )
                        }
                    PolicyCard(
                        selected = globalPolicy == policy,
                        symbol = symbol,
                        label = stringResource(titleRes),
                        description = stringResource(descRes),
                        onClick = { account.settings.changeDefaultRelayAuthPolicy(policy) },
                    )
                }
            }

            if (globalPolicy == RelayAuthPolicy.TRUSTED_FOLLOWS) {
                val trustReads by account.settings.relayAuthTrustFollowsForReads.collectAsState()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.relay_auth_trust_reads),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(R.string.relay_auth_trust_reads_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = trustReads,
                        onCheckedChange = { account.settings.changeRelayAuthTrustFollowsForReads(it) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            if (perRelayOverrides.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.relay_auth_per_relay_overrides),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(4.dp)) {
                        perRelayOverrides.entries.sortedBy { it.key }.forEachIndexed { index, (url, decision) ->
                            if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                            PerRelayOverrideRow(
                                url = url,
                                decision = decision,
                                onRemove = {
                                    scope.launch {
                                        ledger.clearDecision(url)
                                        reloadKey++
                                    }
                                },
                                onToggle = {
                                    scope.launch {
                                        val next =
                                            if (decision == RelayAuthDecision.ALLOW) {
                                                RelayAuthDecision.DENY
                                            } else {
                                                RelayAuthDecision.ALLOW
                                            }
                                        ledger.setDecision(url, next)
                                        reloadKey++
                                    }
                                },
                            )
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.relay_auth_no_overrides),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (rationales.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.relay_auth_why_authenticated),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))

                rationales.entries.sortedBy { it.key }.forEach { (url, rationale) ->
                    RelayRationaleCard(
                        url = url,
                        rationale = rationale,
                        lastUsedSecs = lastUsed[url],
                        accountViewModel = accountViewModel,
                        onForget = {
                            scope.launch {
                                ledger.clearDecision(url)
                                store.clearRationale(url)
                                reloadKey++
                            }
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun RelayRationaleCard(
    url: String,
    rationale: Map<AuthPurposeKind, Set<HexKey>>,
    lastUsedSecs: Long?,
    accountViewModel: AccountViewModel,
    onForget: () -> Unit,
) {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = url,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onForget) {
                    Text(stringResource(R.string.relay_auth_forget))
                }
            }
            if (lastUsedSecs != null && lastUsedSecs > 0L) {
                Text(
                    text = stringResource(R.string.relay_auth_last_used, timeAgo(lastUsedSecs, context, prefix = "")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            rationale.forEach { (kind, pubkeys) ->
                Text(
                    text = stringResource(relayAuthReasonRes(kind)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Bounded, non-lazy list: an outbox relay's rationale can name many people, so cap
                // the rows shown here (the full set is already bounded in the store too).
                pubkeys.take(MAX_RATIONALE_ROWS).forEach { pubkey ->
                    RationaleUserRow(pubkey, accountViewModel)
                }
                if (pubkeys.size > MAX_RATIONALE_ROWS) {
                    Text(
                        text = stringResource(R.string.relay_auth_and_others),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RationaleUserRow(
    pubkey: HexKey,
    accountViewModel: AccountViewModel,
) {
    LoadRelayAuthUser(pubkey, accountViewModel) { user ->
        if (user != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 8.dp),
            ) {
                ClickableUserPicture(user, 28.dp, accountViewModel)
                UsernameDisplay(user, accountViewModel = accountViewModel)
            }
        }
    }
}

@Composable
private fun PerRelayOverrideRow(
    url: String,
    decision: RelayAuthDecision,
    onRemove: () -> Unit,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = url,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SuggestionChip(
            onClick = onToggle,
            label = {
                Text(
                    text =
                        if (decision == RelayAuthDecision.ALLOW) {
                            stringResource(R.string.relay_auth_decision_allow)
                        } else {
                            stringResource(R.string.relay_auth_decision_deny)
                        },
                    style = MaterialTheme.typography.labelSmall,
                )
            },
            colors =
                if (decision == RelayAuthDecision.ALLOW) {
                    SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        labelColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                },
        )
        IconButton(onClick = onRemove) {
            Icon(MaterialSymbols.Close, contentDescription = stringResource(R.string.relay_auth_remove_override))
        }
    }
}
