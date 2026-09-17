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
package com.vitorpamplona.amethyst.service.playback.composable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.LoadedMediaItem
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal const val BACKGROUND_RELEASE_TIMEOUT_MS = 30_000L

/**
 * Checks a pooled [androidx.media3.exoplayer.ExoPlayer] out for the duration of this composable and
 * hands it to [inner] as a [MediaControllerState].
 *
 * There is no MediaSession and no MediaController here. An inline video is a surface the user
 * scrolled past, not something the system needs to know about: it gets a player straight from the
 * process-wide pool, with no session to register, no binder bind to wait on, and no entry in the
 * notification shade. Only a playback the user explicitly promotes — see
 * [com.vitorpamplona.amethyst.service.playback.background.BackgroundPlayback] — claims the one
 * MediaSession that PlaybackService owns.
 */
@Composable
fun GetVideoController(
    mediaItem: LoadedMediaItem,
    muted: Boolean = false,
    play: Boolean = false,
    inner: @Composable (mediaControllerState: MediaControllerState) -> Unit,
) {
    val context = LocalContext.current

    // After the app has been in the background for BACKGROUND_RELEASE_TIMEOUT_MS, hand the player
    // back so its codec and buffer return to the pool. On resume the player is re-acquired, and the
    // pool's URI affinity gives back the same warm instance with its position and buffer intact.
    //
    // This gate is a StateFlow and not a Compose MutableState on purpose. The timeout fires while
    // the activity is STOPPED, and Compose pauses its frame clock below Lifecycle.STARTED, so a
    // state write there cannot recompose — and ON_START/ON_RESUME flip the flag back to true in the
    // same main thread message, before any frame, so the release would never happen at all.
    // Collecting a flow keeps the decision off the frame clock.
    val keepAlive = remember { MutableStateFlow(true) }

    val controllerState by produceState<MediaControllerState?>(null, mediaItem) {
        val pools = Amethyst.instance.videoPlayerPools
        val background = Amethyst.instance.backgroundPlayback

        keepAlive.collectLatest { alive ->
            if (!alive) {
                value = null
                return@collectLatest
            }

            // One checkout at a time, re-taken after a promotion ends. Promoting hands ownership
            // of the player to BackgroundPlayback; when the user gives the slot up we take a player
            // again and the pool's URI affinity returns the same instance, buffer and position
            // intact, so the video drops back inline exactly where it left off.
            while (true) {
                val pooled = pools.acquire(mediaItem.src.proxyPort, mediaItem.item.mediaId, mediaItem.src.repeatMode)
                val player = pooled.player
                var handedOver = false

                try {
                    // A warm player can be handed back still carrying a prior PlaybackException
                    // (e.g. a decoder-init failure from an earlier checkout). The prepare below
                    // clears it before WatchPlaybackErrors ever attaches, so this is the only place
                    // the stale error is observable. Logged so a "Can't play this video" blink that
                    // self-heals can be attributed to pool reuse rather than an undecodable stream.
                    player.playerError?.let { err ->
                        Log.w(ERROR_LOG_TAG) { "Player arrived carrying error for ${mediaItem.item.mediaId}: ${err.describe()}" }
                    }

                    player.volume =
                        when {
                            // Stay silent behind the playback the user detached from the feed.
                            background.isPlaying() -> 0f
                            muted -> 0f
                            else -> 1f
                        }

                    if (play) player.playWhenReady = true

                    // Warm fast path: when the pool returned the player that already holds this
                    // exact item, calling setMediaItem would reset it and throw the buffer away —
                    // exactly what the pool exists to avoid. Still re-prepare if it ended up IDLE.
                    val targetMediaId = mediaItem.item.mediaId
                    if (player.currentMediaItem?.mediaId != targetMediaId) {
                        Log.d(TAG) { "Cold load (setMediaItem+prepare) for $targetMediaId" }
                        player.setMediaItem(mediaItem.item)
                        player.prepare()
                    } else if (player.playbackState == Player.STATE_IDLE) {
                        Log.d(TAG) { "Warm player in STATE_IDLE — re-preparing" }
                        player.prepare()
                    }

                    value = MediaControllerState(controller = player, pooled = pooled)

                    // Suspends for as long as this player stays the feed's. If it is never promoted
                    // this is where the producer waits out the rest of the composable's life.
                    background.current.first { it?.player === player }
                    handedOver = true

                    // Detached: hand `null` down so the scroll mutex, the lifecycle pause and the
                    // on-screen controls all leave composition instead of pausing or re-playing the
                    // video the user is now watching in the picture-in-picture window.
                    value = null

                    background.current.first { it?.player !== player }
                } finally {
                    value = null
                    // A promoted player belongs to the slot now: it keeps playing after this
                    // composable is gone, and goes back to the pool when the slot is given up.
                    if (!handedOver) pooled.release()
                }
            }
        }
    }

    ReleasePlayerWhenBackgroundedFor(BACKGROUND_RELEASE_TIMEOUT_MS, controllerState, keepAlive)

    controllerState?.let { inner(it) }
}

/**
 * Flips [keepAlive] to `false` once the host activity has been at ON_PAUSE for [timeoutMs], so the
 * producer upstream hands the player back. ON_RESUME cancels any pending timer and flips it back.
 *
 * A promoted playback is exempt: it is opted into outliving the screen it started on.
 */
@Composable
private fun ReleasePlayerWhenBackgroundedFor(
    timeoutMs: Long,
    controllerState: MediaControllerState?,
    keepAlive: MutableStateFlow<Boolean>,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    // The observer outlives any single composition pass, so read the player through an updated
    // holder rather than capturing whatever was in scope when the effect was set up.
    val currentState by rememberUpdatedState(controllerState)

    DisposableEffect(lifecycleOwner, keepAlive) {
        val scope = CoroutineScope(Dispatchers.Main)
        var timeoutJob: Job? = null

        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> {
                        timeoutJob?.cancel()
                        timeoutJob =
                            scope.launch {
                                delay(timeoutMs)
                                if (!Amethyst.instance.backgroundPlayback.isPromoted(currentState?.controller)) {
                                    keepAlive.value = false
                                }
                            }
                    }

                    Lifecycle.Event.ON_RESUME -> {
                        timeoutJob?.cancel()
                        timeoutJob = null
                        keepAlive.value = true
                    }

                    else -> Unit
                }
            }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            timeoutJob?.cancel()
            lifecycleOwner.lifecycle.removeObserver(observer)
            scope.cancel()
        }
    }
}

private const val TAG = "VideoPlayback"
