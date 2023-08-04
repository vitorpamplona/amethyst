package com.vitorpamplona.amethyst.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vitorpamplona.amethyst.model.ConnectivityType
import com.vitorpamplona.amethyst.model.UrlCachedPreviewer
import com.vitorpamplona.amethyst.service.connectivitystatus.ConnectivityStatus
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun UrlPreview(url: String, urlText: String, accountViewModel: AccountViewModel) {
    val automaticallyShowUrlPreview = remember {
        when (accountViewModel.account.settings.automaticallyShowUrlPreview) {
            ConnectivityType.WIFI_ONLY -> !ConnectivityStatus.isOnMobileData.value
            ConnectivityType.NEVER -> false
            ConnectivityType.ALWAYS -> true
        }
    }

    if (!automaticallyShowUrlPreview) {
        ClickableUrl(urlText, url)
    } else {
        var urlPreviewState by remember(url) {
            mutableStateOf(
                UrlCachedPreviewer.cache.get(url) ?: UrlPreviewState.Loading
            )
        }

        // Doesn't use a viewModel because of viewModel reusing issues (too many UrlPreview are created).
        if (urlPreviewState == UrlPreviewState.Loading) {
            LaunchedEffect(url) {
                launch(Dispatchers.IO) {
                    UrlCachedPreviewer.previewInfo(url) {
                        urlPreviewState = it
                    }
                }
            }
        }

        Crossfade(
            targetState = urlPreviewState,
            animationSpec = tween(durationMillis = 100)
        ) { state ->
            when (state) {
                is UrlPreviewState.Loaded -> {
                    UrlPreviewCard(url, state.previewInfo, accountViewModel)
                }

                else -> {
                    ClickableUrl(urlText, url)
                }
            }
        }
    }
}
