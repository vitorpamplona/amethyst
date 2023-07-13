package com.vitorpamplona.amethyst.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vitorpamplona.amethyst.model.UrlCachedPreviewer
import com.vitorpamplona.amethyst.service.connectivitystatus.ConnectivityStatus
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun UrlPreview(url: String, urlText: String, accountViewModel: AccountViewModel) {
    val settings = accountViewModel.account.settings
    val isMobile = ConnectivityStatus.isOnMobileData.value

    val automaticallyShowUrlPreview = when (settings.automaticallyShowUrlPreview) {
        true -> !isMobile
        false -> false
        else -> true
    }

    if (!automaticallyShowUrlPreview) {
        ClickableUrl(urlText, url)
    } else {
        var urlPreviewState by remember(url) {
            mutableStateOf(
                UrlCachedPreviewer.cache.get(url)?.let { it } ?: UrlPreviewState.Loading
            )
        }

        // Doesn't use a viewModel because of viewModel reusing issues (too many UrlPreview are created).
        if (urlPreviewState == UrlPreviewState.Loading) {
            LaunchedEffect(url) {
                launch(Dispatchers.IO) {
                    UrlCachedPreviewer.previewInfo(url) {
                        launch(Dispatchers.Main) {
                            urlPreviewState = it
                        }
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
