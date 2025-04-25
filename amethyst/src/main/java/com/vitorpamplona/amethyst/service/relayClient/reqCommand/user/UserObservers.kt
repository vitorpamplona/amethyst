/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.user

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.UserState
import com.vitorpamplona.quartz.nip01Core.metadata.UserMetadata
import com.vitorpamplona.quartz.nip01Core.tags.events.isTaggedEvent
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip65RelayList.RelayUrlFormatter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.sample
import java.math.BigDecimal

@Composable
fun observeUser(user: User): State<UserState?> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user)

    // Subscribe in the LocalCache for changes that arrive in the device
    return user
        .flow()
        .metadata.stateFlow
        .collectAsStateWithLifecycle()
}

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun observeUserName(user: User): State<String> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user)

    val flow =
        remember(user) {
            user
                .flow()
                .metadata.stateFlow
                .mapLatest { it.user.toBestDisplayName() }
                .distinctUntilChanged()
        }

    // Subscribe in the LocalCache for changes that arrive in the device
    return flow.collectAsStateWithLifecycle(user.toBestDisplayName())
}

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun observeUserNip05(user: User): State<String?> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user)

    val flow =
        remember(user) {
            user
                .flow()
                .metadata.stateFlow
                .mapLatest { it.user.info?.nip05 }
                .distinctUntilChanged()
        }

    // Subscribe in the LocalCache for changes that arrive in the device
    return flow.collectAsStateWithLifecycle(user.info?.nip05)
}

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun observeUserAboutMe(user: User): State<String> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(user) {
            user
                .flow()
                .metadata.stateFlow
                .mapLatest { it.user.info?.about ?: "" }
                .distinctUntilChanged()
        }

    return flow.collectAsStateWithLifecycle(user.info?.about ?: "")
}

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun observeUserInfo(user: User): State<UserMetadata?> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(user) {
            user
                .flow()
                .metadata.stateFlow
                .mapLatest { it.user.info }
                .distinctUntilChanged()
        }

    return flow.collectAsStateWithLifecycle(user.info)
}

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun observeUserBanner(user: User): State<String?> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(user) {
            user
                .flow()
                .metadata.stateFlow
                .mapLatest { it.user.info?.banner }
                .distinctUntilChanged()
        }

    return flow.collectAsStateWithLifecycle(user.info?.banner)
}

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun observeUserPicture(user: User): State<String?> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(user) {
            user
                .flow()
                .metadata.stateFlow
                .mapLatest { it.user.info?.picture }
                .distinctUntilChanged()
        }

    return flow.collectAsStateWithLifecycle(user.info?.picture)
}

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun observeUserShortName(user: User): State<String> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(user) {
            user
                .flow()
                .metadata.stateFlow
                .mapLatest { it.user.toBestShortFirstName() }
                .distinctUntilChanged()
        }

    return flow.collectAsStateWithLifecycle(user.toBestShortFirstName())
}

@Composable
fun observeUserFollows(user: User): State<UserState?> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user)

    // Subscribe in the LocalCache for changes that arrive in the device
    return user
        .flow()
        .follows.stateFlow
        .collectAsStateWithLifecycle()
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserFollowCount(user: User): State<Int> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(user) {
            user
                .flow()
                .followers.stateFlow
                .sample(1000)
                .mapLatest { userState ->
                    userState.user.transientFollowCount() ?: 0
                }.distinctUntilChanged()
                .flowOn(Dispatchers.Default)
        }

    return flow.collectAsStateWithLifecycle(0)
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserTagFollows(user: User): State<Int> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(user) {
            user
                .flow()
                .follows.stateFlow
                .sample(1000)
                .mapLatest { userState ->
                    userState.user.latestContactList?.countFollowTags() ?: 0
                }.distinctUntilChanged()
                .flowOn(Dispatchers.Default)
        }

    return flow.collectAsStateWithLifecycle(0)
}

@Composable
fun observeUserBookmarks(user: User): State<UserState?> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user)

    // Subscribe in the LocalCache for changes that arrive in the device
    return user
        .flow()
        .bookmarks.stateFlow
        .collectAsStateWithLifecycle()
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserBookmarkCount(user: User): State<Int> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(user) {
            user
                .flow()
                .followers.stateFlow
                .sample(1000)
                .mapLatest { userState ->
                    userState.user.latestBookmarkList?.countBookmarks() ?: 0
                }.distinctUntilChanged()
                .flowOn(Dispatchers.Default)
        }

    return flow.collectAsStateWithLifecycle(0)
}

