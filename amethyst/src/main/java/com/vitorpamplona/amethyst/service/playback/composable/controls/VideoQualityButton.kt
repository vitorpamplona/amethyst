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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.PinBottomIconSize
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.Size50Modifier

/**
 * A gear icon button that opens the video quality picker.
 * Only visible when the video has multiple renditions (HLS/DASH adaptive streams).
 */
@Composable
fun VideoQualityButton(
    player: Player,
    controllerVisible: MutableState<Boolean>,
    modifier: Modifier = Modifier,
) {
    var tracks by remember { mutableStateOf(player.currentTracks) }
    var showSheet by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onTracksChanged(newTracks: Tracks) {
                    tracks = newTracks
                }
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val hasMultiple = hasMultipleRenditions(tracks)

    AnimatedVisibility(
        visible = controllerVisible.value && hasMultiple,
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
                onClick = { showSheet = true },
                modifier = Size50Modifier,
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringRes(id = R.string.video_quality),
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Size20Modifier,
                )
            }
        }
    }

    if (showSheet) {
        VideoQualitySheet(
            player = player,
            onDismiss = { showSheet = false },
        )
    }
}
