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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.url

import android.annotation.SuppressLint
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.navigation.bottombars.FabBottomBarPadded
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.ui.screen.RefresheableFeedView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.url.dal.UrlFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.url.datasource.UrlFilterAssemblerSubscription
import com.vitorpamplona.quartz.nip01Core.tags.references.HttpUrlFormatter
import com.vitorpamplona.quartz.nip73ExternalIds.urls.UrlId

@Composable
fun UrlScreen(
    route: Route.Url,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (route.url.isEmpty()) return

    PrepareViewModelsUrlScreen(route, accountViewModel, nav)
}

@SuppressLint("StateFlowValueCalledInComposition")
@Composable
fun PrepareViewModelsUrlScreen(
    route: Route.Url,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val normalizedUrl = UrlId.toScope(route.url)
    val urlFeedViewModel: UrlFeedViewModel =
        viewModel(
            key = normalizedUrl + "UrlFeedViewModel",
            factory =
                UrlFeedViewModel.Factory(
                    normalizedUrl,
                    accountViewModel.account.followOutboxesOrProxy.flow.value,
                    accountViewModel.account,
                ),
        )

    UrlScreen(route, urlFeedViewModel, accountViewModel, nav)
}

@Composable
fun UrlScreen(
    route: Route.Url,
    feedViewModel: UrlFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val normalizedUrl = UrlId.toScope(route.url)

    WatchLifecycleAndUpdateModel(feedViewModel)
    UrlFilterAssemblerSubscription(Route.Url(normalizedUrl), accountViewModel)

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            TopBarExtensibleWithBackButton(
                title = {
                    Text(
                        HttpUrlFormatter.displayUrl(normalizedUrl),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                popBack = nav::popBack,
            )
        },
        floatingButton = {
            FabBottomBarPadded(nav) {
                NewUrlPostButton(normalizedUrl, accountViewModel, nav)
            }
        },
        accountViewModel = accountViewModel,
    ) {
        RefresheableFeedView(
            feedViewModel,
            null,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}
