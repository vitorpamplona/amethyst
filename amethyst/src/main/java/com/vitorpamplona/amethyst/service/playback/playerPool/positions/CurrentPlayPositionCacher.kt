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
package com.vitorpamplona.amethyst.service.playback.playerPool.positions

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.vitorpamplona.amethyst.service.playback.diskCache.isLiveStreaming
import kotlin.math.abs

class CurrentPlayPositionCacher(
    val player: Player,
    val cache: MutableVideoViewedPositionCache,
) : Player.Listener {
    var currentUrl: String? = null
    var isLiveStreaming: Boolean = false

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        if (mediaItem == null) {
            currentUrl = null
            isLiveStreaming = false
        } else {
            currentUrl = mediaItem.mediaId
            isLiveStreaming = isLiveStreaming(mediaItem.mediaId)
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        currentUrl?.let { uri ->
            if (!isLiveStreaming) {
                cache.add(uri, player.currentPosition)
            }
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        currentUrl?.let { uri ->
            when (playbackState) {
                Player.STATE_IDLE -> {
                    // only saves if it wqs playing
                    if (!isLiveStreaming && abs(player.currentPosition) > 1) {
                        cache.add(uri, player.currentPosition)
                    }
                }

                Player.STATE_READY -> {
                    if (!isLiveStreaming) {
                        cache.get(uri)?.let { lastPosition ->
                            if (abs(player.currentPosition - lastPosition) > 5 * 60) {
                                player.seekTo(lastPosition)
                            }
                        }
                    }
                }

                else -> {
                    // only saves if it wqs playing
                    if (!isLiveStreaming && abs(player.currentPosition) > 1) {
                        cache.add(uri, player.currentPosition)
                    }
                }
            }
        }
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        currentUrl?.let { uri ->
            if (!isLiveStreaming && player.playbackState != Player.STATE_IDLE) {
                cache.add(uri, newPosition.positionMs)
            }
        }
    }
}
