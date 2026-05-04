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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.model.VideoButtonLocation
import com.vitorpamplona.amethyst.model.VideoPlayerAction
import com.vitorpamplona.amethyst.service.playback.composable.DEFAULT_MUTED_SETTING
import com.vitorpamplona.amethyst.service.playback.composable.MediaControllerState
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.MediaItemData
import com.vitorpamplona.amethyst.service.playback.diskCache.isLiveStreaming
import com.vitorpamplona.amethyst.service.playback.pip.PipVideoActivity
import com.vitorpamplona.amethyst.ui.components.ShareMediaAction
import com.vitorpamplona.amethyst.ui.components.getActivity
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.ui.theme.PinBottomIconSize
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.Size50Modifier
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import kotlinx.collections.immutable.toImmutableList

@Preview
@Composable
fun RenderTopButtonsPreview() {
    ThemeComparisonColumn {
        Box(Modifier.background(BitcoinOrange)) {
            RenderTopButtons(
                mediaData = MediaItemData("http://test.mp4"),
                hasMultipleQualities = false,
                qualityButton = {},
                controllerVisible = remember { mutableStateOf(true) },
                startingMuteState = false,
                isLive = false,
                pipSupported = true,
                onMuteClick = {},
                onPictureInPictureClick = {},
                onZoomClick = {},
                onOverflowQualityClick = {},
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
    val isLive = remember(mediaData.videoUri) { isLiveStreaming(mediaData.videoUri) }
    val pipSupported =
        remember {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        }

    val player = controllerState.controller
    var tracks by remember(player) { mutableStateOf(player.currentTracks) }
    var isMuted by remember(player) { mutableStateOf(player.volume < 0.001) }
    DisposableEffect(player) {
        tracks = player.currentTracks
        isMuted = player.volume < 0.001
        val listener =
            object : Player.Listener {
                override fun onTracksChanged(newTracks: Tracks) {
                    tracks = newTracks
                }

                override fun onVolumeChanged(volume: Float) {
                    isMuted = volume < 0.001
                }
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    val videoGroup = getVideoTrackGroup(tracks)
    val hasMultipleQualities = videoGroup != null && videoGroup.length > 1

    val overflowQualityOpen = remember { mutableStateOf(false) }

    RenderTopButtons(
        mediaData = mediaData,
        hasMultipleQualities = hasMultipleQualities,
        qualityButton = {
            VideoQualityButton(
                player = player,
                controllerVisible = controllerVisible,
            )
        },
        controllerVisible = controllerVisible,
        startingMuteState = isMuted,
        isLive = isLive,
        pipSupported = pipSupported,
        onMuteClick = { mute ->
            DEFAULT_MUTED_SETTING.value = mute
            player.volume = if (mute) 0f else 1f
        },
        onPictureInPictureClick = {
            player.pause()
            PipVideoActivity.callIn(mediaData, controllerState.visibility.bounds, context.getActivity())
        },
        onZoomClick =
            onZoomClick?.let {
                {
                    player.pause()
                    it()
                }
            },
        onOverflowQualityClick = { overflowQualityOpen.value = true },
        modifier = modifier,
        accountViewModel = accountViewModel,
    )

    if (overflowQualityOpen.value && videoGroup != null) {
        VideoQualityPopup(
            player = player,
            videoGroup = videoGroup,
            onDismiss = { overflowQualityOpen.value = false },
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RenderTopButtons(
    mediaData: MediaItemData,
    hasMultipleQualities: Boolean,
    qualityButton: @Composable () -> Unit,
    controllerVisible: MutableState<Boolean>,
    startingMuteState: Boolean,
    isLive: Boolean,
    pipSupported: Boolean,
    onMuteClick: (Boolean) -> Unit,
    onPictureInPictureClick: () -> Unit,
    onZoomClick: (() -> Unit)?,
    onOverflowQualityClick: () -> Unit,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
) {
    val buttonItems by accountViewModel.videoPlayerButtonItemsFlow().collectAsStateWithLifecycle()
    val shareDialogVisible = remember { mutableStateOf(false) }
    val saveAction =
        rememberSaveMediaAction { context ->
            accountViewModel.saveMediaToGallery(mediaData.videoUri, mediaData.mimeType, context)
        }

    fun isAvailable(action: VideoPlayerAction): Boolean =
        when (action) {
            VideoPlayerAction.Fullscreen -> onZoomClick != null
            VideoPlayerAction.Mute -> true
            VideoPlayerAction.Quality -> hasMultipleQualities
            VideoPlayerAction.Share -> true
            VideoPlayerAction.Download -> !isLive
            VideoPlayerAction.PictureInPicture -> pipSupported
        }

    val canFullscreen = onZoomClick != null
    // ImmutableList so Compose can treat the action lists as stable parameters when they're
    // passed through to AnimatedOverflowMenuButton — a plain List is unstable and forces the
    // overflow tree to recompose whenever any unrelated parent state ticks.
    val topBarActions =
        remember(buttonItems, canFullscreen, hasMultipleQualities, isLive, pipSupported) {
            buttonItems
                .filter { it.location == VideoButtonLocation.TopBar && isAvailable(it.action) }
                .map { it.action }
                .toImmutableList()
        }
    val overflowActions =
        remember(buttonItems, canFullscreen, hasMultipleQualities, isLive, pipSupported) {
            buttonItems
                .filter { it.location == VideoButtonLocation.OverflowMenu && isAvailable(it.action) }
                .map { it.action }
                .toImmutableList()
        }

    Row(modifier) {
        topBarActions.forEach { action ->
            when (action) {
                VideoPlayerAction.Fullscreen -> {
                    onZoomClick?.let {
                        FullScreenButton(
                            controllerVisible = controllerVisible,
                            onClick = it,
                        )
                    }
                }

                VideoPlayerAction.Mute -> {
                    MuteButton(
                        controllerVisible = controllerVisible,
                        startingMuteState = startingMuteState,
                        toggle = onMuteClick,
                    )
                }

                VideoPlayerAction.Quality -> {
                    qualityButton()
                }

                VideoPlayerAction.Share -> {
                    AnimatedTopBarIconButton(
                        controllerVisible = controllerVisible,
                        symbol = MaterialSymbols.Share,
                        contentDescription = stringRes(R.string.share_or_save),
                        onClick = { shareDialogVisible.value = true },
                    )
                }

                VideoPlayerAction.Download -> {
                    AnimatedTopBarIconButton(
                        controllerVisible = controllerVisible,
                        symbol = MaterialSymbols.SaveAlt,
                        contentDescription = stringRes(R.string.download_to_phone),
                        onClick = saveAction,
                    )
                }

                VideoPlayerAction.PictureInPicture -> {
                    AnimatedTopBarIconButton(
                        controllerVisible = controllerVisible,
                        symbol = MaterialSymbols.PictureInPicture,
                        contentDescription = stringRes(R.string.picture_in_picture),
                        onClick = onPictureInPictureClick,
                    )
                }
            }
        }

        if (overflowActions.isNotEmpty()) {
            AnimatedOverflowMenuButton(
                controllerVisible = controllerVisible,
                actions = overflowActions,
                onFullscreenClick = onZoomClick,
                onMuteClick = { onMuteClick(!startingMuteState) },
                startingMuteState = startingMuteState,
                onQualityClick = onOverflowQualityClick,
                onShareClick = { shareDialogVisible.value = true },
                onSaveClick = saveAction,
                onPipClick = onPictureInPictureClick,
            )
        }

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

@Composable
internal fun AnimatedTopBarIconButton(
    controllerVisible: State<Boolean>,
    symbol: MaterialSymbol,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = controllerVisible.value,
        modifier = modifier,
        enter = remember { fadeIn() },
        exit = remember { fadeOut() },
    ) {
        Box(modifier = PinBottomIconSize) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .fillMaxSize(0.7f)
                    .align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.background),
            )

            IconButton(
                onClick = onClick,
                modifier = Size50Modifier,
            ) {
                Icon(
                    symbol = symbol,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Size20Modifier,
                )
            }
        }
    }
}