@Composable
fun observeUserFollowers(user: User): State<UserState?> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user)

    // Subscribe in the LocalCache for changes that arrive in the device
    return user
        .flow()
        .followers.stateFlow
        .collectAsStateWithLifecycle()
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserFollowerCount(user: User): State<Int> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(user) {
            user
                .flow()
                .followers.stateFlow
                .sample(1000)
                .mapLatest { userState ->
                    userState.user.transientFollowerCount()
                }.distinctUntilChanged()
                .flowOn(Dispatchers.Default)
        }

    return flow.collectAsStateWithLifecycle(0)
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserIsFollowing(
    user1: User,
    user2: User,
): State<Boolean> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user1)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(user1) {
            user1
                .flow()
                .follows.stateFlow
                .sample(1000)
                .mapLatest { userState ->
                    userState.user.isFollowing(user2)
                }.distinctUntilChanged()
                .flowOn(Dispatchers.Default)
        }

    return flow.collectAsStateWithLifecycle(user1.isFollowing(user2))
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserIsFollowingHashtag(
    user: User,
    hashtag: String,
): State<Boolean> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(user) {
            user
                .flow()
                .follows.stateFlow
                .sample(1000)
                .mapLatest { userState ->
                    userState.user.isFollowingHashtag(hashtag)
                }.distinctUntilChanged()
                .flowOn(Dispatchers.Default)
        }

    return flow.collectAsStateWithLifecycle(user.isFollowingHashtag(hashtag))
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserIsFollowingGeohash(
    user: User,
    geohash: String,
): State<Boolean> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(user) {
            user
                .flow()
                .follows.stateFlow
                .sample(1000)
                .mapLatest { userState ->
                    userState.user.isFollowingGeohash(geohash)
                }.distinctUntilChanged()
                .flowOn(Dispatchers.Default)
        }

    return flow.collectAsStateWithLifecycle(user.isFollowingGeohash(geohash))
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserIsFollowingChannel(
    user: User,
    channel: Channel,
): State<Boolean> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(user) {
            user
                .flow()
                .follows.stateFlow
                .sample(1000)
                .mapLatest { userState ->
                    userState.user.latestContactList?.isTaggedEvent(channel.idHex) ?: false
                }.distinctUntilChanged()
                .flowOn(Dispatchers.Default)
        }

    return flow.collectAsStateWithLifecycle(user.latestContactList?.isTaggedEvent(channel.idHex) ?: false)
}

@Composable
fun observeUserZaps(user: User): State<UserState?> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user)

    // Subscribe in the LocalCache for changes that arrive in the device
    return user
        .flow()
        .zaps.stateFlow
        .collectAsStateWithLifecycle()
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserZapAmount(user: User): State<BigDecimal> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(user) {
            user
                .flow()
                .zaps.stateFlow
                .sample(1000)
                .mapLatest { userState ->
                    userState.user.zappedAmount()
                }.distinctUntilChanged()
                .flowOn(Dispatchers.Default)
        }

    return flow.collectAsStateWithLifecycle(BigDecimal.ZERO)
}

@Composable
fun observeUserReports(user: User): State<UserState?> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user)

    // Subscribe in the LocalCache for changes that arrive in the device
    return user
        .flow()
        .reports.stateFlow
        .collectAsStateWithLifecycle()
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserReportCount(user: User): State<Int> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(user) {
            user
                .flow()
                .reports
                .stateFlow
                .sample(1000)
                .mapLatest { userState ->
                    userState.user.reports.values
                        .sumOf { it.size }
                }.distinctUntilChanged()
        }

    return flow.collectAsStateWithLifecycle(0)
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserStatuses(user: User): State<ImmutableList<AddressableNote>> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(user) {
            user
                .flow()
                .statuses
                .stateFlow
                .sample(1000)
                .mapLatest { userState ->
                    LocalCache.findStatusesForUser(userState.user)
                }.distinctUntilChanged()
                .flowOn(Dispatchers.Default)
        }

    return flow.collectAsStateWithLifecycle(persistentListOf())
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserRelayIntoList(
    user: User,
    relayUrl: String,
): State<Boolean> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(user) {
            user
                .flow()
                .relayInfo
                .stateFlow
                .sample(1000)
                .mapLatest { userState ->
                    userState.user.latestContactList
                        ?.relays()
                        ?.none { it.key == relayUrl } == true
                }.distinctUntilChanged()
                .flowOn(Dispatchers.Default)
        }

    return flow.collectAsStateWithLifecycle(false)
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserRoomSubject(
    user: User,
    room: ChatroomKey,
): State<String?> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(user) {
            user
                .flow()
                .messages
                .stateFlow
                .sample(1000)
                .mapLatest { userState ->
                    userState.user.privateChatrooms[room]?.subject
                }.distinctUntilChanged()
                .flowOn(Dispatchers.Default)
        }

    return flow.collectAsStateWithLifecycle(user.privateChatrooms[room]?.subject)
}

data class RelayUsage(
    val relays: List<String> = emptyList(),
    val userRelayList: List<String> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserRelaysUsing(user: User): State<RelayUsage> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(user) {
            combine(user.flow().relays.stateFlow, user.flow().relayInfo.stateFlow) { relays, relayInfo ->
                val userRelaysBeingUsed = relays.user.relaysBeingUsed.map { it.key }
                val currentUserRelays =
                    relayInfo.user.latestContactList
                        ?.relays()
                        ?.map { RelayUrlFormatter.normalize(it.key) } ?: emptyList()

                RelayUsage(userRelaysBeingUsed, currentUserRelays)
            }.sample(1000)
                .distinctUntilChanged()
                .flowOn(Dispatchers.Default)
        }

    return flow.collectAsStateWithLifecycle(RelayUsage())
}
