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
package com.vitorpamplona.amethyst.ui.components

import androidx.annotation.OptIn
import androidx.collection.LruCache
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import com.vitorpamplona.amethyst.commons.audio.PlayableLayout
import com.vitorpamplona.amethyst.commons.audio.mediaFormatLabel
import com.vitorpamplona.amethyst.commons.audio.player.AudioCardInfo
import com.vitorpamplona.amethyst.commons.audio.player.AudioPlaybackUi
import com.vitorpamplona.amethyst.commons.audio.player.PlayableMediaCard
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.audio_card_untitled
import com.vitorpamplona.amethyst.commons.resources.audio_card_untitled_no_format
import com.vitorpamplona.amethyst.commons.resources.playable_media_untitled
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.service.playback.composable.GetVideoController
import com.vitorpamplona.amethyst.service.playback.composable.MediaControllerState
import com.vitorpamplona.amethyst.service.playback.composable.PauseControllerWhenInBackground
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.GetMediaItem
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.LoadedMediaItem
import com.vitorpamplona.amethyst.ui.note.types.RenderTopButtonsForVoice
import kotlinx.coroutines.delay

/**
 * What the player found when it probed a URL: true when every track is audio, false when there is a
 * video track. Remembered per URL so a post scrolled back into view takes its probed shape at once
 * instead of starting from its declaration and reflowing again.
 */
object PlayableMediaProbeCache {
    private val cache = LruCache<String, Boolean>(500)

    fun get(url: String): Boolean? = cache[url]

    fun put(
        url: String,
        audioOnly: Boolean,
    ) {
        cache.put(url, audioOnly)
    }
}

/**
 * A playable file inside a post that is (or may be) audio: the shared [PlayableMediaCard] for [layout],
 * driven by the same pooled player as every other inline media.
 *
 * Unlike a feed video it never autoplays — audio playing muted is pointless and playing it aloud
 * unasked is worse — and it does not loop. With automatic media loading off (data saver), nothing is
 * fetched until play is pressed. Once the player knows the tracks, [onProbed] reports whether they are
 * all audio, which is how an undecided file finds its real card and how a mislabelled video leaves for
 * the video player.
 */
@Composable
fun PlayableAudioView(
    content: MediaUrlVideo,
    layout: PlayableLayout,
    onProbed: (audioOnly: Boolean) -> Unit,
    accountViewModel: AccountViewModel,
) {
    val authorName =
        remember(content.authorName, content.authorPubKey) {
            content.authorName ?: content.authorPubKey?.let { accountViewModel.getUserIfExists(it)?.toBestDisplayName() }
        }

    val info =
        remember(content, authorName) {
            AudioCardInfo(
                title = null,
                artist = authorName,
                format = mediaFormatLabel(content.mimeType, content.url),
                sizeBytes = content.sizeBytes,
                seed = content.hash ?: content.url,
                waveform = content.waveform,
                artworkUrl = content.artworkUri,
            )
        }

    // The name the system media notification shows.
    val mediaTitle =
        when {
            layout == PlayableLayout.UNDECIDED -> stringRes(Res.string.playable_media_untitled)
            info.format != null -> stringRes(Res.string.audio_card_untitled, info.format)
            else -> stringRes(Res.string.audio_card_untitled_no_format)
        }

    var load by remember(content.url) { mutableStateOf(accountViewModel.settings.startVideoPlayback()) }
    var playWhenConnected by remember(content.url) { mutableStateOf(false) }
    var active by remember(content.url) { mutableStateOf<Pair<MediaControllerState, LoadedMediaItem>?>(null) }
    val controllerVisible = remember(content.url) { mutableStateOf(false) }

    if (load) {
        val proxyPort = remember(content.url) { accountViewModel.httpClientBuilder.proxyPortForVideo(content.url) }
        GetMediaItem(
            videoUri = content.url,
            title = mediaTitle,
            artworkUri = content.artworkUri,
            authorName = authorName,
            callbackUri = content.uri,
            mimeType = content.mimeType,
            proxyPort = proxyPort,
            keepPlaying = false,
            isLiveStream = content.isLiveStream,
            hash = content.hash,
        ) { mediaItem ->
            GetVideoController(mediaItem = mediaItem, muted = false) { controller ->
                PauseControllerWhenInBackground(controller)
                ReportProbe(controller.controller, onProbed)

                DisposableEffect(controller, mediaItem) {
                    active = controller to mediaItem
                    onDispose {
                        if (active?.first === controller) active = null
                    }
                }

                // Play was pressed before the controller connected.
                LaunchedEffect(controller) {
                    if (playWhenConnected) {
                        playWhenConnected = false
                        controller.controller.play()
                    }
                }
            }
        }
    }

    Box {
        val current = active
        if (current != null) {
            val (controller, mediaItem) = current
            val playback by rememberAudioPlaybackUi(controller.controller)
            PlayableMediaCard(
                layout = layout,
                info = info,
                playback = playback,
                onPlayPause = { playOrPause(controller.controller) },
                onSeek = { fraction -> playback.durationMs?.let { controller.controller.seekTo((it * fraction).toLong()) } },
                onClick = { controllerVisible.value = !controllerVisible.value },
                modifier =
                    Modifier.onGloballyPositioned { coordinates ->
                        // Where picture-in-picture animates from.
                        val bounds = coordinates.boundsInWindow()
                        controller.visibility.setBounds(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt())
                    },
                overlay = {
                    RenderTopButtonsForVoice(
                        mediaData = mediaItem.src,
                        controllerState = controller,
                        controllerVisible = controllerVisible,
                        modifier = Modifier.align(Alignment.TopEnd),
                        accountViewModel = accountViewModel,
                    )
                },
            )
        } else {
            PlayableMediaCard(
                layout = layout,
                info = info,
                playback = AudioPlaybackUi.Idle,
                onPlayPause = {
                    playWhenConnected = true
                    load = true
                },
                onSeek = {},
            )
        }
    }
}

