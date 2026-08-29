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
package com.vitorpamplona.amethyst.desktop.media

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import io.github.kdroidfilter.composemediaplayer.InitialPlayerState
import io.github.kdroidfilter.composemediaplayer.SubtitleTrack
import io.github.kdroidfilter.composemediaplayer.VideoMetadata
import io.github.kdroidfilter.composemediaplayer.VideoPlayerError
import io.github.kdroidfilter.composemediaplayer.VideoPlayerState
import io.github.vinceglb.filekit.PlatformFile

/**
 * A ComposeMediaPlayer engine that records what was asked of it instead of
 * loading a native backend.
 *
 * Written out by hand rather than mocked so the test states exactly what the
 * engine contract is: which calls the handle is expected to make, and what the
 * engine reports back. Members the seam never touches are present because the
 * interface requires them, and are inert.
 */
class FakeVideoPlayerState : VideoPlayerState {
    var openedUri: String? = null
        private set
    var playCalls = 0
        private set
    var pauseCalls = 0
        private set
    var stopCalls = 0
        private set
    var disposed = false
        private set

    /** Set by the test to simulate the backend having probed the media. */
    var reportedDuration: Double = 0.0
    var reportedCurrentTime: Double = 0.0
    var reportedHasMedia: Boolean = false
    var reportedIsLoading: Boolean = false
    var reportedError: VideoPlayerError? = null
    var reportedAspectRatio: Float = 0f

    override val hasMedia: Boolean get() = reportedHasMedia
    override val isPlaying: Boolean get() = playCalls > pauseCalls + stopCalls
    override val isLoading: Boolean get() = reportedIsLoading
    override val currentTime: Double get() = reportedCurrentTime
    override val duration: Double get() = reportedDuration
    override val aspectRatio: Float get() = reportedAspectRatio
    override val error: VideoPlayerError? get() = reportedError
    override val positionText: String get() = ""
    override val durationText: String get() = ""
    override val metadata: VideoMetadata = VideoMetadata()
    override val availableSubtitleTracks: MutableList<SubtitleTrack> = mutableListOf()

    override var volume: Float = 1f
    override var sliderPos: Float = 0f
    override var userDragging: Boolean = false
    override var loop: Boolean = false
    override var playbackSpeed: Float = 1f
    override var onPlaybackEnded: (() -> Unit)? = null
    override var onRestart: (() -> Unit)? = null
    override var isFullscreen: Boolean = false
    override var subtitlesEnabled: Boolean = false
    override var currentSubtitleTrack: SubtitleTrack? = null
    override var subtitleTextStyle: TextStyle = TextStyle.Default
    override var subtitleBackgroundColor: Color = Color.Transparent

    override fun play() {
        playCalls++
    }

    override fun pause() {
        pauseCalls++
    }

    override fun stop() {
        stopCalls++
    }

    override fun seekTo(value: Float) {
        sliderPos = value
    }

    override fun openUri(
        uri: String,
        initializeplayerState: InitialPlayerState,
    ) {
        openedUri = uri
    }

    override fun openFile(
        file: PlatformFile,
        initializeplayerState: InitialPlayerState,
    ) = Unit

    override fun toggleFullscreen() {
        isFullscreen = !isFullscreen
    }

    override fun clearError() {
        reportedError = null
    }

    override fun selectSubtitleTrack(track: SubtitleTrack?) = Unit

    override fun disableSubtitles() = Unit

    override fun dispose() {
        disposed = true
    }
}
