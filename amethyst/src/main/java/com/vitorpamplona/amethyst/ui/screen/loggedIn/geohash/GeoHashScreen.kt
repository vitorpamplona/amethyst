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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.geohash

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserIsFollowingGeohash
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.ui.note.creators.location.LoadCityName
import com.vitorpamplona.amethyst.ui.screen.RefresheableFeedView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.geohash.dal.GeoHashFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.geohash.datasource.GeoHashFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.FollowButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.UnfollowButton

@Composable
fun GeoHashScreen(
    tag: Route.Geohash,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (tag.geohash.isEmpty()) return

    PrepareViewModelsGeoHashScreen(tag, accountViewModel, nav)
}

@SuppressLint("StateFlowValueCalledInComposition")
@Composable
fun PrepareViewModelsGeoHashScreen(
    tag: Route.Geohash,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val geohashViewModel: GeoHashFeedViewModel =
        viewModel(
            key = tag.geohash + "GeoHashFeedViewModel",
            factory =
                GeoHashFeedViewModel.Factory(
                    tag.geohash,
                    accountViewModel.account.followOutboxes.flow.value,
                    accountViewModel.account,
                ),
        )

    GeoHashScreen(tag, geohashViewModel, accountViewModel, nav)
}

@Composable
fun GeoHashScreen(
    tag: Route.Geohash,
    feedViewModel: GeoHashFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchLifecycleAndUpdateModel(feedViewModel)
    GeoHashFilterAssemblerSubscription(tag, accountViewModel)

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            TopBarExtensibleWithBackButton(
                title = {
                    DisplayGeoTagHeader(tag.geohash, Modifier.weight(1f))
                    GeoHashActionOptions(tag.geohash, accountViewModel)
                },
                popBack = nav::popBack,
            )
        },
        floatingButton = {
            NewGeoPostButton(tag.geohash, accountViewModel, nav)
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
fun DisplayGeoTagHeader(
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
    val isFollowingTag by observeUserIsFollowingGeohash(accountViewModel.userProfile(), tag, accountViewModel)

    if (isFollowingTag) {
        UnfollowButton {
            if (!accountViewModel.isWriteable()) {
                accountViewModel.toastManager.toast(
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
                accountViewModel.toastManager.toast(
                    R.string.read_only_user,
                    R.string.login_with_a_private_key_to_be_able_to_follow,
                )
            } else {
                accountViewModel.followGeohash(tag)
            }
        }
    }
}
