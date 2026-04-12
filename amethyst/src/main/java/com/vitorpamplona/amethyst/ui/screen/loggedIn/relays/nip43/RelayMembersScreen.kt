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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.nip43

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.IAccountViewModel
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.UserCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.fetchAsFlow
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip43RelayMembers.joinRequest.RelayJoinRequestEvent
import com.vitorpamplona.quartz.nip43RelayMembers.leaveRequest.RelayLeaveRequestEvent
import com.vitorpamplona.quartz.nip43RelayMembers.list.RelayMembershipListEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelayMembersScreen(
    relayUrl: String,
    accountViewModel: IAccountViewModel,
    nav: INav,
) {
    @Suppress("NAME_SHADOWING")
    val accountViewModel = accountViewModel as AccountViewModel
    val normalizedRelayUrl = remember(relayUrl) { RelayUrlNormalizer.normalizeOrNull(relayUrl) }
    if (normalizedRelayUrl == null) return

    var members by remember { mutableStateOf<List<HexKey>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isMember by remember { mutableStateOf(false) }
    var joinRequestSent by remember { mutableStateOf(false) }
    var leaveRequestSent by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(normalizedRelayUrl) {
        launch(Dispatchers.IO) {
            val filter =
                Filter(
                    kinds = listOf(RelayMembershipListEvent.KIND),
                    limit = 1,
                )

            val events =
                accountViewModel.account.client
                    .fetchAsFlow(normalizedRelayUrl, filter)
                    .lastOrNull()

            val membershipEvent =
                events
                    ?.mapNotNull { it as? RelayMembershipListEvent }
                    ?.maxByOrNull { it.createdAt }

            val memberList = membershipEvent?.members() ?: emptyList()
            members = memberList
            isMember = memberList.contains(accountViewModel.account.signer.pubKey)
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringRes(R.string.relay_members_title, normalizedRelayUrl.displayUrl()),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding),
        ) {
            MembershipActions(
                isMember = isMember,
                isLoading = isLoading,
                joinRequestSent = joinRequestSent,
                leaveRequestSent = leaveRequestSent,
                onJoinRequest = {
                    scope.launch(Dispatchers.IO) {
                        sendJoinRequest(normalizedRelayUrl, accountViewModel)
                        joinRequestSent = true
                    }
                },
                onLeaveRequest = {
                    scope.launch(Dispatchers.IO) {
                        sendLeaveRequest(normalizedRelayUrl, accountViewModel)
                        leaveRequestSent = true
                    }
                },
            )

            HorizontalDivider()

            if (isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = stringRes(R.string.relay_members_loading))
                }
            } else if (members.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Default.People,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringRes(R.string.relay_members_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    text = stringRes(R.string.relay_members_count, members.size),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(members, key = { it }) { memberPubKey ->
                        val user = remember(memberPubKey) { accountViewModel.account.cache.getOrCreateUser(memberPubKey) }
                        UserCompose(
                            baseUser = user,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MembershipActions(
    isMember: Boolean,
    isLoading: Boolean,
    joinRequestSent: Boolean,
    leaveRequestSent: Boolean,
    onJoinRequest: () -> Unit,
    onLeaveRequest: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLoading) return@Row

        if (isMember) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringRes(R.string.relay_members_you_are_member),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            if (leaveRequestSent) {
                Text(
                    text = stringRes(R.string.relay_members_leave_sent),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                OutlinedButton(
                    onClick = onLeaveRequest,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = stringRes(R.string.relay_members_request_leave))
                }
            }
        } else {
            if (joinRequestSent) {
                Text(
                    text = stringRes(R.string.relay_members_join_sent),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Button(onClick = onJoinRequest) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = stringRes(R.string.relay_members_request_join))
                }
            }
        }
    }
}

@Preview
@Composable
private fun MembershipActionsNotMemberPreview() {
    ThemeComparisonColumn {
        MembershipActions(
            isMember = false,
            isLoading = false,
            joinRequestSent = false,
            leaveRequestSent = false,
            onJoinRequest = {},
            onLeaveRequest = {},
        )
    }
}

@Preview
@Composable
private fun MembershipActionsIsMemberPreview() {
    ThemeComparisonColumn {
        MembershipActions(
            isMember = true,
            isLoading = false,
            joinRequestSent = false,
            leaveRequestSent = false,
            onJoinRequest = {},
            onLeaveRequest = {},
        )
    }
}

@Preview
@Composable
private fun MembershipActionsJoinSentPreview() {
    ThemeComparisonColumn {
        MembershipActions(
            isMember = false,
            isLoading = false,
            joinRequestSent = true,
            leaveRequestSent = false,
            onJoinRequest = {},
            onLeaveRequest = {},
        )
    }
}

@Preview
@Composable
private fun MembershipActionsLeaveSentPreview() {
    ThemeComparisonColumn {
        MembershipActions(
            isMember = true,
            isLoading = false,
            joinRequestSent = false,
            leaveRequestSent = true,
            onJoinRequest = {},
            onLeaveRequest = {},
        )
    }
}

suspend fun sendJoinRequest(
    relay: NormalizedRelayUrl,
    accountViewModel: IAccountViewModel,
) {
    @Suppress("NAME_SHADOWING")
    val accountViewModel = accountViewModel as AccountViewModel
    val template = RelayJoinRequestEvent.build()
    val signedEvent = accountViewModel.account.signer.sign(template)
    accountViewModel.account.cache.justConsumeMyOwnEvent(signedEvent)
    accountViewModel.account.client.publish(signedEvent, setOf(relay))
}

suspend fun sendLeaveRequest(
    relay: NormalizedRelayUrl,
    accountViewModel: IAccountViewModel,
) {
    @Suppress("NAME_SHADOWING")
    val accountViewModel = accountViewModel as AccountViewModel
    val template = RelayLeaveRequestEvent.build()
    val signedEvent = accountViewModel.account.signer.sign(template)
    accountViewModel.account.cache.justConsumeMyOwnEvent(signedEvent)
    accountViewModel.account.client.publish(signedEvent, setOf(relay))
}
