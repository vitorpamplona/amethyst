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

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthDecision
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPolicy
import com.vitorpamplona.amethyst.service.relayClient.authCommand.model.DataStoreRelayAuthPermissionStore
import com.vitorpamplona.amethyst.service.relayClient.authCommand.model.RelayAuthPermissionLedger
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadKey) {
        perRelayOverrides = withContext(Dispatchers.IO) { store.allDecisions() }
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
                        }
                    PolicyCard(
                        selected = globalPolicy == policy,
                        symbol = symbol,
                        title = stringResource(titleRes),
                        description = stringResource(descRes),
                        onClick = { account.settings.changeDefaultRelayAuthPolicy(policy) },
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
        }
    }
}

@Composable
private fun PolicyCard(
    selected: Boolean,
    symbol: MaterialSymbol,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val bgColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(width = if (selected) 2.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(12.dp))
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                symbol = symbol,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) {
                Icon(
                    symbol = MaterialSymbols.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
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
