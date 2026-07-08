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
package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.LoadRelayGroupChannel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupPreviewSubscription
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip29RelayGroups.GroupInviteLink

/**
 * Renders a NIP-29 group invite link (`<relay>'<groupId>[?code=<code>]`) as a full-width
 * card. It draws immediately with a stable layout — a robohash avatar seeded from the group
 * id, the id as a placeholder name, and the host relay — then fills in the real name, picture
 * and member count reactively as the relay-signed metadata arrives, WITHOUT changing the
 * card's structure or height. While on screen it also warms the group (live metadata +
 * recent messages/threads) so tapping it opens an already-populated screen.
 */
@Composable
fun RelayGroupCard(
    linkText: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val invite = remember(linkText) { GroupInviteLink.parse(linkText) }
    if (invite == null) {
        // Detection produced this, so it should always parse; degrade to a plain link if not.
        ClickableRelayGroupLink(linkText, nav)
        return
    }

    val groupId = remember(invite) { invite.toGroupId() }

    LoadRelayGroupChannel(groupId, accountViewModel) { channel ->
        RelayGroupCardContent(channel, invite.code, accountViewModel, nav)
    }
}

@Composable
private fun RelayGroupCardContent(
    baseChannel: RelayGroupChannel,
    inviteCode: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    RelayGroupPreviewSubscription(baseChannel, accountViewModel.dataSources().relayGroupPreview, accountViewModel)

    // Recompose in place when the relay-signed metadata / roster changes.
    val channelState by baseChannel
        .flow()
        .metadata.stateFlow
        .collectAsStateWithLifecycle()
    val channel = channelState.channel as? RelayGroupChannel ?: baseChannel

    val autoPlayGif by accountViewModel.settings.autoPlayVideosFlow.collectAsStateWithLifecycle()
    val memberCount = channel.memberCount()

    // Relay host is known immediately; the member count fills in when the roster loads.
    // Both render on the same single line so nothing reflows as the count arrives.
    val relayLabel = channel.groupId.relayUrl.displayUrl()
    val subtitle =
        if (memberCount > 0) {
            "$relayLabel · ${pluralStringResource(R.plurals.relay_group_member_count, memberCount, memberCount)}"
        } else {
            relayLabel
        }

    OutlinedCard(
        onClick = {
            nav.nav(
                Route.RelayGroup(channel.groupId.id, channel.groupId.relayUrl.url, inviteCode = inviteCode),
            )
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RobohashFallbackAsyncImage(
                robot = channel.groupId.id,
                model = channel.profilePicture(),
                contentDescription = channel.toBestDisplayName(),
                modifier = Modifier.size(48.dp).clip(CircleShape),
                loadProfilePicture = accountViewModel.settings.showProfilePictures(),
                loadRobohash = accountViewModel.settings.isNotPerformanceMode(),
                autoPlayGif = autoPlayGif,
            )

            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (channel.isPrivate()) {
                        Icon(
                            symbol = MaterialSymbols.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Text(
                        text = channel.toBestDisplayName(),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Icon(
                symbol = MaterialSymbols.ChevronRight,
                contentDescription = stringRes(R.string.relay_group_open),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
