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
package com.vitorpamplona.amethyst.service.playback.composable.controls

import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.service.playback.composable.DEFAULT_MUTED_SETTING
import com.vitorpamplona.amethyst.service.playback.composable.MediaControllerState
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.MediaItemData
import com.vitorpamplona.amethyst.service.playback.diskCache.isLiveStreaming
import com.vitorpamplona.amethyst.service.playback.pip.PipVideoActivity
import com.vitorpamplona.amethyst.ui.components.ShareMediaAction
import com.vitorpamplona.amethyst.ui.components.getActivity
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn

@Preview
@Composable
fun RenderTopButtonsPreview() {
    ThemeComparisonColumn {
        Box(Modifier.background(BitcoinOrange)) {
            RenderTopButtons(
                mediaData = MediaItemData("http://test.mp4"),
                controllerVisible = remember { mutableStateOf(true) },
                startingMuteState = false,
                isLive = false,
                pipSupported = true,
                onMuteClick = {},
                onPictureInPictureClick = {},
                onZoomClick = {},
                modifier = Modifier,
                accountViewModel = mockAccountViewModel(),
            )
        }
    }
}

@Composable
fun RenderTopButtons(
    mediaData: MediaItemData,
    controllerState: MediaControllerState,
    controllerVisible: MutableState<Boolean>,
    onZoomClick: (() -> Unit)?,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
) {
    val context = LocalContext.current
    val isLive = isLiveStreaming(mediaData.videoUri)
    val pipSupported =
        remember {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        }

    RenderTopButtons(
        mediaData = mediaData,
        controllerVisible = controllerVisible,
        startingMuteState = controllerState.controller.volume < 0.001,
        isLive = isLive,
        pipSupported = pipSupported,
        onMuteClick = { mute ->
            DEFAULT_MUTED_SETTING.value = mute
            controllerState.controller.volume = if (mute) 0f else 1f
        },
        onPictureInPictureClick = {
            controllerState.controller.pause()
            PipVideoActivity.callIn(mediaData, controllerState.visibility.bounds, context.getActivity())
        },
        onZoomClick =
            onZoomClick?.let {
                {
                    controllerState.controller.pause()
                    it()
                }
            },
        modifier = modifier,
        accountViewModel = accountViewModel,
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RenderTopButtons(
    mediaData: MediaItemData,
    controllerVisible: MutableState<Boolean>,
    startingMuteState: Boolean,
    isLive: Boolean,
    pipSupported: Boolean,
    onMuteClick: (Boolean) -> Unit,
    onPictureInPictureClick: () -> Unit,
    onZoomClick: (() -> Unit)?,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
) {
    val shareDialogVisible = remember { mutableStateOf(false) }
    val saveAction =
        rememberSaveMediaAction { context ->
            accountViewModel.saveMediaToGallery(mediaData.videoUri, mediaData.mimeType, context)
        }

    LaunchedEffect(controllerVisible.value) {
        if (!controllerVisible.value) shareDialogVisible.value = false
    }

    Row(modifier) {
        if (onZoomClick != null) {
            FullScreenButton(
                controllerVisible = controllerVisible,
                onClick = onZoomClick,
            )
        }

        MuteButton(
            controllerVisible = controllerVisible,
            startingMuteState = startingMuteState,
            toggle = onMuteClick,
        )

        Box {
            AnimatedOverflowMenuButton(
                controllerVisible = controllerVisible,
                showShare = true,
                showSave = !isLive,
                showPip = pipSupported,
                onShareClick = { shareDialogVisible.value = true },
                onSaveClick = saveAction,
                onPipClick = onPictureInPictureClick,
            )

            if (shareDialogVisible.value) {
                ShareMediaAction(
                    popupExpanded = shareDialogVisible,
                    videoUri = mediaData.videoUri,
                    postNostrUri = mediaData.callbackUri,
                    blurhash = null,
                    dim = null,
                    hash = null,
                    mimeType = mediaData.mimeType,
                    onDismiss = { shareDialogVisible.value = false },
                    content =
                        MediaUrlVideo(
                            url = mediaData.videoUri,
                            mimeType = mediaData.mimeType,
                            artworkUri = mediaData.artworkUri,
                            authorName = mediaData.authorName,
                            description = mediaData.title,
                            uri = mediaData.callbackUri,
                        ),
                    accountViewModel = accountViewModel,
                )
            }
        }
    }
}
