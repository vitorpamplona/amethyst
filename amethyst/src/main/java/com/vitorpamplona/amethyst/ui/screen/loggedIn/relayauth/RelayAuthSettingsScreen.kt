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
import androidx.compose.foundation.shape.CircleShape
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
import com.vitorpamplona.amethyst.service.relayClient.authCommand.model.DataStoreRelayAuthPermissionStore
import com.vitorpamplona.amethyst.service.relayClient.authCommand.model.RelayAuthPermissionLedger
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.ui.note.timeAgo
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.napplets.PolicyCard
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Avatars shown in a relay's facepile before the "+N" overflow badge. */
private const val FACEPILE_MAX = 3

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

            // One list per relay: its allow/deny state, who it serves (a facepile), and when it was
            // last used. The union of relays we have an override for and relays we've recorded a
            // reason for — so the "why we're logged in" info and the override control live together.
            val relayUrls =
                remember(perRelayOverrides, rationales, lastUsed) {
                    (perRelayOverrides.keys + rationales.keys + lastUsed.keys).toSortedSet()
                }

            Text(
                text = stringResource(R.string.relay_auth_per_relay_overrides),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))

            if (relayUrls.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.relay_auth_no_overrides),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                relayUrls.forEach { url ->
                    RelayCard(
                        url = url,
                        decision = perRelayOverrides[url],
                        servedUsers =
                            rationales[url]
                                ?.values
                                ?.flatten()
                                ?.distinct()
                                .orEmpty(),
                        lastUsedSecs = lastUsed[url],
                        accountViewModel = accountViewModel,
                        onToggle = {
                            scope.launch {
                                // null (allowed by policy) or ALLOW -> block; DENY -> allow.
                                val next =
                                    if (perRelayOverrides[url] == RelayAuthDecision.DENY) {
                                        RelayAuthDecision.ALLOW
                                    } else {
                                        RelayAuthDecision.DENY
                                    }
                                ledger.setDecision(url, next)
                                reloadKey++
                            }
                        },
                        onForget = {
                            scope.launch {
                                // Single "forget" clears both the override and the recorded reason,
                                // so the relay drops off this list entirely.
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

/**
 * One relay's card in the merged list: URL, an allow/deny chip, a facepile of the people it serves,
 * and when it was last used. [decision] is null when the relay is allowed by policy rather than an
 * explicit override; the chip still reads "Allowed" and tapping it records an explicit block.
 */
@Composable
private fun RelayCard(
    url: String,
    decision: RelayAuthDecision?,
    servedUsers: List<HexKey>,
    lastUsedSecs: Long?,
    accountViewModel: AccountViewModel,
    onToggle: () -> Unit,
    onForget: () -> Unit,
) {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = url,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                    modifier = Modifier.weight(1f),
                )
                DecisionChip(decision = decision, onToggle = onToggle)
                IconButton(onClick = onForget) {
                    Icon(MaterialSymbols.Close, contentDescription = stringResource(R.string.relay_auth_forget))
                }
            }
            if (servedUsers.isNotEmpty()) {
                UserFacepile(servedUsers, accountViewModel)
            }
            if (lastUsedSecs != null && lastUsedSecs > 0L) {
                Text(
                    text = stringResource(R.string.relay_auth_last_used, timeAgo(lastUsedSecs, context, prefix = "")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Allow/deny pill for a relay. Green when allowed (explicitly or by policy), red when blocked. */
@Composable
private fun DecisionChip(
    decision: RelayAuthDecision?,
    onToggle: () -> Unit,
) {
    val allowed = decision != RelayAuthDecision.DENY
    SuggestionChip(
        onClick = onToggle,
        label = {
            Text(
                text = stringResource(if (allowed) R.string.relay_auth_decision_allow else R.string.relay_auth_decision_deny),
                style = MaterialTheme.typography.labelSmall,
            )
        },
        colors =
            if (allowed) {
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
}

/** Overlapping avatars for the people a relay serves — [FACEPILE_MAX] pictures then a "+N" badge. */
@Composable
private fun UserFacepile(
    pubkeys: List<HexKey>,
    accountViewModel: AccountViewModel,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
            pubkeys.take(FACEPILE_MAX).forEach { pubkey ->
                LoadRelayAuthUser(pubkey, accountViewModel) { user ->
                    if (user != null) {
                        ClickableUserPicture(
                            baseUser = user,
                            size = 28.dp,
                            accountViewModel = accountViewModel,
                            modifier = Modifier.border(2.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                        )
                    }
                }
            }
        }
        val extra = pubkeys.size - FACEPILE_MAX
        if (extra > 0) {
            Text(
                text = "+$extra",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
