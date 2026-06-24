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
package com.vitorpamplona.amethyst.ui.navigation.topbars

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserPicture
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.ui.note.SearchIcon
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.HeaderPictureModifier
import com.vitorpamplona.amethyst.ui.theme.Size22Modifier
import com.vitorpamplona.amethyst.ui.theme.placeholderText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDrawerSearchTopBar(
    accountViewModel: AccountViewModel,
    nav: INav,
    content: @Composable () -> Unit,
) {
    ShorterTopAppBar(
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                content()
            }
        },
        navigationIcon = {
            // When this screen sits on top of a back stack (entered via the drawer
            // or any deep link), show a back arrow. When it's the root (entered via
            // the bottom nav, which clears the stack), show the drawer opener.
            if (nav.canPop()) {
                IconButton(onClick = nav::popBack) {
                    ArrowBackIcon()
                }
            } else {
                LoggedInUserPictureDrawer(accountViewModel, nav::openDrawer)
            }
        },
        actions = {
            ReadFeedAloudButton(accountViewModel)
            IconButton(onClick = { nav.nav(Route.Search) }) {
                SearchIcon(modifier = Size22Modifier, MaterialTheme.colorScheme.placeholderText)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadFeedAloudButton(accountViewModel: AccountViewModel) {
    val ui = accountViewModel.settings.uiSettingsFlow
    val showButton by ui.showReadFeedAloudButton.collectAsStateWithLifecycle()

    if (!showButton) return
    if (!accountViewModel.readAloud.hasReadableFeed) return

    val context = LocalContext.current

    val isPlaying = accountViewModel.readAloud.isPlaying

    // Show a one-time tooltip the first time the button appears, so people discover the feature.
    val tooltipState = rememberTooltipState(isPersistent = true)
    val hintShown by ui.readFeedAloudHintShown.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        if (!hintShown) {
            ui.readFeedAloudHintShown.tryEmit(true)
            tooltipState.show()
        }
    }

    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(stringRes(R.string.read_feed_aloud_hint)) } },
        state = tooltipState,
    ) {
        IconButton(
            onClick = { accountViewModel.readAloud.toggle(context) },
        ) {
            Icon(
                symbol = if (isPlaying) MaterialSymbols.Stop else MaterialSymbols.PlayCircle,
                contentDescription =
                    stringRes(
                        if (isPlaying) R.string.read_feed_aloud_stop else R.string.read_feed_aloud_start,
                    ),
                modifier = Size22Modifier,
                tint = MaterialTheme.colorScheme.placeholderText,
            )
        }
    }
}

@Composable
internal fun LoggedInUserPictureDrawer(
    accountViewModel: AccountViewModel,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        val profilePicture by observeUserPicture(accountViewModel.userProfile(), accountViewModel)

        RobohashFallbackAsyncImage(
            robot = accountViewModel.userProfile().pubkeyHex,
            model = profilePicture,
            contentDescription = stringRes(id = R.string.your_profile_image),
            modifier = HeaderPictureModifier,
            contentScale = ContentScale.Crop,
            loadProfilePicture = accountViewModel.settings.showProfilePictures(),
            loadRobohash = accountViewModel.settings.isNotPerformanceMode(),
            autoPlayGif =
                accountViewModel.settings.autoPlayVideosFlow
                    .collectAsStateWithLifecycle()
                    .value,
        )
    }
}
