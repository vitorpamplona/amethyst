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
package com.vitorpamplona.amethyst.ios.playback

import com.vitorpamplona.amethyst.commons.ui.playback.player.AmethystPlayer
import com.vitorpamplona.amethyst.ios.toNSData
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.MediaPlayer.MPChangePlaybackPositionCommandEvent
import platform.MediaPlayer.MPMediaItemArtwork
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyArtwork
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyIsLiveStream
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandEvent
import platform.MediaPlayer.MPRemoteCommandHandlerStatus
import platform.MediaPlayer.MPRemoteCommandHandlerStatusCommandFailed
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
import platform.UIKit.UIImage

/**
 * The lock screen and Control Center for the player the user hears: title, author, cover,
 * progress, and the play/pause, scrub and ±10s commands, which act on that player.
 */
@OptIn(ExperimentalForeignApi::class)
object NowPlaying {
    /** Fetches a cover image's bytes; set by the app (it owns the HTTP client). */
    var artworkLoader: (suspend (String) -> ByteArray?)? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var target: AvAmethystPlayer? = null
    private var commandsInstalled = false
    private var artworkFor: String? = null
    private var artwork: MPMediaItemArtwork? = null

    fun update(
        player: AvAmethystPlayer?,
        metadata: NowPlayingMetadata?,
    ) {
        target = player
        val center = MPNowPlayingInfoCenter.defaultCenter()
        if (player == null) {
            center.nowPlayingInfo = null
            return
        }
        installCommands()
        loadArtwork(metadata?.artworkUrl)

        val duration = player.duration
        val info = mutableMapOf<Any?, Any?>()
        info[MPMediaItemPropertyTitle] = metadata?.title ?: player.url.substringAfterLast('/')
        metadata?.artist?.let { info[MPMediaItemPropertyArtist] = it }
        info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = player.currentPosition / 1000.0
        info[MPNowPlayingInfoPropertyPlaybackRate] = if (player.isPlaying) player.playbackSpeed.toDouble() else 0.0
        if (duration == AmethystPlayer.TIME_UNSET) {
            info[MPNowPlayingInfoPropertyIsLiveStream] = true
        } else {
            info[MPMediaItemPropertyPlaybackDuration] = duration / 1000.0
        }
        artwork?.takeIf { artworkFor == metadata?.artworkUrl }?.let { info[MPMediaItemPropertyArtwork] = it }
        center.nowPlayingInfo = info
    }

    private fun loadArtwork(url: String?) {
        if (url == null || url == artworkFor) return
        artworkFor = url
        artwork = null
        val loader = artworkLoader ?: return
        scope.launch {
            val bytes = loader(url) ?: return@launch
            val image = UIImage.imageWithData(bytes.toNSData()) ?: return@launch
            if (artworkFor != url) return@launch
            artwork = MPMediaItemArtwork(boundsSize = image.size) { _ -> image }
            update(target, IosPlayerPool.metadataOf(target))
        }
    }

    private fun installCommands() {
        if (commandsInstalled) return
        commandsInstalled = true
        val center = MPRemoteCommandCenter.sharedCommandCenter()

        fun handle(action: (AvAmethystPlayer) -> Unit): (MPRemoteCommandEvent?) -> MPRemoteCommandHandlerStatus =
            { _ ->
                val player = target
                if (player == null) {
                    MPRemoteCommandHandlerStatusCommandFailed
                } else {
                    action(player)
                    update(player, IosPlayerPool.metadataOf(player))
                    MPRemoteCommandHandlerStatusSuccess
                }
            }
        center.playCommand.addTargetWithHandler(handle { it.play() })
        center.pauseCommand.addTargetWithHandler(handle { it.pause() })
        center.togglePlayPauseCommand.addTargetWithHandler(handle { if (it.isPlaying) it.pause() else it.play() })
        center.skipForwardCommand.preferredIntervals = listOf(SKIP_SECONDS)
        center.skipForwardCommand.addTargetWithHandler(handle { it.seekTo(it.currentPosition + SKIP_SECONDS * 1000) })
        center.skipBackwardCommand.preferredIntervals = listOf(SKIP_SECONDS)
        center.skipBackwardCommand.addTargetWithHandler(handle { it.seekTo((it.currentPosition - SKIP_SECONDS * 1000).coerceAtLeast(0)) })
        center.changePlaybackPositionCommand.addTargetWithHandler { event ->
            val player = target
            val seconds = (event as? MPChangePlaybackPositionCommandEvent)?.positionTime
            if (player == null || seconds == null) {
                MPRemoteCommandHandlerStatusCommandFailed
            } else {
                player.seekTo((seconds * 1000).toLong())
                update(player, IosPlayerPool.metadataOf(player))
                MPRemoteCommandHandlerStatusSuccess
            }
        }
    }

    private const val SKIP_SECONDS = 10L
}
