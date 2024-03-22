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
package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.NostrHashtagDataSource
import com.vitorpamplona.amethyst.ui.screen.NostrHashtagFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.RefresheableFeedView
import com.vitorpamplona.amethyst.ui.theme.StdPadding

@Composable
fun HashtagScreen(
    tag: String?,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    if (tag == null) return

    PrepareViewModelsHashtagScreen(tag, accountViewModel, nav)
}

@Composable
fun PrepareViewModelsHashtagScreen(
    tag: String,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val followsFeedViewModel: NostrHashtagFeedViewModel =
        viewModel(
            key = tag + "HashtagFeedViewModel",
            factory =
                NostrHashtagFeedViewModel.Factory(
                    tag,
                    accountViewModel.account,
                ),
        )

    HashtagScreen(tag, followsFeedViewModel, accountViewModel, nav)
}

@Composable
fun HashtagScreen(
    tag: String,
    feedViewModel: NostrHashtagFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val lifeCycleOwner = LocalLifecycleOwner.current

    NostrHashtagDataSource.loadHashtag(tag)

    DisposableEffect(tag) {
        NostrHashtagDataSource.start()
        feedViewModel.invalidateData()

        onDispose {
            NostrHashtagDataSource.loadHashtag(null)
            NostrHashtagDataSource.stop()
        }
    }

    DisposableEffect(lifeCycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    println("Hashtag Start")
                    NostrHashtagDataSource.loadHashtag(tag)
                    NostrHashtagDataSource.start()
                    feedViewModel.invalidateData()
                }
                if (event == Lifecycle.Event.ON_PAUSE) {
                    println("Hashtag Stop")
                    NostrHashtagDataSource.loadHashtag(null)
                    NostrHashtagDataSource.stop()
                }
            }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose { lifeCycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier.padding(vertical = 0.dp),
        ) {
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
    val userState by accountViewModel.userProfile().live().follows.observeAsState()
    val isFollowingTag by
        remember(userState) {
            derivedStateOf { userState?.user?.isFollowingHashtagCached(tag) ?: false }
        }

    if (isFollowingTag) {
        UnfollowButton {
            if (!accountViewModel.isWriteable()) {
                accountViewModel.toast(
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
                accountViewModel.toast(
                    R.string.read_only_user,
                    R.string.login_with_a_private_key_to_be_able_to_follow,
                )
            } else {
                accountViewModel.followHashtag(tag)
            }
        }
    }
}