@OptIn(UnstableApi::class)
private fun playOrPause(player: Player) {
    // Handles the idle (re-prepare) and ended (restart) states, not just play/pause.
    Util.handlePlayPauseButtonAction(player)
}

/** True when the tracks are all audio, false when one is video, null while the player hasn't found any. */
internal fun Tracks.audioOnlyOrNull(): Boolean? {
    if (groups.isEmpty()) return null
    if (groups.any { it.type == C.TRACK_TYPE_VIDEO }) return false
    return if (groups.any { it.type == C.TRACK_TYPE_AUDIO }) true else null
}

@Composable
private fun ReportProbe(
    player: Player,
    onProbed: (Boolean) -> Unit,
) {
    val currentOnProbed by rememberUpdatedState(onProbed)
    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    tracks.audioOnlyOrNull()?.let { currentOnProbed(it) }
                }
            }
        player.addListener(listener)
        player.currentTracks.audioOnlyOrNull()?.let { currentOnProbed(it) }
        onDispose { player.removeListener(listener) }
    }
}

/** The player's state as the card draws it: event-driven, plus a position poll while it plays. */
@Composable
private fun rememberAudioPlaybackUi(player: Player): State<AudioPlaybackUi> {
    val state = remember(player) { mutableStateOf(player.toPlaybackUi()) }
    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onEvents(
                    player: Player,
                    events: Player.Events,
                ) {
                    state.value = player.toPlaybackUi()
                }
            }
        player.addListener(listener)
        state.value = player.toPlaybackUi()
        onDispose { player.removeListener(listener) }
    }

    val isPlaying = state.value.isPlaying
    LaunchedEffect(player, isPlaying) {
        while (isPlaying) {
            delay(POSITION_POLL_MS)
            state.value = player.toPlaybackUi()
        }
    }
    return state
}

@OptIn(UnstableApi::class)
private fun Player.toPlaybackUi() =
    AudioPlaybackUi(
        // Matches what the button should offer: pause while playing or about to (buffering with
        // playWhenReady), play when paused, ended or idle.
        isPlaying = !Util.shouldShowPlayButton(this),
        positionMs = currentPosition.coerceAtLeast(0),
        durationMs = duration.takeIf { it != C.TIME_UNSET && it > 0 },
    )

private const val POSITION_POLL_MS = 250L
