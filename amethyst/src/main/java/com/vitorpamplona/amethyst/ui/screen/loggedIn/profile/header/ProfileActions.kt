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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.header

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.zaps.ShowUserButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.zaps.WatchIsHiddenUser

@Composable
fun ProfileActions(
    baseUser: User,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val tempFollowLists = remember { generateFollowLists().toMutableStateList() }
    val isMe by
        remember(accountViewModel) { derivedStateOf { accountViewModel.userProfile() == baseUser } }

    if (isMe) {
        EditButton(nav)
    }

    WatchIsHiddenUser(baseUser, accountViewModel) { isHidden ->
        if (isHidden) {
            ShowUserButton { accountViewModel.showUser(baseUser.pubkeyHex) }
        } else {
            DisplayFollowUnfollowButton(baseUser, accountViewModel)
        }
    }

    FollowSetsActionMenu(
        userHex = baseUser.pubkeyHex,
        followLists = tempFollowLists,
        addUser = { index, list ->
            Log.d("Amethyst", "ProfileActions: Updating list ...")
            val newList = tempFollowLists[index].profileList + baseUser.pubkeyHex
            tempFollowLists[index] = tempFollowLists[index].copy(profileList = newList)
            println("Updated List. New size: ${tempFollowLists[index].profileList.size}")
        },
        removeUser = { index ->
            Log.d("Amethyst", "ProfileActions: Updating list ...")
            val newList = tempFollowLists[index].profileList - baseUser.pubkeyHex
            tempFollowLists[index] = tempFollowLists[index].copy(profileList = newList)
            println("Updated List. New size: ${tempFollowLists[index].profileList.size}")
        },
    )
}
