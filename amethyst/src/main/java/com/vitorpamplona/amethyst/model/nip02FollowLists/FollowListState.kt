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
package com.vitorpamplona.amethyst.model.nip02FollowLists

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.AccountSettings
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.UserState
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohashes
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip02FollowList.tags.ContactTag
import com.vitorpamplona.quartz.nip73ExternalIds.location.GeohashId
import com.vitorpamplona.quartz.nip73ExternalIds.topics.HashtagId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

class FollowListState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) {
    // fun getEphemeralChatListAddress() = cache.getOrCreateUser(signer.pubKey)

    fun getFollowListUser(): User = cache.getOrCreateUser(signer.pubKey)

    fun getFollowListFlow(): StateFlow<UserState> = getFollowListUser().flow().follows.stateFlow

    fun getFollowListEvent(): ContactListEvent? = getFollowListUser().latestContactList

    @OptIn(ExperimentalCoroutinesApi::class)
    private val innerFlow: Flow<Kind3Follows> =
        getFollowListFlow().transformLatest {
            emit(buildKind3Follows(it.user.latestContactList ?: settings.backupContactList))
        }

    val flow =
        innerFlow
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                // this has priority.
                buildKind3Follows(getFollowListEvent() ?: settings.backupContactList),
            )

    /**
     This contains a big OR of everything the user wants to see in the a single feed.
     */
    @Immutable
    class Kind3Follows(
        val authors: Set<String> = emptySet(),
        val authorsPlusMe: Set<String>,
        val hashtags: Set<String> = emptySet(),
        val geotags: Set<String> = emptySet(),
        val communities: Set<String> = emptySet(),
    ) {
        val geotagScopes: Set<String> = geotags.mapTo(mutableSetOf<String>()) { GeohashId.toScope(it) }
        val hashtagScopes: Set<String> = hashtags.mapTo(mutableSetOf<String>()) { HashtagId.toScope(it) }
    }

    fun buildKind3Follows(latestContactList: ContactListEvent?): Kind3Follows {
        // makes sure the output include only valid p tags
        val verifiedFollowingUsers = latestContactList?.verifiedFollowKeySet() ?: emptySet()

        return Kind3Follows(
            authors = verifiedFollowingUsers,
            authorsPlusMe = verifiedFollowingUsers + signer.pubKey,
            hashtags =
                latestContactList
                    ?.unverifiedFollowTagSet()
                    ?.map { it.lowercase() }
                    ?.toSet() ?: emptySet(),
            geotags =
                latestContactList
                    ?.geohashes()
                    ?.toSet() ?: emptySet(),
            communities =
                latestContactList
                    ?.verifiedFollowAddressSet()
                    ?.toSet() ?: emptySet(),
        )
    }

    fun follow(
        user: User,
        onDone: (ContactListEvent) -> Unit,
    ) {
        if (!signer.isWriteable()) return
        val contactList = getFollowListEvent()

        if (contactList != null) {
            ContactListEvent.followUser(contactList, user.pubkeyHex, signer, onReady = onDone)
        } else {
            ContactListEvent.createFromScratch(
                followUsers = listOf(ContactTag(user.pubkeyHex, user.bestRelayHint(), null)),
                relayUse = emptyMap(),
                signer = signer,
                onReady = onDone,
            )
        }
    }

    fun unfollow(
        user: User,
        onDone: (ContactListEvent) -> Unit,
    ) {
        if (!signer.isWriteable()) return
        val contactList = getFollowListEvent()

        if (contactList != null && contactList.tags.isNotEmpty()) {
            ContactListEvent.unfollowUser(
                contactList,
                user.pubkeyHex,
                signer,
                onReady = onDone,
            )
        }
    }

    init {
        settings.backupContactList?.let {
            Log.d("AccountRegisterObservers", "Loading saved contacts ${it.toJson()}")

            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) { LocalCache.justConsumeMyOwnEvent(it) }
        }

        // saves contact list for the next time.
        scope.launch(Dispatchers.Default) {
            Log.d("AccountRegisterObservers", "Kind 3 Collector Start")
            getFollowListFlow().collect {
                Log.d("AccountRegisterObservers", "Updating Kind 3 ${signer.pubKey}")
                settings.updateContactListTo(it.user.latestContactList)
            }
        }
    }
}
