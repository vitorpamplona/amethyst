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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.hashtag

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserIsFollowingHashtag
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.ui.screen.RefresheableFeedView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.hashtag.dal.HashtagFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.hashtag.datasource.HashtagFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.FollowButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.UnfollowButton
import com.vitorpamplona.amethyst.ui.theme.StdPadding

@Composable
fun HashtagScreen(
    tag: Route.Hashtag,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (tag.hashtag.isEmpty()) return

    PrepareViewModelsHashtagScreen(tag, accountViewModel, nav)
}

@SuppressLint("StateFlowValueCalledInComposition")
@Composable
fun PrepareViewModelsHashtagScreen(
    tag: Route.Hashtag,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val hashtagFeedViewModel: HashtagFeedViewModel =
        viewModel(
            key = tag.hashtag + "HashtagFeedViewModel",
            factory =
                HashtagFeedViewModel.Factory(
                    tag.hashtag,
                    accountViewModel.account.followOutboxesOrProxy.flow.value,
                    accountViewModel.account,
                ),
        )

    HashtagScreen(tag, hashtagFeedViewModel, accountViewModel, nav)
}

@Composable
fun HashtagScreen(
    tag: Route.Hashtag,
    feedViewModel: HashtagFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchLifecycleAndUpdateModel(feedViewModel)
    HashtagFilterAssemblerSubscription(tag, accountViewModel)

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            TopBarExtensibleWithBackButton(
                title = {
                    Text("#${tag.hashtag}", modifier = Modifier.weight(1f))
                    HashtagActionOptions(tag.hashtag, accountViewModel)
                },
                popBack = nav::popBack,
            )
        },
        floatingButton = {
            NewHashtagPostButton(tag.hashtag, accountViewModel, nav)
        },
        accountViewModel = accountViewModel,
    ) {
        Column(Modifier.padding(it)) {
            RefresheableFeedView(
                feedViewModel,
                null,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
fun HashtagHeader(
    tag: String,
    modifier: Modifier = StdPadding,
    account: AccountViewModel,
    onClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.then(modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            "#$tag",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )

        HashtagActionOptions(tag, account)
    }
}

@Composable
fun HashtagActionOptions(
    tag: String,
    accountViewModel: AccountViewModel,
) {
    val isFollowingTag by observeUserIsFollowingHashtag(tag, accountViewModel)

    if (isFollowingTag) {
        UnfollowButton {
            if (!accountViewModel.isWriteable()) {
                accountViewModel.toastManager.toast(
                    R.string.read_only_user,
                    R.string.login_with_a_private_key_to_be_able_to_unfollow,
                )
            } else {
                accountViewModel.unfollowHashtag(tag)
            }
        }
    } else {
        FollowButton {
            if (!accountViewModel.isWriteable()) {
                accountViewModel.toastManager.toast(
                    R.string.read_only_user,
                    R.string.login_with_a_private_key_to_be_able_to_follow,
                )
            } else {
                accountViewModel.followHashtag(tag)
            }
        }
    }
}
