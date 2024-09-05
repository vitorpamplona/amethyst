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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.geohash

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.NostrGeohashDataSource
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.navigation.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.ui.note.LoadCityName
import com.vitorpamplona.amethyst.ui.screen.NostrGeoHashFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.RefresheableFeedView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.FollowButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.UnfollowButton
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.StdPadding

@Composable
fun GeoHashScreen(
    tag: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (tag == null) return

    PrepareViewModelsGeoHashScreen(tag, accountViewModel, nav)
}

@Composable
fun PrepareViewModelsGeoHashScreen(
    tag: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val followsFeedViewModel: NostrGeoHashFeedViewModel =
        viewModel(
            key = tag + "GeoHashFeedViewModel",
            factory =
                NostrGeoHashFeedViewModel.Factory(
                    tag,
                    accountViewModel.account,
                ),
        )

    GeoHashScreen(tag, followsFeedViewModel, accountViewModel, nav)
}

@Composable
fun GeoHashScreen(
    tag: String,
    feedViewModel: NostrGeoHashFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val lifeCycleOwner = LocalLifecycleOwner.current

    NostrGeohashDataSource.loadHashtag(tag)

    DisposableEffect(tag) {
        NostrGeohashDataSource.start()
        feedViewModel.invalidateData()
        onDispose {
            NostrGeohashDataSource.loadHashtag(null)
            NostrGeohashDataSource.stop()
        }
    }

    DisposableEffect(lifeCycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    println("Hashtag Start")
                    NostrGeohashDataSource.loadHashtag(tag)
                    NostrGeohashDataSource.start()
                    feedViewModel.invalidateData()
                }
                if (event == Lifecycle.Event.ON_PAUSE) {
                    println("Hashtag Stop")
                    NostrGeohashDataSource.loadHashtag(null)
                    NostrGeohashDataSource.stop()
                }
            }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose { lifeCycleOwner.lifecycle.removeObserver(observer) }
    }

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            TopBarExtensibleWithBackButton(
                title = {
                    DislayGeoTagHeader(tag, Modifier.weight(1f))
                    GeoHashActionOptions(tag, accountViewModel)
                },
                popBack = nav::popBack,
            )
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
fun GeoHashHeader(
    tag: String,
    modifier: Modifier = StdPadding,
    account: AccountViewModel,
    onClick: () -> Unit = {},
) {
    Column(
        Modifier.fillMaxWidth().clickable { onClick() },
    ) {
        Column(modifier = modifier) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                DislayGeoTagHeader(tag, remember { Modifier.weight(1f) })

                GeoHashActionOptions(tag, account)
            }
        }

        HorizontalDivider(
            thickness = DividerThickness,
        )
    }
}

@Composable
fun DislayGeoTagHeader(
    geohash: String,
    modifier: Modifier,
) {
    LoadCityName(geohashStr = geohash) { cityName ->
        Text(
            cityName,
            fontWeight = FontWeight.Bold,
            modifier = modifier,
        )
    }
}

@Composable
fun GeoHashActionOptions(
    tag: String,
    accountViewModel: AccountViewModel,
) {
    val userState by accountViewModel
        .userProfile()
        .live()
        .follows
        .observeAsState()
    val isFollowingTag by
        remember(userState, tag) {
            derivedStateOf { userState?.user?.isFollowingGeohash(tag) ?: false }
        }

    if (isFollowingTag) {
        UnfollowButton {
            if (!accountViewModel.isWriteable()) {
                accountViewModel.toast(
                    R.string.read_only_user,
                    R.string.login_with_a_private_key_to_be_able_to_unfollow,
                )
            } else {
                accountViewModel.unfollowGeohash(tag)
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
                accountViewModel.followGeohash(tag)
            }
        }
    }
}
