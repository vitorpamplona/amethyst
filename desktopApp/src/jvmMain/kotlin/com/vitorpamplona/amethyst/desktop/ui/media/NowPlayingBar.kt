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
package com.vitorpamplona.amethyst.desktop.ui.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.accessibility_download_for_offline
import com.vitorpamplona.amethyst.commons.resources.close
import com.vitorpamplona.amethyst.commons.resources.mute_button
import com.vitorpamplona.amethyst.commons.resources.muted_button
import com.vitorpamplona.amethyst.commons.resources.pause
import com.vitorpamplona.amethyst.commons.resources.play
import com.vitorpamplona.amethyst.commons.resources.video_player_settings_action_fullscreen
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.desktop.service.media.GlobalMediaPlayer
import com.vitorpamplona.amethyst.desktop.service.media.MediaPlaybackState
import com.vitorpamplona.amethyst.desktop.service.media.VideoThumbnailCache
import kotlinx.coroutines.launch

enum class MediaType { AUDIO, VIDEO }

@Composable
fun NowPlayingBar(modifier: Modifier = Modifier) {
    val videoState by GlobalMediaPlayer.videoState.collectAsState()
    val audioState by GlobalMediaPlayer.audioState.collectAsState()

    val hasVideo = videoState.url != null
    val hasAudio = audioState.url != null
    val visible = hasVideo || hasAudio

    // Show video bar if video is active, otherwise audio. Once nothing plays it keeps what it last
    // showed, so it slides out with that rather than empty.
    val lastShown = remember { arrayOfNulls<Pair<MediaPlaybackState, MediaType>>(1) }
    if (visible) lastShown[0] = if (hasVideo) videoState to MediaType.VIDEO else audioState to MediaType.AUDIO
    val (activeState, activeType) = lastShown[0] ?: (audioState to MediaType.AUDIO)
    val isVideo = activeType == MediaType.VIDEO

    val scope = rememberCoroutineScope()

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = modifier,
    ) {
        NowPlayingBarContent(
            state = activeState,
            type = activeType,
            // A cached frame, not a live VideoPlayerSurface: two surfaces drawing one player's
            // frame bitmap hit a use-after-free in kdroidFilter 0.10.0's macOS surface.
            thumbnail = activeState.url?.let { VideoThumbnailCache.getCached(it) },
            onPlayPause = { if (isVideo) GlobalMediaPlayer.toggleVideoPlayPause() else GlobalMediaPlayer.toggleAudioPlayPause() },
            onSeek = { if (isVideo) GlobalMediaPlayer.seekVideo(it) else GlobalMediaPlayer.seekAudio(it) },
            onToggleMute = { if (isVideo) GlobalMediaPlayer.toggleVideoMute() else GlobalMediaPlayer.toggleAudioMute() },
            onVolumeChange = { if (isVideo) GlobalMediaPlayer.setVideoVolume(it) else GlobalMediaPlayer.setAudioVolume(it) },
            onSave = { activeState.url?.let { url -> scope.launch { SaveMediaAction.saveMedia(url = url) } } },
            onFullscreen = GlobalMediaPlayer::toggleFullscreen,
            onStop = { if (isVideo) GlobalMediaPlayer.stopVideo() else GlobalMediaPlayer.stopAudio() },
        )
    }
}

/** The bar itself, driven only by its arguments so it renders without a player. */
@Composable
internal fun NowPlayingBarContent(
    state: MediaPlaybackState,
    type: MediaType,
    thumbnail: ImageBitmap?,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    onSave: () -> Unit,
    onFullscreen: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val secondary = colors.onSurfaceVariant

    Column(modifier.fillMaxWidth().background(colors.surfaceContainer)) {
        HorizontalDivider(color = colors.outlineVariant)
        BoxWithConstraints {
            // A narrow window keeps the seek bar usable by dropping the file name and volume slider.
            val roomy = maxWidth >= 760.dp

            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(type, thumbnail)

                val fileName = if (roomy) state.url?.let(::readableFileName) else null
                if (fileName != null) {
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 12.dp).widthIn(max = 160.dp),
                    )
                }

                Spacer(Modifier.width(12.dp))

                FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (state.isPlaying) MaterialSymbols.Pause else MaterialSymbols.PlayArrow,
                        contentDescription = stringRes(if (state.isPlaying) Res.string.pause else Res.string.play),
                        modifier = Modifier.size(22.dp),
                    )
                }

                Spacer(Modifier.width(12.dp))

                TimeLabel(formatTime(state.currentTime), secondary)
                // Seeking is costly, so a drag seeks once, where it is let go.
                ThinSlider(
                    value = state.position,
                    onValueChange = onSeek,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    color = colors.primary,
                    trackColor = colors.onSurface.copy(alpha = 0.15f),
                )
                TimeLabel(formatTime(state.duration), secondary)

                Spacer(Modifier.width(12.dp))

                BarButton(
                    if (state.isMuted) MaterialSymbols.AutoMirrored.VolumeOff else MaterialSymbols.AutoMirrored.VolumeUp,
                    stringRes(if (state.isMuted) Res.string.muted_button else Res.string.mute_button),
                    onToggleMute,
                )
                if (roomy) {
                    ThinSlider(
                        value = state.volume / 100f,
                        onValueChange = { onVolumeChange((it * 100).toInt()) },
                        modifier = Modifier.width(96.dp).padding(start = 8.dp, end = 8.dp),
                        changesWhileDragging = true,
                        color = secondary,
                        trackColor = colors.onSurface.copy(alpha = 0.15f),
                    )
                }

                BarButton(MaterialSymbols.SaveAlt, stringRes(Res.string.accessibility_download_for_offline), onSave)
                if (type == MediaType.VIDEO) {
                    BarButton(MaterialSymbols.Fullscreen, stringRes(Res.string.video_player_settings_action_fullscreen), onFullscreen)
                }
                BarButton(MaterialSymbols.Close, stringRes(Res.string.close), onStop)
            }
        }
    }
}

/** The video's poster, or a music note for audio, in a 40dp tile. */
@Composable
private fun Artwork(
    type: MediaType,
    thumbnail: ImageBitmap?,
) {
    val shape = RoundedCornerShape(6.dp)
    if (type == MediaType.VIDEO && thumbnail != null) {
        Image(
            bitmap = thumbnail,
            contentDescription = null,
            modifier = Modifier.size(width = 64.dp, height = 40.dp).clip(shape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            Modifier.size(40.dp).clip(shape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                MaterialSymbols.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun TimeLabel(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
        color = color,
        maxLines = 1,
    )
}

@Composable
private fun BarButton(
    symbol: MaterialSymbol,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(symbol, contentDescription = contentDescription, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

// Blossom names a file after the SHA-256 of its bytes, optionally with an extension.
private val HASH_FILE_NAME = Regex("^[0-9a-fA-F]{64}(?:\\.[^./]+)?$")

/** The URL's last path segment, or null when it is empty or just a content hash. */
private fun readableFileName(url: String): String? =
    url
        .substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('/')
        .takeIf { it.isNotBlank() && !HASH_FILE_NAME.matches(it) }
