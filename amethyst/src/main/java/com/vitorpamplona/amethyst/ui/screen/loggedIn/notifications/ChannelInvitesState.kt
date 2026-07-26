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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzChannelInvite
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzChannelInvites
import com.vitorpamplona.amethyst.model.Account
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The channels somebody else added the viewer to that are still awaiting a decision.
 *
 * An entry drops out the moment it stops being a question: accepting writes the group into kind-10009
 * (so `joined` covers it and the ordinary Messages row takes over), dismissing records the channel in
 * `dismissedChannelInvites`, and leaving makes the relay withdraw the membership. Nothing here asserts
 * membership — the relay already granted that — it only tracks whose call it is to surface the channel.
 *
 * Modelled on [OpenPollsState]: a small always-on projection the Notifications screen and the Messages
 * "New Requests" tab both render, so the two surfaces can never disagree about what is pending.
 */
@Stable
class ChannelInvitesState(
    private val account: Account,
    scope: CoroutineScope,
) {
    val flow: StateFlow<List<BuzzChannelInvite>> =
        combine(
            BuzzChannelInvites.flow.map { it[account.userProfile().pubkeyHex] ?: emptyMap() },
            account.settings.dismissedChannelInvites,
            account.relayGroupList.liveRelayGroupList,
        ) { invites, dismissed, joined ->
            val joinedIds = joined.mapTo(HashSet()) { it.groupId }
            invites.values
                .filter { it.channelId !in dismissed && it.channelId !in joinedIds }
                .sortedByDescending { it.createdAt }
        }.flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())
}
