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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.lists

import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.ui.dal.FeedFilter
import com.vitorpamplona.amethyst.ui.dal.FollowSetFeedFilter
import com.vitorpamplona.amethyst.ui.feeds.InvalidatableContent
import com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.equalImmutableLists
import com.vitorpamplona.ammolite.relays.BundledUpdate
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip51Lists.peopleList.PeopleListEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

// TODO: Investigate the addition of feed filters, for bookmark sets and general ones.
open class NostrListFeedViewModel(
    val dataSource: FeedFilter<FollowSet>,
) : ViewModel(),
    InvalidatableContent {
    private val _feedContent = MutableStateFlow<FollowSetState>(FollowSetState.Loading)
    val feedContent = _feedContent.asStateFlow()

    private fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            refreshSuspended()
        }
    }

    fun getFollowSetNote(
        noteIdentifier: String,
        account: Account,
    ): AddressableNote? {
        checkNotInMainThread()
        val potentialNote = account.userProfile().followSetNotes.find { it.dTag() == noteIdentifier }
        return potentialNote
    }

    fun followSetExistsWithName(
        setName: String,
        account: Account,
    ): Boolean {
        checkNotInMainThread()
        val potentialNote =
            account
                .userProfile()
                .followSetNotes
                .find { (it.event as PeopleListEvent).nameOrTitle() == setName }
        return potentialNote != null
    }

    override val isRefreshing: MutableState<Boolean> = mutableStateOf(false)

    private fun refreshSuspended() {
        checkNotInMainThread()
        try {
            isRefreshing.value = true
            val oldFeedState = _feedContent.value

            val newSets = dataSource.loadTop().toImmutableList()

            if (oldFeedState is FollowSetState.Loaded) {
                val oldFeedList = oldFeedState.feed.toImmutableList()
                // Using size as a proxy for has changed.
                if (!equalImmutableLists(newSets, oldFeedList)) {
                    updateFeed(newSets)
                }
            } else {
                updateFeed(newSets)
            }
        } catch (e: Exception) {
            Log.e(
                "NostrListFeedViewModel",
                "refreshSuspended: Error loading or refreshing feed -> ${e.message}",
            )
            _feedContent.update { FollowSetState.FeedError(e.message.toString()) }
        } finally {
            isRefreshing.value = false
        }
    }

    fun addFollowSet(
        setName: String,
        setDescription: String?,
        setType: ListVisibility,
        account: Account,
    ) {
        if (account.settings.isWriteable()) {
            println("You are in read-only mode. Please login to make modifications.")
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                PeopleListEvent.createListWithDescription(
                    dTag = UUID.randomUUID().toString(),
                    title = setName,
                    description = setDescription,
                    isPrivate = setType == ListVisibility.Private,
                    signer = account.signer,
                ) {
                    account.sendMyPublicAndPrivateOutbox(it)
                }
            }
        }
    }

    fun renameFollowSet(
        newName: String,
        followSet: FollowSet,
        account: Account,
    ) {
        if (!account.settings.isWriteable()) {
            println("You are in read-only mode. Please login to make modifications.")
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                val setEvent = getFollowSetNote(followSet.identifierTag, account)?.event as PeopleListEvent
                PeopleListEvent.modifyListName(
                    earlierVersion = setEvent,
                    newName = newName,
                    signer = account.signer,
                ) {
                    account.sendMyPublicAndPrivateOutbox(it)
                }
            }
        }
    }

    fun deleteFollowSet(
        followSet: FollowSet,
        account: Account,
    ) {
        if (!account.settings.isWriteable()) {
            println("You are in read-only mode. Please login to make modifications.")
            return
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                val followSetEvent = getFollowSetNote(followSet.identifierTag, account)?.event as PeopleListEvent
                val deletionEvent = account.signer.sign(DeletionEvent.build(listOf(followSetEvent)))
                account.sendMyPublicAndPrivateOutbox(deletionEvent)
            }
        }
    }

    fun addUserToSet(
        userProfileHex: String,
        followSet: FollowSet,
        account: Account,
    ) {
        if (!account.settings.isWriteable()) {
            println("You are in read-only mode. Please login to make modifications.")
            return
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                val followSetEvent = getFollowSetNote(followSet.identifierTag, account)?.event as PeopleListEvent
                PeopleListEvent.addUser(
                    earlierVersion = followSetEvent,
                    pubKeyHex = userProfileHex,
                    isPrivate = followSet.visibility == ListVisibility.Private,
                    signer = account.signer,
                ) {
                    account.sendMyPublicAndPrivateOutbox(it)
                }
            }
        }
    }

    fun removeUserFromSet(
        userProfileHex: String,
        followSet: FollowSet,
        account: Account,
    ) {
        if (!account.settings.isWriteable()) {
            println("You are in read-only mode. Please login to make modifications.")
            return
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                val followSetEvent = getFollowSetNote(followSet.identifierTag, account)?.event as PeopleListEvent
                PeopleListEvent.removeUser(
                    earlierVersion = followSetEvent,
                    pubKeyHex = userProfileHex,
                    signer = account.signer,
                ) {
                    account.sendMyPublicAndPrivateOutbox(it)
                }
            }
        }
    }

    private fun updateFeed(sets: ImmutableList<FollowSet>) {
        if (sets.isNotEmpty()) {
            _feedContent.update { FollowSetState.Loaded(sets) }
        } else {
            _feedContent.update { FollowSetState.Empty }
        }
    }

    private val bundler = BundledUpdate(2000, Dispatchers.IO)

    override fun invalidateData(ignoreIfDoing: Boolean) {
//        refresh()
        bundler.invalidate(ignoreIfDoing) {
            // adds the time to perform the refresh into this delay
            // holding off new updates in case of heavy refresh routines.

            refreshSuspended()
        }
    }

    var collectorJob: Job? = null

    init {
        Log.d("Init", this.javaClass.simpleName)
        Log.d(this.javaClass.simpleName, " FollowSetState : ${_feedContent.value}")
        collectorJob =
            viewModelScope.launch(Dispatchers.IO) {
                LocalCache.live.newEventBundles.collect { newNotes ->

                    invalidateData()
                }
            }
    }

    override fun onCleared() {
        Log.d("Init", "OnCleared: ${this.javaClass.simpleName}")
        bundler.cancel()
        collectorJob?.cancel()
        super.onCleared()
    }

    @Stable
    class Factory(
        val account: Account,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = NostrListFeedViewModel(FollowSetFeedFilter(account)) as T
    }
}
