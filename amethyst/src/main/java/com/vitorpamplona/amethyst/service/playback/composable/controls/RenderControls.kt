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
package com.vitorpamplona.amethyst.service.playback.composable.controls

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.playback.composable.BackgroundMedia
import com.vitorpamplona.amethyst.service.playback.composable.DEFAULT_MUTED_SETTING
import com.vitorpamplona.amethyst.service.playback.composable.MediaControllerState
import com.vitorpamplona.amethyst.service.playback.diskCache.isLiveStreaming
import com.vitorpamplona.amethyst.ui.actions.MediaSaverToDisk
import com.vitorpamplona.amethyst.ui.components.ShareImageAction
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.Size110dp
import com.vitorpamplona.amethyst.ui.theme.Size165dp
import com.vitorpamplona.amethyst.ui.theme.Size55dp

@Composable
fun RenderControls(
    videoUri: String,
    mimeType: String?,
    controllerState: MediaControllerState,
    nostrUriCallback: String?,
    controllerVisible: MutableState<Boolean>,
    buttonPositionModifier: Modifier,
    accountViewModel: AccountViewModel,
) {
    MuteButton(
        controllerVisible,
        (controllerState.controller.value?.volume ?: 0f) < 0.001,
        buttonPositionModifier,
    ) { mute: Boolean ->
        // makes the new setting the default for new creations.
        DEFAULT_MUTED_SETTING.value = mute

        // if the user unmutes a video and it's not the current playing, switches to that one.
        if (!mute && BackgroundMedia.hasBackgroundButNot(controllerState)) {
            BackgroundMedia.removeBackgroundControllerIfNotComposed()
        }

        controllerState.controller.value?.volume = if (mute) 0f else 1f
    }

    KeepPlayingButton(
        controllerState.keepPlaying,
        controllerVisible,
        buttonPositionModifier.padding(end = Size55dp),
    ) { newKeepPlaying: Boolean ->
        // If something else is playing and the user marks this video to keep playing, stops the other
        // one.
        if (newKeepPlaying) {
            BackgroundMedia.switchKeepPlaying(controllerState)
        } else {
            // if removed from background.
            if (BackgroundMedia.isMutex(controllerState)) {
                BackgroundMedia.removeBackgroundControllerIfNotComposed()
            }
        }

        controllerState.keepPlaying.value = newKeepPlaying
    }

    if (!isLiveStreaming(videoUri)) {
        AnimatedSaveButton(controllerVisible, buttonPositionModifier.padding(end = Size110dp)) { context ->
            saveMediaToGallery(videoUri, mimeType, context, accountViewModel)
        }

        AnimatedShareButton(controllerVisible, buttonPositionModifier.padding(end = Size165dp)) { popupExpanded, toggle ->
            ShareImageAction(accountViewModel = accountViewModel, popupExpanded, videoUri, nostrUriCallback, null, null, null, mimeType, toggle)
        }
    } else {
        AnimatedShareButton(controllerVisible, buttonPositionModifier.padding(end = Size110dp)) { popupExpanded, toggle ->
            ShareImageAction(accountViewModel = accountViewModel, popupExpanded, videoUri, nostrUriCallback, null, null, null, mimeType, toggle)
        }
    }
}

private fun saveMediaToGallery(
    videoUri: String?,
    mimeType: String?,
    localContext: Context,
    accountViewModel: AccountViewModel,
) {
    MediaSaverToDisk.saveDownloadingIfNeeded(
        videoUri = videoUri,
        forceProxy = accountViewModel.account.shouldUseTorForVideoDownload(),
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
