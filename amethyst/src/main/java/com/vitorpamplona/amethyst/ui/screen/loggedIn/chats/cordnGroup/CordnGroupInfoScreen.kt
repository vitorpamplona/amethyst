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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.cordnGroup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.cordn.GroupExposure
import com.vitorpamplona.amethyst.commons.cordn.ui.CordnExposureCard
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.cordnGroups.CordnGroupChatroom
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.back
import com.vitorpamplona.amethyst.commons.resources.cordn_group_untitled
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.types.observeUserNameByHex
import com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.QrCodeDrawer
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.cordn.spec00Coordinator.JoinRequest
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.launch

/**
 * What this room is, and what the coordinator can see of it.
 *
 * The **exposure card's real home**. The link screen shows the same disclosure
 * before joining, when it is a decision; this shows it after, when it is a
 * fact worth being able to check. §8 is only meaningful if it is available at
 * both moments — a privacy property nobody can look up again is a claim, not a
 * property.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CordnGroupInfoScreen(
    coordinatorPubKey: HexKey,
    gid: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val runtime = accountViewModel.account.cordnRuntime
    val room = remember(coordinatorPubKey, gid) { runtime?.groups?.get(coordinatorPubKey, gid) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { nav.popBack() }) {
                        Icon(MaterialSymbols.AutoMirrored.ArrowBack, contentDescription = stringRes(Res.string.back))
                    }
                },
                title = { Text(stringRes(R.string.cordn_group_info)) },
            )
        },
    ) { padding ->
        if (room == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text(stringRes(R.string.cordn_group_unavailable))
            }
            return@Scaffold
        }

        CordnGroupInfo(room, coordinatorPubKey, accountViewModel, nav, Modifier.padding(padding))
    }
}

@Composable
private fun CordnGroupInfo(
    room: CordnGroupChatroom,
    coordinatorPubKey: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier = Modifier,
) {
    val name by room.name.collectAsStateWithLifecycle()
    val description by room.description.collectAsStateWithLifecycle()
    val members by room.members.collectAsStateWithLifecycle()
    val admins by room.adminPubkeys.collectAsStateWithLifecycle()
    val epoch by room.epoch.collectAsStateWithLifecycle()

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = name?.takeIf { it.isNotBlank() } ?: stringRes(Res.string.cordn_group_untitled, room.gid.take(8)),
            style = MaterialTheme.typography.headlineSmall,
        )
        description?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        InfoRow(stringRes(R.string.cordn_info_coordinator), coordinatorPubKey)
        InfoRow(stringRes(R.string.cordn_info_gid), room.gid)
        InfoRow(stringRes(R.string.cordn_info_epoch), epoch.toString())
        InfoRow(stringRes(R.string.cordn_info_members), members.size.toString())

        // The roster itself, not only its size. "4 members" in a group whose
        // whole point is knowing exactly who can read you is the one number
        // that is no use on its own.
        members.forEach { member ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                UserPicture(userHex = member, size = 28.dp, accountViewModel = accountViewModel, nav = nav)
                Text(
                    text = observeUserNameByHex(member, accountViewModel),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                if (member in admins) {
                    Text(
                        text = stringRes(R.string.cordn_info_admin_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        InfoRow(
            label = stringRes(R.string.cordn_info_admins),
            // An empty admin set is not "none configured" — spec/01.md makes it
            // permanently egalitarian, so saying "0" would read as a group
            // waiting to be set up rather than one that decided.
            value =
                if (admins.isEmpty()) {
                    stringRes(R.string.cordn_info_admins_egalitarian)
                } else {
                    admins.size.toString()
                },
        )

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        ShareGroup(room, coordinatorPubKey, accountViewModel)

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        JoinRequests(room, coordinatorPubKey, accountViewModel, nav)

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        // Computed, never asserted. Hardcoding these made the card look like a
        // disclosure while reporting the same four values for every group --
        // which is exactly the regression §7 risk 4 of the plan warns about.
        val runtime = accountViewModel.account.cordnRuntime
        val exposure by
            produceState<GroupExposure?>(null, runtime, coordinatorPubKey, room.gid) {
                value =
                    runtime
                        ?.sessionOrNull(coordinatorPubKey)
                        ?.runCatching { exposure(room.gid) }
                        ?.getOrNull()
            }

        exposure?.let { CordnExposureCard(it) }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Who is asking to get in, and the two buttons that answer them.
 *
 * ## Not gated on being an admin, because cordn has no such gate
 *
 * `spec/01.md` §5.3 makes `admin_pubkeys` presentation metadata, and neither
 * the spec nor the reference coordinator restricts who may commit — see
 * `CordnGroupPolicy`'s "why there is no authorization hook". Any member can
 * add anyone. Hiding this section behind an admin check would invent a
 * boundary the protocol does not have, and worse, imply to everyone else that
 * one is protecting them. The line under the heading says so out loud.
 *
 * ## Fetched on a tap, never polled
 *
 * Same reason as the invitations screen: every call to a coordinator is
 * metadata (`spec/00.md` §8), so a live count of pending requests would be a
 * steady heartbeat telling the coordinator this group is open on someone's
 * screen.
 */
