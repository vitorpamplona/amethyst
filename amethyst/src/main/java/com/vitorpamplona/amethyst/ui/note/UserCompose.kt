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
package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.observeAccountIsHiddenUser
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserIsFollowing
import com.vitorpamplona.amethyst.ui.layouts.listItem.SlimListItem
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav.nav
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.FollowButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.ListButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.UnfollowButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.zaps.ShowUserButton
import com.vitorpamplona.amethyst.ui.theme.Size55dp
import com.vitorpamplona.amethyst.ui.theme.StdPadding

@Composable
fun UserCompose(
    baseUser: User,
    modifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    SlimListItem(
        modifier = modifier.clickable { nav.nav(routeFor(baseUser)) },
        leadingContent = {
            UserPicture(baseUser, Size55dp, accountViewModel = accountViewModel, nav = nav)
        },
        headlineContent = {
            UsernameDisplay(baseUser, accountViewModel = accountViewModel)
        },
        supportingContent = {
            AboutDisplay(baseUser, accountViewModel)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserActionOptions(baseUser, accountViewModel, nav)
            }
        },
    )
}

@Composable
fun UserActionOptions(
    baseAuthor: User,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val isHidden by observeAccountIsHiddenUser(accountViewModel.account, baseAuthor)
    if (isHidden) {
        ShowUserButton { accountViewModel.show(baseAuthor) }
    } else {
        ShowFollowingOrUnfollowingButton(baseAuthor, accountViewModel)
        ListButton { nav.nav(Route.PeopleListManagement(baseAuthor.pubkeyHex)) }
    }
}

@Composable
fun ShowFollowingOrUnfollowingButton(
    baseAuthor: User,
    accountViewModel: AccountViewModel,
) {
    val isFollowing = observeUserIsFollowing(accountViewModel.account.userProfile(), baseAuthor, accountViewModel)

    if (isFollowing.value) {
        UnfollowButton(true) {
            if (!accountViewModel.isWriteable()) {
                accountViewModel.toastManager.toast(
                    R.string.read_only_user,
                    R.string.login_with_a_private_key_to_be_able_to_unfollow,
                )
            } else {
                accountViewModel.unfollow(baseAuthor)
            }
        }
    } else {
        FollowButton(R.string.follow, true) {
            if (!accountViewModel.isWriteable()) {
                accountViewModel.toastManager.toast(
                    R.string.read_only_user,
                    R.string.login_with_a_private_key_to_be_able_to_follow,
                )
            } else {
                accountViewModel.follow(baseAuthor)
            }
        }
    }
}

@Composable
fun UserComposeNoAction(
    baseUser: User,
    modifier: Modifier = StdPadding,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(
        modifier = modifier.clickable { nav.nav(routeFor(baseUser)) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserPicture(baseUser, Size55dp, accountViewModel = accountViewModel, nav = nav)

        Column(modifier = remember { Modifier.padding(start = 10.dp).weight(1f) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UsernameDisplay(baseUser, accountViewModel = accountViewModel)
            }

            AboutDisplay(baseUser, accountViewModel)
        }
    }
}
