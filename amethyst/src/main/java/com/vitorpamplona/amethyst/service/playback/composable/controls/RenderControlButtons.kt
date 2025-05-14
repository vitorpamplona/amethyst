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
package com.vitorpamplona.amethyst.service.playback.composable.controls

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.playback.composable.DEFAULT_MUTED_SETTING
import com.vitorpamplona.amethyst.service.playback.composable.MediaControllerState
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.MediaItemData
import com.vitorpamplona.amethyst.service.playback.diskCache.isLiveStreaming
import com.vitorpamplona.amethyst.service.playback.pip.PipVideoActivity
import com.vitorpamplona.amethyst.ui.actions.MediaSaverToDisk
import com.vitorpamplona.amethyst.ui.components.ShareImageAction
import com.vitorpamplona.amethyst.ui.components.getActivity
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.Size110dp
import com.vitorpamplona.amethyst.ui.theme.Size165dp
import com.vitorpamplona.amethyst.ui.theme.Size55dp

@Composable
fun RenderControlButtons(
    mediaData: MediaItemData,
    controllerState: MediaControllerState,
    controllerVisible: MutableState<Boolean>,
    buttonPositionModifier: Modifier,
    accountViewModel: AccountViewModel,
) {
    MuteButton(
        controllerVisible,
        (controllerState.controller?.volume ?: 0f) < 0.001,
        buttonPositionModifier,
    ) { mute: Boolean ->
        // makes the new setting the default for new creations.
        DEFAULT_MUTED_SETTING.value = mute

        controllerState.controller?.volume = if (mute) 0f else 1f
    }

    val context = LocalContext.current.getActivity()

    PictureInPictureButton(
        controllerVisible,
        buttonPositionModifier.padding(end = Size55dp),
    ) {
        PipVideoActivity.callIn(mediaData, controllerState.visibility.bounds, context)
    }

    if (!isLiveStreaming(mediaData.videoUri)) {
        AnimatedSaveButton(controllerVisible, buttonPositionModifier.padding(end = Size110dp)) { context ->
            saveMediaToGalleryInner(mediaData.videoUri, mediaData.mimeType, context, accountViewModel)
        }

        AnimatedShareButton(controllerVisible, buttonPositionModifier.padding(end = Size165dp)) { popupExpanded, toggle ->
            ShareImageAction(accountViewModel = accountViewModel, popupExpanded, mediaData.videoUri, mediaData.callbackUri, null, null, null, mediaData.mimeType, toggle)
        }
    } else {
        AnimatedShareButton(controllerVisible, buttonPositionModifier.padding(end = Size110dp)) { popupExpanded, toggle ->
            ShareImageAction(accountViewModel = accountViewModel, popupExpanded, mediaData.videoUri, mediaData.callbackUri, null, null, null, mediaData.mimeType, toggle)
        }
    }
}

private fun saveMediaToGalleryInner(
    videoUri: String?,
    mimeType: String?,
    localContext: Context,
    accountViewModel: AccountViewModel,
) {
    MediaSaverToDisk.saveDownloadingIfNeeded(
        videoUri = videoUri,
        okHttpClient = accountViewModel::okHttpClientForVideo,
        mimeType = mimeType,
        localContext = localContext,
        onSuccess = {
            accountViewModel.toastManager.toast(R.string.video_saved_to_the_gallery, R.string.video_saved_to_the_gallery)
        },
        onError = {
            accountViewModel.toastManager.toast(R.string.failed_to_save_the_video, null, it)
        },
    )
}
