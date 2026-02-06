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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.followers.dal

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn

@Stable
class UserProfileFollowersUserFeedViewModel(
    val user: User,
    val account: Account,
) : ViewModel() {
    val followerFilter =
        Filter(
            kinds = listOf(ContactListEvent.KIND),
            tags = mapOf("p" to listOf(user.pubkeyHex)),
        )

    val sortingModel: Comparator<User> =
        compareBy(
            { !account.isFollowing(it) },
            { it.pubkeyHex },
        )

    fun List<Event>.toNonHiddenOwners(): List<User> =
        mapNotNull { event ->
            if (!account.isHidden(event.pubKey)) {
                account.cache.getOrCreateUser(event.pubKey)
            } else {
                null
            }
        }

    val followersFlow: StateFlow<List<User>> =
        account.cache
            .observeEvents(followerFilter)
            .sample(500)
            .map { followerContactLists ->
                followerContactLists.toNonHiddenOwners().sortedWith(sortingModel)
            }.flowOn(Dispatchers.IO)
            .stateIn(
                viewModelScope,
                initialValue = emptyList(),
                started = SharingStarted.Lazily,
            )

    class Factory(
        val user: User,
        val account: Account,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = UserProfileFollowersUserFeedViewModel(user, account) as T
    }
}
