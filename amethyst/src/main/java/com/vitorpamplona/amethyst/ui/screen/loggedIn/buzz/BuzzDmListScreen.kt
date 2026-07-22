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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.buzz

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserName
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.timeAgoShort
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import kotlinx.coroutines.launch

/**
 * The **Buzz Direct Messages** inbox. A Buzz DM is a relay-authoritative NIP-29 group
 * whose id is a UUID, so tapping a row opens the very same [Route.RelayGroup] chat screen
 * every workspace channel uses — this screen only lists the conversations (discovered from
 * the relay's kind-41001 confirmations via [BuzzDmListViewModel]) and starts new ones.
 */
@Composable
fun BuzzDmListScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val pubkey = accountViewModel.account.userProfile().pubkeyHex
    val viewModel: BuzzDmListViewModel = viewModel(key = "BuzzDmList-$pubkey")
    viewModel.bindAccountIfMissing(accountViewModel.account)

    val rows by viewModel.rows.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(R.string.buzz_dm_title), nav) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(stringRes(R.string.buzz_dm_new)) },
                icon = { Icon(symbol = MaterialSymbols.Add, contentDescription = null) },
                onClick = { nav.nav(Route.BuzzNewDm) },
            )
        },
    ) { padding ->
        if (rows.isEmpty()) {
            EmptyDmInbox(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(rows, key = { it.channelId }) { row ->
                    DmRowCard(row, accountViewModel, nav)
                }
            }
        }
    }
}

/** One conversation: a participant avatar (stacked for group DMs), names, host, last-seen, kebab. */
@Composable
private fun DmRowCard(
    row: BuzzDmListViewModel.DmRow,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val groupId = remember(row.channelId, row.relayUrl) { GroupId(row.channelId, row.relayUrl) }
    var menuOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(
        onClick = { nav.nav(Route.RelayGroup(groupId.id, groupId.relayUrl.url)) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DmAvatars(row.others, accountViewModel, nav)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                DmTitle(row.others, accountViewModel)
                Text(
                    text = row.relayUrl.displayUrl(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = timeAgoShort(row.lastActivity, stringRes(R.string.now)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box {
                Icon(
                    symbol = MaterialSymbols.MoreVert,
                    contentDescription = stringRes(R.string.buzz_dm_more),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .clip(CircleShape)
                            .clickable { menuOpen = true }
                            .size(28.dp)
                            .padding(4.dp),
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringRes(R.string.buzz_dm_hide)) },
                        onClick = {
                            menuOpen = false
                            scope.launch {
                                val channel = LocalCache.getOrCreateRelayGroupChannel(groupId)
                                accountViewModel.account.hideBuzzDm(channel)
                            }
                        },
                    )
                }
            }
        }
    }
}

/** The DM title: the other participants' display names, comma-joined (or a "+N" tail). */
@Composable
private fun DmTitle(
    others: List<HexKey>,
    accountViewModel: AccountViewModel,
) {
    // `map` is inline (a @Composable call is legal inside it); resolve every shown name
    // first, then join the plain strings — joinToString is NOT inline so can't call one.
    val text =
        if (others.isEmpty()) {
            stringRes(R.string.buzz_dm_just_you)
        } else {
            val shown = others.take(2).map { UserName(it, accountViewModel) }
            val joined = shown.joinToString(", ")
            if (others.size > 2) "$joined +${others.size - 2}" else joined
        }
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** A resolved display name for [userHex] (falls back to a short pubkey while loading). */
@Composable
private fun UserName(
    userHex: HexKey,
    accountViewModel: AccountViewModel,
): String {
    val user: User = remember(userHex) { LocalCache.getOrCreateUser(userHex) }
    val name by observeUserName(user, accountViewModel)
    return name
}

/** A single avatar for a 1:1 DM, or two overlapping avatars for a group DM. */
@Composable
private fun DmAvatars(
    others: List<HexKey>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    when {
        others.isEmpty() ->
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    symbol = MaterialSymbols.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
        others.size == 1 -> UserPicture(others[0], 44.dp, accountViewModel = accountViewModel, nav = nav)
        else ->
            Box(modifier = Modifier.size(44.dp)) {
                UserPicture(
                    others[1],
                    30.dp,
                    pictureModifier = Modifier.align(Alignment.BottomEnd),
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
                UserPicture(
                    others[0],
                    30.dp,
                    pictureModifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .offset(x = (-2).dp, y = (-2).dp),
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
    }
}

/** Inviting empty state for a fresh DM inbox. */
@Composable
private fun EmptyDmInbox(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.padding(24.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    symbol = MaterialSymbols.AutoMirrored.Send,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp),
                )
            }
            Text(
                text = stringRes(R.string.buzz_dm_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringRes(R.string.buzz_dm_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
