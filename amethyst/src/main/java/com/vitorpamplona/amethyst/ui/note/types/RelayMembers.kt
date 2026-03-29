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
package com.vitorpamplona.amethyst.ui.note.types

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.quartz.nip43RelayMembers.addMember.RelayAddMemberEvent
import com.vitorpamplona.quartz.nip43RelayMembers.list.RelayMembershipListEvent
import com.vitorpamplona.quartz.nip43RelayMembers.removeMember.RelayRemoveMemberEvent

@Composable
fun RenderRelayMembershipList(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? RelayMembershipListEvent ?: return
    val memberCount = remember(noteEvent) { noteEvent.members().size }

    RelayMemberEventCard(
        icon = Icons.Default.People,
        title = stringRes(R.string.relay_membership_list),
        subtitle = stringRes(R.string.relay_members_count, memberCount),
        nav = nav,
        relayPubKey = noteEvent.pubKey,
    )
}

@Composable
fun RenderRelayAddMember(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? RelayAddMemberEvent ?: return
    val memberKeys = remember(noteEvent) { noteEvent.memberPubKeys() }
    val title =
        if (memberKeys.size == 1) {
            stringRes(R.string.relay_member_added)
        } else {
            stringRes(R.string.relay_members_added, memberKeys.size)
        }
    val subtitle = remember(memberKeys) { memberKeys.joinToString(", ") { it.take(16) + "..." } }

    RelayMemberEventCard(
        icon = Icons.Default.PersonAdd,
        title = title,
        subtitle = subtitle,
        relayPubKey = noteEvent.pubKey,
    )
}

@Composable
fun RenderRelayRemoveMember(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? RelayRemoveMemberEvent ?: return
    val memberKeys = remember(noteEvent) { noteEvent.memberPubKeys() }
    val title =
        if (memberKeys.size == 1) {
            stringRes(R.string.relay_member_removed)
        } else {
            stringRes(R.string.relay_members_removed, memberKeys.size)
        }
    val subtitle = remember(memberKeys) { memberKeys.joinToString(", ") { it.take(16) + "..." } }

    RelayMemberEventCard(
        icon = Icons.Default.PersonRemove,
        title = title,
        subtitle = subtitle,
        relayPubKey = noteEvent.pubKey,
    )
}

@Composable
fun RenderRelayJoinRequest(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    RelayMemberEventCard(
        icon = Icons.Default.PersonAdd,
        title = stringRes(R.string.relay_join_request),
        subtitle = null,
        nav = nav,
        relayPubKey = null,
    )
}

@Composable
fun RenderRelayLeaveRequest(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    RelayMemberEventCard(
        icon = Icons.AutoMirrored.Filled.ExitToApp,
        title = stringRes(R.string.relay_leave_request),
        subtitle = null,
        nav = nav,
        relayPubKey = null,
    )
}

@Composable
private fun RelayMemberEventCard(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    nav: INav? = null,
    relayPubKey: String? = null,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun RelayMembershipListCardPreview() {
    ThemeComparisonColumn {
        RelayMemberEventCard(
            icon = Icons.Default.People,
            title = "Relay membership list",
            subtitle = "42 members",
        )
    }
}

@Preview
@Composable
private fun RelayAddMemberCardPreview() {
    ThemeComparisonColumn {
        RelayMemberEventCard(
            icon = Icons.Default.PersonAdd,
            title = "Member added to relay",
            subtitle = "a1b2c3d4e5f6a7b8...",
        )
    }
}

@Preview
@Composable
private fun RelayRemoveMemberCardPreview() {
    ThemeComparisonColumn {
        RelayMemberEventCard(
            icon = Icons.Default.PersonRemove,
            title = "Member removed from relay",
            subtitle = "a1b2c3d4e5f6a7b8...",
        )
    }
}

@Preview
@Composable
private fun RelayJoinRequestCardPreview() {
    ThemeComparisonColumn {
        RelayMemberEventCard(
            icon = Icons.Default.PersonAdd,
            title = "Relay join request",
            subtitle = null,
        )
    }
}

@Preview
@Composable
private fun RelayLeaveRequestCardPreview() {
    ThemeComparisonColumn {
        RelayMemberEventCard(
            icon = Icons.AutoMirrored.Filled.ExitToApp,
            title = "Relay leave request",
            subtitle = null,
        )
    }
}
