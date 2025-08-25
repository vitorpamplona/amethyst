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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.header

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.observeAccountIsHiddenUser
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.FollowSetState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.NostrUserListFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.zaps.ShowUserButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ProfileActions(
    baseUser: User,
    followSetsViewModel: NostrUserListFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val followSetsState by followSetsViewModel.feedContent.collectAsState()
    val (isMenuOpen, setMenuValue) = remember { mutableStateOf(false) }
    val uiScope = rememberCoroutineScope()
    val isMe by
        remember(accountViewModel) { derivedStateOf { accountViewModel.userProfile() == baseUser } }

    if (isMe) {
        EditButton(nav)
    }

    val isHidden by observeAccountIsHiddenUser(accountViewModel.account, baseUser)

    if (isHidden) {
        ShowUserButton { accountViewModel.showUser(baseUser.pubkeyHex) }
    } else {
        DisplayFollowUnfollowButton(baseUser, accountViewModel)
    }

    when (followSetsState) {
        is FollowSetState.Loaded -> {
            val lists = (followSetsState as FollowSetState.Loaded).feed
            FollowSetsActionMenu(
                isMenuOpen = isMenuOpen,
                setMenuOpenState = {
                    uiScope.launch {
                        delay(100)
                        setMenuValue(!isMenuOpen)
                    }
                },
                userHex = baseUser.pubkeyHex,
                followLists = lists,
                addUser = { index, userPubkey, list ->
                    Log.d("Amethyst", "ProfileActions: Updating list ...")
                    followSetsViewModel.addUserToSet(baseUser.pubkeyHex, list, accountViewModel.account)
                    Log.d("Amethyst", "Updated List. New size: ${lists[index].profileList.size}")
                },
                removeUser = { index, userPubkey, list ->
                    Log.d("Amethyst", "ProfileActions: Updating list ...")
                    followSetsViewModel.removeUserFromSet(baseUser.pubkeyHex, list, accountViewModel.account)
                    Log.d("Amethyst", "Updated List. New size: ${lists[index].profileList.size}")
                },
            )
        }
        else -> {}
    }
}
