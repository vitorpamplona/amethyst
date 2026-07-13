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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.relayauth.AuthPurposeKind
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthDecision
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPermissionStore
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPolicy
import com.vitorpamplona.amethyst.model.nip11RelayInfo.loadRelayInfo
import com.vitorpamplona.amethyst.service.relayClient.authCommand.compose.LoadRelayAuthUser
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.ui.note.timeAgo
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.napplets.PolicyCard
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.SettingsDivider
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.SettingsSection
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.SettingsSwitchTile
import com.vitorpamplona.amethyst.ui.theme.MediumRelayIconModifier
import com.vitorpamplona.amethyst.ui.theme.RelayIconFilter
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrlOrNull
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
    val store: RelayAuthPermissionStore = account.relayAuthPermissions
    val ledger = account.relayAuthLedger
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
        // The union of relays we have an override for and relays we've recorded a reason for — so the
        // "why we're logged in" info and the override control live together, one row each.
        val relayUrls =
            remember(perRelayOverrides, rationales, lastUsed) {
                (perRelayOverrides.keys + rationales.keys + lastUsed.keys).toSortedSet().toList()
            }

        // LazyColumn so only the visible per-relay rows compose (each builds a NIP-11 icon and
        // avatars — real per-row work). Blocks 1 & 2 are small and fixed, so they share one item.
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    // Block 1: when to authenticate (the mode).
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GroupHeader(stringResource(R.string.relay_auth_global_policy))
                        RelayAuthPolicy.entries.forEach { policy ->
                            val (titleRes, descRes, symbol) =
                                when (policy) {
                                    RelayAuthPolicy.ALWAYS ->
                                        Triple(R.string.relay_auth_policy_always, R.string.relay_auth_policy_always_desc, MaterialSymbols.LockOpen)
                                    RelayAuthPolicy.NEVER ->
                                        Triple(R.string.relay_auth_policy_never, R.string.relay_auth_policy_never_desc, MaterialSymbols.Lock)
                                    RelayAuthPolicy.CUSTOM ->
                                        Triple(R.string.relay_auth_policy_custom, R.string.relay_auth_policy_custom_desc, MaterialSymbols.Tune)
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

                    // Block 2: what to log in to (the custom toggles), as settings switch tiles.
                    if (globalPolicy == RelayAuthPolicy.CUSTOM) {
                        val myRelays by account.settings.relayAuthTrustMyRelaysAndVenues.collectAsState()
                        val readFollows by account.settings.relayAuthTrustReadFollows.collectAsState()
                        val messageFollows by account.settings.relayAuthTrustMessageFollows.collectAsState()
                        val messageStrangers by account.settings.relayAuthTrustMessageStrangers.collectAsState()

                        SettingsSection(R.string.relay_auth_custom_section) {
                            SettingsSwitchTile(
                                icon = MaterialSymbols.Dns,
                                title = R.string.relay_auth_toggle_my_relays,
                                description = R.string.relay_auth_toggle_my_relays_desc,
                                checked = myRelays,
                                onCheckedChange = { account.settings.changeRelayAuthTrustMyRelaysAndVenues(it) },
                            )
                            SettingsDivider()
                            SettingsSwitchTile(
                                icon = MaterialSymbols.Download,
                                title = R.string.relay_auth_toggle_read_follows,
                                description = R.string.relay_auth_toggle_read_follows_desc,
                                checked = readFollows,
                                onCheckedChange = { account.settings.changeRelayAuthTrustReadFollows(it) },
                            )
                            SettingsDivider()
                            SettingsSwitchTile(
                                icon = MaterialSymbols.Mail,
                                title = R.string.relay_auth_toggle_message_follows,
                                description = R.string.relay_auth_toggle_message_follows_desc,
                                checked = messageFollows,
                                onCheckedChange = { account.settings.changeRelayAuthTrustMessageFollows(it) },
                            )
                            SettingsDivider()
                            SettingsSwitchTile(
                                icon = MaterialSymbols.Public,
                                title = R.string.relay_auth_toggle_message_strangers,
                                description = R.string.relay_auth_toggle_message_strangers_desc,
                                checked = messageStrangers,
                                onCheckedChange = { account.settings.changeRelayAuthTrustMessageStrangers(it) },
                            )
                        }
                    }

                    // Block 3 header — its rows are the lazy items below.
                    GroupHeader(stringResource(R.string.relay_auth_per_relay_overrides))
                }
                Spacer(Modifier.height(8.dp))
            }

            if (relayUrls.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.relay_auth_no_overrides),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            } else {
                // Each row is its own lazy item but shares one rounded-card background: the first/last
                // clip the top/bottom corners so the contiguous rows read as a single settings card.
                itemsIndexed(relayUrls, key = { _, url -> url }) { index, url ->
                    Column(
                        modifier =
                            Modifier
                                .clip(sectionCardShape(index, relayUrls.size))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        if (index > 0) SettingsDivider()
                        RelayRow(
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
                            nav = nav,
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
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

/** Primary-colored section label, matching [SettingsSection]'s header used across settings. */
@Composable
private fun GroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

/** Corner shape for one row of a grouped settings card: round the outer corners of the first and
 *  last rows only, so contiguous rows read as a single rounded card. */
private fun sectionCardShape(
    index: Int,
    count: Int,
): Shape =
    when {
        count <= 1 -> RoundedCornerShape(20.dp)
        index == 0 -> RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        index == count - 1 -> RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
        else -> RectangleShape
    }

/**
 * One relay's row in the per-relay list: NIP-11 icon + shortened URL (tap the row to open the relay's
 * info screen), when it was last used, a facepile of the people it serves, an Allow/Deny chip, and a
 * Forget button. [decision] is null when the relay is allowed by policy rather than an explicit
 * override; the chip still reads "Allowed" and tapping it records an explicit block. Styled to match
 * the app's other relay lists (icon-led rows separated by dividers).
 */
@Composable
private fun RelayRow(
    url: String,
    decision: RelayAuthDecision?,
    servedUsers: List<HexKey>,
    lastUsedSecs: Long?,
    accountViewModel: AccountViewModel,
    nav: INav,
    onToggle: () -> Unit,
    onForget: () -> Unit,
) {
    val context = LocalContext.current
    val relay = remember(url) { url.normalizeRelayUrlOrNull() }

    Row(
        modifier =
            Modifier
                .clickable { nav.nav(Route.RelayInfo(url)) }
                .fillMaxWidth()
                .padding(start = 16.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RelayIcon(relay, url, accountViewModel)
        Column(Modifier.weight(1f)) {
            Text(
                text = relay?.displayUrl() ?: url,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            if (servedUsers.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                UserFacepile(servedUsers, accountViewModel)
            }
            if (lastUsedSecs != null && lastUsedSecs > 0L) {
                Text(
                    text = stringResource(R.string.relay_auth_last_used, timeAgo(lastUsedSecs, context, prefix = "")),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        DecisionChip(decision = decision, onToggle = onToggle)
        IconButton(onClick = onForget) {
            Icon(MaterialSymbols.Close, contentDescription = stringResource(R.string.relay_auth_forget))
        }
    }
}

/** The relay's NIP-11 icon (robohash fallback), matching the other relay lists in the app. */
@Composable
private fun RelayIcon(
    relay: NormalizedRelayUrl?,
    url: String,
    accountViewModel: AccountViewModel,
) {
    val info = if (relay != null) loadRelayInfo(relay).value else null
    RobohashFallbackAsyncImage(
        robot = info?.id ?: relay?.displayUrl() ?: url,
        model = info?.icon,
        contentDescription = stringResource(R.string.relay_info, url),
        colorFilter = RelayIconFilter,
        modifier = MediumRelayIconModifier,
        loadProfilePicture = accountViewModel.settings.showProfilePictures(),
        loadRobohash = accountViewModel.settings.isNotPerformanceMode(),
    )
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