@Composable
private fun JoinRequests(
    room: CordnGroupChatroom,
    coordinatorPubKey: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val runtime = accountViewModel.account.cordnRuntime ?: return
    val scope = rememberCoroutineScope()
    val failed = stringRes(R.string.cordn_requests_failed)

    var requests by remember { mutableStateOf<List<JoinRequest>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        busy = true
        error = null
        try {
            requests = runtime.joinRequests(coordinatorPubKey, room.gid)
        } catch (e: Exception) {
            error = e.message ?: failed
        } finally {
            busy = false
        }
    }

    Text(stringRes(R.string.cordn_requests_title), style = MaterialTheme.typography.titleSmall)
    Text(
        text = stringRes(R.string.cordn_requests_any_member),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    OutlinedButton(onClick = { scope.launch { reload() } }, enabled = !busy) {
        Text(stringRes(R.string.cordn_requests_check))
    }

    error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }

    val loaded = requests
    if (loaded != null && !busy) {
        if (loaded.isEmpty()) {
            Text(
                text = stringRes(R.string.cordn_requests_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        loaded.forEach { request ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                UserPicture(userHex = request.pubKey, size = 28.dp, accountViewModel = accountViewModel, nav = nav)
                Text(
                    // Admitting someone to an encrypted group off sixteen hex
                    // characters is not a decision anyone can actually make.
                    text = observeUserNameByHex(request.pubKey, accountViewModel),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = {
                    scope.launch {
                        try {
                            runtime.acceptJoinRequest(coordinatorPubKey, request)
                        } catch (e: Exception) {
                            error = e.message ?: failed
                        }
                        reload()
                    }
                }) {
                    Text(stringRes(R.string.cordn_requests_accept))
                }
                TextButton(onClick = {
                    scope.launch {
                        try {
                            runtime.declineJoinRequest(coordinatorPubKey, request)
                        } catch (e: Exception) {
                            error = e.message ?: failed
                        }
                        reload()
                    }
                }) {
                    Text(stringRes(R.string.cordn_requests_decline))
                }
            }
        }
    }
}

/**
 * The group's `cordn1…` ref, as text and as a QR.
 *
 * The ref is built from the live MLS group (`CordnGroupManager.shareRef`), so
 * it carries the `gid` and the coordinator that actually serves it rather than
 * whatever this screen was navigated with. It is public by design — `spec/02.md`
 * gives it no secret — and holding one makes nobody a member: the most it does
 * is let someone ask, which is what the line under it says.
 *
 * A QR because a ref is a long bech32 string nobody wants to read aloud, and
 * because the person you are handing a group to is usually in the room.
 */
@Composable
private fun ShareGroup(
    room: CordnGroupChatroom,
    coordinatorPubKey: HexKey,
    accountViewModel: AccountViewModel,
) {
    val runtime = accountViewModel.account.cordnRuntime ?: return
    val ref =
        remember(room.gid, coordinatorPubKey) {
            runtime
                .sessionOrNull(coordinatorPubKey)
                ?.manager
                ?.runCatching { shareRef(room.gid).encode() }
                ?.getOrNull()
        } ?: return

    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Text(stringRes(R.string.cordn_share_title), style = MaterialTheme.typography.titleSmall)
    Text(
        text = stringRes(R.string.cordn_share_explainer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    SelectionContainer {
        Text(ref, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
    }

    QrCodeDrawer(ref, Modifier.size(220.dp))

    OutlinedButton(
        onClick = {
            clipboard.setText(AnnotatedString(ref))
            copied = true
        },
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Text(stringRes(if (copied) R.string.cordn_share_copied else R.string.cordn_share_copy))
    }
}
