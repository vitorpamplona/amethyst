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
package com.vitorpamplona.amethyst.ui.note.types

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.playback.composable.GetVideoController
import com.vitorpamplona.amethyst.service.playback.composable.MediaControllerState
import com.vitorpamplona.amethyst.service.playback.composable.WaveformData
import com.vitorpamplona.amethyst.service.playback.composable.controls.RenderControlButtons
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.GetMediaItem
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.LoadedMediaItem
import com.vitorpamplona.amethyst.service.playback.composable.wavefront.Waveform
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.elements.DisplayUncitedHashtags
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.MaxWidthPaddingTop5dp
import com.vitorpamplona.amethyst.ui.theme.Size50Modifier
import com.vitorpamplona.amethyst.ui.theme.Size75Modifier
import com.vitorpamplona.amethyst.ui.theme.VoiceHeightModifier
import com.vitorpamplona.amethyst.ui.theme.imageModifier
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hasHashtags
import com.vitorpamplona.quartz.nip14Subject.subject
import com.vitorpamplona.quartz.nip92IMeta.imetas
import com.vitorpamplona.quartz.nipA0VoiceMessages.AudioMeta
import com.vitorpamplona.quartz.nipA0VoiceMessages.BaseVoiceEvent

@Composable
fun RenderVoiceTrack(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? BaseVoiceEvent ?: return

    VoiceHeader(noteEvent, note, accountViewModel, nav)
}

@Composable
fun VoiceHeader(
    noteEvent: BaseVoiceEvent,
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val media =
        remember(noteEvent) {
            noteEvent.content.ifBlank { null } ?: noteEvent.iMetaTags().firstOrNull()?.url
        }

    if (media == null) return

    val waveform =
        remember(noteEvent) {
            noteEvent
                .iMetaTags()
                .firstOrNull()
                ?.waveform
                ?.let { WaveformData(it) }
        }

    RenderAudioWithWaveform(
        mediaUrl = media,
        title = noteEvent.subject(),
        mimeType = null,
        waveform = waveform,
        note = note,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

/**
 * Shared composable for rendering audio with waveform visualization.
 * Used by both VoiceHeader (for BaseVoiceEvent) and RenderAudioFromIMeta (for other events with audio IMeta).
 */
@Composable
fun RenderAudioWithWaveform(
    mediaUrl: String,
    title: String?,
    mimeType: String?,
    waveform: WaveformData?,
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event ?: return
    val callbackUri = remember(note) { note.toNostrUri() }

    Column(modifier = MaxWidthPaddingTop5dp, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GetMediaItem(
                videoUri = mediaUrl,
                title = title,
                artworkUri = null,
                authorName = note.author?.toBestDisplayName(),
                callbackUri = callbackUri,
                mimeType = mimeType,
                aspectRatio = null,
                proxyPort = accountViewModel.httpClientBuilder.proxyPortForVideo(mediaUrl),
                keepPlaying = false,
                waveformData = waveform,
            ) { mediaItem ->
                GetVideoController(
                    mediaItem = mediaItem,
                    muted = false,
                ) { controller ->
                    RenderVoicePlayer(
                        mediaItem = mediaItem,
                        controllerState = controller,
                        waveform = waveform,
                        borderModifier = MaterialTheme.colorScheme.imageModifier,
                        accountViewModel = accountViewModel,
                    )
                }
            }
        }

        if (noteEvent.hasHashtags()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DisplayUncitedHashtags(noteEvent, callbackUri, accountViewModel, nav)
            }
        }
    }
}

@Composable
@OptIn(UnstableApi::class)
fun RenderVoicePlayer(
    mediaItem: LoadedMediaItem,
    controllerState: MediaControllerState,
    waveform: WaveformData? = null,
    borderModifier: Modifier,
    accountViewModel: AccountViewModel,
) {
    val controllerVisible = remember(controllerState) { mutableStateOf(false) }

    Box(modifier = borderModifier) {
        AndroidView(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(100.dp),
            factory = { context: Context ->
                PlayerView(context).apply {
                    player = controllerState.controller
                    // if we already know the size of the frame, this forces the player to stay in the size
                    layoutParams =
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        )

                    setBackgroundColor(Color.Transparent.toArgb())
                    setShutterBackgroundColor(Color.Transparent.toArgb())

                    controllerAutoShow = false
                    useController = true
                    hideController()

                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visible ->
                            controllerVisible.value = visible == View.VISIBLE
                        },
                    )
                }
            },
        )

        Row(VoiceHeightModifier, verticalAlignment = Alignment.CenterVertically) {
            PlayPauseButton(controllerState)
            waveform?.let { Waveform(it, controllerState, Modifier) }
        }

        RenderControlButtons(
            mediaItem.src,
            controllerState,
            controllerVisible,
            Modifier.align(Alignment.TopEnd),
            accountViewModel,
        )
    }
}

@Composable
fun PlayPauseButton(controllerState: MediaControllerState) {
    val controller = controllerState.controller
    var isPlaying by remember(controller) {
        mutableStateOf(controllerState.isPlaying())
    }

    if (controller != null) {
        val view = LocalView.current
        // Keeps the screen on while playing and viewing videos.
        DisposableEffect(key1 = controller, key2 = view) {
            val listener =
                object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        // doesn't consider the mutex because the screen can turn off if the video
                        // being played in the mutex is not visible.
                        if (view.keepScreenOn != playing) {
                            view.keepScreenOn = playing
                        }
                        isPlaying = playing
                    }

                    fun destroy() {
                        if (view.keepScreenOn) {
                            view.keepScreenOn = false
                        }
                        isPlaying = false
                    }
                }

            controller.addListener(listener)
            onDispose {
                controller.removeListener(listener)
                listener.destroy()
            }
        }

        // Play/Pause Button
        IconButton(
            onClick = {
                if (controller.isPlaying) {
                    controller.pause()
                } else {
                    controller.play()
                }
            },
            modifier = Size75Modifier,
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.PauseCircleOutline else Icons.Default.PlayCircleOutline,
                contentDescription = if (isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                modifier = Size50Modifier,
                tint = Color.White,
            )
        }
    }
}

/**
 * Extracts AudioMeta from an event's IMeta tags if it has audio content with waveform.
 * Returns the first audio IMeta that has a waveform, or null if none found.
 */
fun Event.getAudioMetaWithWaveform(): AudioMeta? {
    val audioMetas = imetas().map { AudioMeta.parse(it) }
    return audioMetas.firstOrNull { meta ->
        meta.waveform != null &&
            (meta.mimeType == null || meta.mimeType?.startsWith("audio/") == true)
    }
}

/**
 * Checks if the event content is primarily an audio attachment (content is just the audio URL).
 */
fun Event.isAudioOnlyContent(): Boolean {
    val audioMeta = getAudioMetaWithWaveform() ?: return false
    return content.trim() == audioMeta.url
}

/**
 * Renders audio with waveform for any event type that has audio IMeta attachment.
 * This allows KIND 1 (TextNoteEvent) and KIND 1111 (CommentEvent) with voice
 * attachments to display the same waveform UI as VoiceEvent.
 */
@Composable
fun RenderAudioFromIMeta(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event ?: return

    val audioMeta = remember(noteEvent) { noteEvent.getAudioMetaWithWaveform() } ?: return

    val waveform =
        remember(audioMeta) {
            audioMeta.waveform?.let { WaveformData(it) }
        }

    RenderAudioWithWaveform(
        mediaUrl = audioMeta.url,
        title = null,
        mimeType = audioMeta.mimeType,
        waveform = waveform,
        note = note,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}
