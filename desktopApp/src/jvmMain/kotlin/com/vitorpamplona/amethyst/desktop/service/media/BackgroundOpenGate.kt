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
package com.vitorpamplona.amethyst.desktop.service.media

/**
 * Decides, one report of the watched video at a time, when the engines opened paused beside it may
 * start their own downloads (see GlobalMediaPlayer.holdBehindPlayingVideo). They wait only while a
 * video is starting: when the user paused it, or it ended or failed, they go at once.
 *
 * With no video open they wait only a moment ([Verdict.IDLE]): a screen opening gives its cards
 * their engines before it picks the one to play, and opening them then (as they did at once on the
 * first try) put them back between that video and the link. A feed with nothing to play, such as a
 * text note in the middle of Home, then costs its video cards that moment, not the whole hold.
 *
 * A video starts once its clock moves past where it began playing, not once it is past zero: one
 * that resumes where it was last left has a clock far from zero, and says it is playing, before its
 * picture moves. One gate per engine: it remembers that starting point.
 */
internal class BackgroundOpenGate {
    enum class Verdict {
        /** Open now. */
        OPEN,

        /** No video is open: open unless one starts within a moment. */
        IDLE,

        /** A video is starting: wait for it to move. */
        HOLD,
    }

    private var url: String? = null
    private var playingFrom: Long? = null

    /** What the engines should do, now that the watched video reports [state]. */
    fun check(
        state: MediaPlaybackState,
        pauseRequested: Boolean,
    ): Verdict {
        if (state.url != url) {
            url = state.url
            playingFrom = null
        }
        if (state.url == null) return Verdict.IDLE
        if (pauseRequested || state.errorReason != null || state.position >= GlobalMediaPlayer.ENDED_POSITION) return Verdict.OPEN
        if (!state.isPlaying) {
            playingFrom = null
            return Verdict.HOLD
        }
        val from = playingFrom ?: state.currentTime.also { playingFrom = it }
        return if (state.currentTime > from) Verdict.OPEN else Verdict.HOLD
    }
}
