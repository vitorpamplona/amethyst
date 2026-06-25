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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.napplet.signers.AppSignerPolicy
import com.vitorpamplona.amethyst.commons.napplet.signers.NostrOpDecision
import com.vitorpamplona.amethyst.commons.napplet.signers.NostrSignerOp
import com.vitorpamplona.amethyst.commons.napplet.signers.NostrSignerPermissionLedger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppSignerPermissionEntry(
    val coordinate: String,
    val policy: AppSignerPolicy,
    val opOverrides: Map<String, NostrOpDecision>,
)

class NappletSignerPermissionsViewModel(
    private val ledger: NostrSignerPermissionLedger,
) : ViewModel() {
    private val _entries = MutableStateFlow<List<AppSignerPermissionEntry>>(emptyList())
    val entries: StateFlow<List<AppSignerPermissionEntry>> = _entries.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            val policies = ledger.store.allPolicies()
            _entries.value =
                policies.map { (coord, policy) ->
                    AppSignerPermissionEntry(
                        coordinate = coord,
                        policy = policy,
                        opOverrides = ledger.store.allOpDecisions(coord),
                    )
                }
        }
    }

    fun revokeAll(coordinate: String) {
        viewModelScope.launch {
            ledger.revokeAll(coordinate)
            reload()
        }
    }

    fun revokeOp(
        coordinate: String,
        opKey: String,
    ) {
        viewModelScope.launch {
            NostrSignerOp.fromKey(opKey)?.let { ledger.revokeOpDecision(coordinate, it) }
            reload()
        }
    }
}

@Composable
fun NappletSignerPermissionsScreen(viewModel: NappletSignerPermissionsViewModel) {
    val entries by viewModel.entries.collectAsState()

    if (entries.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.napplet_signer_permissions_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(entries, key = { it.coordinate }) { entry ->
                AppSignerPermissionCard(
                    entry = entry,
                    onRevokeAll = { viewModel.revokeAll(entry.coordinate) },
                    onRevokeOp = { opKey -> viewModel.revokeOp(entry.coordinate, opKey) },
                )
            }
        }
    }
}

@Composable
private fun AppSignerPermissionCard(
    entry: AppSignerPermissionEntry,
    onRevokeAll: () -> Unit,
    onRevokeOp: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        entry.coordinate.substringAfter(":").ifBlank { entry.coordinate },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        entry.coordinate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRevokeAll) {
                    Icon(
                        symbol = MaterialSymbols.Delete,
                        contentDescription = stringResource(R.string.napplet_signer_permissions_revoke_all),
                    )
                }
            }

            SuggestionChip(
                onClick = {},
                label = { Text(entry.policy.label()) },
            )

            if (entry.opOverrides.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    stringResource(R.string.napplet_permissions_overrides),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                for ((opKey, decision) in entry.opOverrides) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            NostrSignerOp.fromKey(opKey)?.label() ?: opKey,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            decision.label(),
                            style = MaterialTheme.typography.labelSmall,
                            color =
                                if (decision == NostrOpDecision.DENY) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                        )
                        IconButton(onClick = { onRevokeOp(opKey) }) {
                            Icon(
                                symbol = MaterialSymbols.Delete,
                                contentDescription = null,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppSignerPolicy.label(): String =
    when (this) {
        AppSignerPolicy.FULL_TRUST -> stringResource(R.string.napplet_policy_full_trust)
        AppSignerPolicy.REASONABLE -> stringResource(R.string.napplet_policy_reasonable)
        AppSignerPolicy.PARANOID -> stringResource(R.string.napplet_policy_paranoid)
    }

@Composable
private fun NostrSignerOp.label(): String =
    when (this) {
        is NostrSignerOp.SignKind -> stringResource(R.string.napplet_op_sign_kind, kind)
        NostrSignerOp.Encrypt -> stringResource(R.string.napplet_op_encrypt)
        NostrSignerOp.Decrypt -> stringResource(R.string.napplet_op_decrypt)
    }

@Composable
private fun NostrOpDecision.label(): String =
    when (this) {
        NostrOpDecision.ALLOW -> stringResource(R.string.napplet_decision_allow)
        NostrOpDecision.ASK -> stringResource(R.string.napplet_decision_ask)
        NostrOpDecision.DENY -> stringResource(R.string.napplet_decision_deny)
    }
