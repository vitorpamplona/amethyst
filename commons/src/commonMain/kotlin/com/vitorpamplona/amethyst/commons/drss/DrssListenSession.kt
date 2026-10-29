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
package com.vitorpamplona.amethyst.commons.drss

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.drss.listenProgress.DrssListenPass
import kotlin.math.abs

/**
 * When to save listening progress while one episode plays, the way drss.io's player does: once a
 * minute while playing (the first tick only starts the clock, so loading an episode never
 * overwrites another device's record), on pause, at the end, and when playback counts a play
 * ([DrssListenPass]). A save that would repeat the last one is skipped.
 *
 * The player reports its playhead about once a second through [onSample]; a jump the elapsed time
 * cannot explain is taken as a seek, so a player need not report seeks itself.
 */
class DrssListenSession {
    /** What to save: hand it to `DrssState.saveProgress`. */
    @Immutable
    data class Save(
        val position: Long?,
        val durationInSeconds: Long?,
        val countsAPlay: Boolean,
        val ended: Boolean,
    )

    private val pass = DrssListenPass()
    private var lastPosition = -1.0
    private var lastSampleAt = 0L
    private var lastDuration: Long? = null
    private var wasPlaying = false
    private var hasPlayed = false
    private var clockStartedAt: Long? = null
    private var ended = false
    private var lastSaved: Save? = null

    fun onSample(
        positionInSeconds: Double,
        durationInSeconds: Double?,
        isPlaying: Boolean,
        hasEnded: Boolean,
        nowMillis: Long,
    ): Save? {
        val duration = durationInSeconds?.takeIf { it > 0 }
        duration?.let { lastDuration = it.toLong() }

        if (hasEnded) {
            if (ended) return null
            ended = true
            wasPlaying = false
            return dedup(Save(null, lastDuration, countsAPlay = false, ended = true))
        }
        // Playing again after the end: a replay, with its own resume point.
        if (ended && isPlaying) ended = false

        if (lastPosition >= 0) {
            val elapsed = if (wasPlaying) (nowMillis - lastSampleAt) / 1000.0 else 0.0
            if (abs(positionInSeconds - (lastPosition + elapsed)) > SEEK_TOLERANCE_SECONDS) pass.seeked()
        }
        val countsAPlay = duration != null && pass.update(positionInSeconds, duration, isPlaying)
        val position = positionInSeconds.toLong()

        val save =
            when {
                countsAPlay -> Save(position, lastDuration, countsAPlay = true, ended = false)
                wasPlaying && !isPlaying && position > 0 && !ended -> Save(position, lastDuration, countsAPlay = false, ended = false)
                isPlaying && position > 0 -> {
                    val started = clockStartedAt
                    if (started == null) {
                        clockStartedAt = nowMillis
                        null
                    } else if (nowMillis - started >= DrssListenPass.RELAY_SAVE_INTERVAL_SECONDS * 1000) {
                        clockStartedAt = nowMillis
                        Save(position, lastDuration, countsAPlay = false, ended = false)
                    } else {
                        null
                    }
                }
                else -> null
            }

        lastPosition = positionInSeconds
        lastSampleAt = nowMillis
        wasPlaying = isPlaying
        if (isPlaying) hasPlayed = true
        return save?.let(::dedup)
    }

    /** The player is going away: save where it stopped, unless it ended or never played. */
    fun onLeave(): Save? {
        if (ended || !hasPlayed || lastPosition <= 0) return null
        return dedup(Save(lastPosition.toLong(), lastDuration, countsAPlay = false, ended = false))
    }

    private fun dedup(save: Save): Save? {
        if (save == lastSaved) return null
        lastSaved = save
        return save
    }

    companion object {
        const val SEEK_TOLERANCE_SECONDS = 3.0
    }
}
