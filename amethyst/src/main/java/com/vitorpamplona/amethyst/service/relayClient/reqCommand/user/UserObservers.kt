/**
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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.user

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatChannel
import com.vitorpamplona.amethyst.commons.model.nip01Core.UserInfo
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.NoteState
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.hashtagList.HashtagListEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun observeUserName(
    user: User,
    accountViewModel: AccountViewModel,
): State<String> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user, accountViewModel)

    val flow =
        remember(user) {
            user
                .metadata()
                .flow
                .map {
                    it?.info?.bestName() ?: user.pubkeyDisplayHex()
                }.distinctUntilChanged()
        }

    // Subscribe in the LocalCache for changes that arrive in the device
    return flow.collectAsStateWithLifecycle(user.toBestDisplayName())
}

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun observeUserAboutMe(
    user: User,
    accountViewModel: AccountViewModel,
): State<String> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user, accountViewModel)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(user) {
            user.metadata().flow.map {
                it?.info?.about ?: ""
            }
        }

    return flow.collectAsStateWithLifecycle(
        user
            .metadataOrNull()
            ?.flow
            ?.value
            ?.info
            ?.about ?: "",
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun observeUserInfo(
    user: User,
    accountViewModel: AccountViewModel,
): State<UserInfo?> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user, accountViewModel)

    // Subscribe in the LocalCache for changes that arrive in the device
    return user.metadata().flow.collectAsStateWithLifecycle()
}

@SuppressLint("StateFlowValueCalledInComposition")
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun observeUserBanner(
    user: User,
    accountViewModel: AccountViewModel,
): State<String?> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user, accountViewModel)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(user) {
            user
                .metadata()
                .flow
                .map {
                    it?.info?.banner
                }.distinctUntilChanged()
        }

    return flow.collectAsStateWithLifecycle(
        user
            .metadataOrNull()
            ?.flow
            ?.value
            ?.info
            ?.banner,
    )
}

@SuppressLint("StateFlowValueCalledInComposition")
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun observeUserPicture(
    user: User,
    accountViewModel: AccountViewModel,
): State<String?> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user, accountViewModel)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(user) {
            user
                .metadata()
                .flow
                .map {
                    it?.info?.picture
                }.distinctUntilChanged()
        }

    return flow.collectAsStateWithLifecycle(
        user
            .metadataOrNull()
            ?.flow
            ?.value
            ?.info
            ?.picture,
    )
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserTagFollowCount(
    user: User,
    accountViewModel: AccountViewModel,
): State<Int> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user, accountViewModel)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(user) {
            accountViewModel
                .hashtagFollows(user)
                .flow()
                .metadata.stateFlow
                .sample(1000)
                .mapLatest { noteState ->
                    (noteState.note.event as? HashtagListEvent)?.let { accountViewModel.account.hashtagListDecryptionCache.hashtags(it) }?.size ?: 0
                }.onStart {
                    emit((accountViewModel.hashtagFollows(user).event as? HashtagListEvent)?.let { accountViewModel.account.hashtagListDecryptionCache.hashtags(it) }?.size ?: 0)
                }.distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }

    return flow.collectAsStateWithLifecycle(0)
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserTagFollows(
    user: User,
    accountViewModel: AccountViewModel,
): State<List<String>> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user, accountViewModel)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(user) {
            accountViewModel
                .hashtagFollows(user)
                .flow()
                .metadata.stateFlow
                .sample(200)
                .mapLatest { noteState ->
                    (noteState.note.event as? HashtagListEvent)?.let { accountViewModel.account.hashtagListDecryptionCache.hashtags(it) }?.sorted() ?: emptyList()
                }.onStart {
                    emit((accountViewModel.hashtagFollows(user).event as? HashtagListEvent)?.let { accountViewModel.account.hashtagListDecryptionCache.hashtags(it) }?.sorted() ?: emptyList())
                }.distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }

    return flow.collectAsStateWithLifecycle(emptyList())
}

@Composable
fun observeUserBookmarks(
    user: User,
    accountViewModel: AccountViewModel,
): State<NoteState> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user, accountViewModel)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(user) {
            accountViewModel
                .bookmarks(user)
                .flow()
                .metadata.stateFlow
        }

    // Subscribe in the LocalCache for changes that arrive in the device
    return flow.collectAsStateWithLifecycle()
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserBookmarkCount(
    user: User,
    accountViewModel: AccountViewModel,
): State<Int> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user, accountViewModel)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(user) {
            accountViewModel
                .bookmarks(user)
                .flow()
                .metadata.stateFlow
                .sample(200)
                .mapLatest { noteState ->
                    (noteState.note.event as? BookmarkListEvent)?.countBookmarks() ?: 0
                }.distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }

    return flow.collectAsStateWithLifecycle(0)
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserIsFollowing(
    user1: User,
    user2: User,
    accountViewModel: AccountViewModel,
): State<Boolean> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user1, accountViewModel)

    val note =
        remember(user1) {
            accountViewModel.follows(user1)
        }

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(user1) {
            note
                .flow()
                .metadata.stateFlow
                .mapLatest { userState ->
                    val event = userState.note.event as? ContactListEvent
                    event?.isFollowing(user2.pubkeyHex) ?: false
                }.distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }

    return flow.collectAsStateWithLifecycle(
        (note.event as? ContactListEvent)?.isFollowing(user2.pubkeyHex) ?: false,
    )
}

@SuppressLint("StateFlowValueCalledInComposition")
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserIsFollowingHashtag(
    hashtag: String,
    accountViewModel: AccountViewModel,
): State<Boolean> {
    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(accountViewModel) {
            accountViewModel.account.hashtagList.flow
                .mapLatest { hashtags ->
                    hashtag in hashtags
                }.onStart {
                    emit(hashtag in accountViewModel.account.hashtagList.flow.value)
                }.distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }

    return flow.collectAsStateWithLifecycle(hashtag in accountViewModel.account.hashtagList.flow.value)
}

@SuppressLint("StateFlowValueCalledInComposition")
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserIsFollowingGeohash(
    geohash: String,
    accountViewModel: AccountViewModel,
): State<Boolean> {
    val flow =
        remember(accountViewModel) {
            accountViewModel.account.geohashList.flow
                .mapLatest { geohashes ->
                    geohash in geohashes
                }.onStart {
                    emit(geohash in accountViewModel.account.geohashList.flow.value)
                }.distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }

    return flow.collectAsStateWithLifecycle(geohash in accountViewModel.account.geohashList.flow.value)
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserIsFollowingChannel(
    account: Account,
    channel: PublicChatChannel,
    accountViewModel: AccountViewModel,
): State<Boolean> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(account.userProfile(), accountViewModel)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(account) {
            account
                .publicChatList
                .flowSet
                .mapLatest { followingChannels ->
                    channel.idHex in followingChannels
                }.distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }

    @SuppressLint("StateFlowValueCalledInComposition")
    return flow.collectAsStateWithLifecycle(channel.idHex in account.publicChatList.flowSet.value)
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserIsFollowingChannel(
    account: Account,
    channel: EphemeralChatChannel,
    accountViewModel: AccountViewModel,
): State<Boolean> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(account.userProfile(), accountViewModel)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(account) {
            account
                .ephemeralChatList
                .liveEphemeralChatList
                .mapLatest { followingChannels ->
                    channel.roomId in followingChannels
                }.distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }

    @SuppressLint("StateFlowValueCalledInComposition")
    return flow.collectAsStateWithLifecycle(channel.roomId in account.ephemeralChatList.liveEphemeralChatList.value)
}

@Composable
fun observeUserReports(
    user: User,
    accountViewModel: AccountViewModel,
    onUpdate: () -> Unit,
) {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user, accountViewModel)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(user, onUpdate) {
            user
                .reports()
                .receivedReportsByAuthor
                .onEach { onUpdate() }
                .onStart { onUpdate() }
        }.collectAsStateWithLifecycle(emptyMap())
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserReportCount(
    user: User,
    accountViewModel: AccountViewModel,
): State<Int> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user, accountViewModel)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow = remember(user) { user.reports().countFlow() }

    return flow.collectAsStateWithLifecycle(0)
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserContactCardsScore(
    user: User,
    accountViewModel: AccountViewModel,
): State<Int?> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user, accountViewModel)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow = remember(user) { user.cards().rankFlow(accountViewModel.account.trustProviderList) }

    return flow.collectAsStateWithLifecycle(null)
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserContactCardsFollowerCount(
    user: User,
    accountViewModel: AccountViewModel,
): State<String> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user, accountViewModel)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow = remember(user) { user.cards().followerCountStrFlow(accountViewModel.account.trustProviderList) }

    return flow.collectAsStateWithLifecycle("--")
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserStatuses(
    user: User,
    accountViewModel: AccountViewModel,
): State<ImmutableList<AddressableNote>> {
    // Subscribe in the relay for changes in the metadata of this user.
    UserFinderFilterAssemblerSubscription(user, accountViewModel)

    return user.statusState().statuses.collectAsStateWithLifecycle(persistentListOf())
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeUserRelayIntoList(
    relayUrl: NormalizedRelayUrl,
    accountViewModel: AccountViewModel,
): State<Boolean> {
    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(accountViewModel) {
            accountViewModel.account.trustedRelays.flow
                .mapLatest { relays ->
                    relayUrl in relays
                }.distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }

    return flow.collectAsStateWithLifecycle(false)
}
