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

import com.vitorpamplona.amethyst.commons.model.MediaAspectRatioCache
import com.vitorpamplona.amethyst.commons.ui.playback.player.AmethystPlayer
import com.vitorpamplona.amethyst.commons.ui.playback.player.PlaybackState
import com.vitorpamplona.amethyst.commons.ui.playback.player.VideoSize
import com.vitorpamplona.quartz.utils.Log
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionCategoryOptionMixWithOthers
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionModeDefault
import platform.AVFAudio.AVAudioSessionModeMoviePlayback
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.setActive

/** What the lock screen shows for a player: the note's title, author and cover. */
data class NowPlayingMetadata(
    val title: String? = null,
    val artist: String? = null,
    val artworkUrl: String? = null,
)

/**
 * The app's players, by URL. A URL shown twice (a feed card and its fullscreen view, or a note
 * scrolled away and back) gets the same [AvAmethystPlayer], so position and buffer carry over, as
 * Android's warm pool does. A player nobody shows stays paused and warm until [WARM_LIMIT] newer
 * ones push it out.
 *
 * It also keeps the audio session in step: "ambient, mixing with others" while every player is
 * muted, so a muted feed never stops the user's music, and "playback" once one is heard, which is
 * what lets it go on in the background and on the lock screen ([NowPlaying]).
 */
@OptIn(ExperimentalForeignApi::class)
object IosPlayerPool {
    private const val TAG = "IosPlayerPool"
    private const val WARM_LIMIT = 3

    private class Entry(
        val player: AvAmethystPlayer,
        var users: Int,
        var metadata: NowPlayingMetadata,
        val watcher: AmethystPlayer.Listener,
    )

    private val entries = LinkedHashMap<String, Entry>()

    /** The player the user hears, if any: the lock screen and background audio follow it. */
    var audible: AvAmethystPlayer? = null
        private set

    /** The player of [url]; a new one reads through the disk cache when [cacheable]. */
    fun acquire(
        url: String,
        metadata: NowPlayingMetadata,
        cacheable: Boolean = false,
    ): AvAmethystPlayer {
        entries[url]?.let { entry ->
            entry.users++
            entry.metadata = metadata
            // Most recently used goes last, so warm eviction drops the oldest.
            entries.remove(url)
            entries[url] = entry
            return entry.player
        }
        val player = AvAmethystPlayer(url, cacheable)
        val watcher =
            object : AmethystPlayer.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) = refreshAudio()

                override fun onVolumeChanged(volume: Float) = refreshAudio()

                override fun onPlaybackStateChanged(state: PlaybackState) = NowPlaying.update(audible, metadataOf(audible))

                override fun onPositionDiscontinuity() = NowPlaying.update(audible, metadataOf(audible))

                // What the player measured replaces the event's `dim` for the card's shape, as
                // Android's AspectRatioCacher does: `dim` can ignore rotation or be the stored size
                // of an anamorphic video. presentationSize is already the displayed shape.
                override fun onVideoSizeChanged(size: VideoSize) {
                    if (size.width > 0 && size.height > 0) MediaAspectRatioCache.addMeasured(url, size.width, size.height)
                }
            }
        player.addListener(watcher)
        entries[url] = Entry(player, 1, metadata, watcher)
        trimWarm()
        return player
    }

    fun release(player: AvAmethystPlayer) {
        val entry = entries[player.url] ?: return
        entry.users = (entry.users - 1).coerceAtLeast(0)
        if (entry.users == 0 && player != audible && player != PictureInPicture.activePlayer) player.pause()
        trimWarm()
    }

    /** The player of [url], if one is alive. */
    fun find(url: String): AvAmethystPlayer? = entries[url]?.player

    fun metadataOf(player: AvAmethystPlayer?): NowPlayingMetadata? = player?.let { entries[it.url]?.metadata }

    /** Pauses everything except what the user is listening to (it may go on in the background). */
    fun pauseAllButAudible() {
        entries.values.forEach { if (it.player != audible && it.player != PictureInPicture.activePlayer && it.player.isPlaying) it.player.pause() }
    }

    private fun trimWarm() {
        val idle = entries.values.filter { it.users == 0 && it.player != audible && it.player != PictureInPicture.activePlayer }
        val excess = idle.size - WARM_LIMIT
        if (excess <= 0) return
        idle.take(excess).forEach { entry ->
            Log.d(TAG) { "Dropping warm player ${entry.player.url}" }
            entry.player.removeListener(entry.watcher)
            entry.player.release()
            entries.remove(entry.player.url)
        }
    }

    private fun refreshAudio() {
        val heard = entries.values.firstOrNull { it.player.isPlaying && it.player.volume > 0f }?.player
        if (heard != null) {
            audible = heard
            AudioSessionMode.playback()
        } else if (audible?.isPlaying != true || (audible?.volume ?: 0f) <= 0f) {
            // Paused but still the one the lock screen controls; muted or gone means nobody is heard.
            if ((audible?.volume ?: 0f) <= 0f) audible = null
            if (audible == null) AudioSessionMode.ambient()
        }
        NowPlaying.update(audible, metadataOf(audible))
    }
}

@OptIn(ExperimentalForeignApi::class)
internal object AudioSessionMode {
    private var current: String? = null

    fun playback() {
        if (current == AVAudioSessionCategoryPlayback) return
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, mode = AVAudioSessionModeMoviePlayback, options = 0u, error = null)
        session.setActive(true, error = null)
        current = AVAudioSessionCategoryPlayback
    }

    /** Records from the microphone while the rest of the app's sound keeps playing. */
    fun record() {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayAndRecord, mode = AVAudioSessionModeDefault, options = AVAudioSessionCategoryOptionDefaultToSpeaker, error = null)
        session.setActive(true, error = null)
        current = AVAudioSessionCategoryPlayAndRecord
    }

    /** After a recording: back to whatever the players need. */
    fun release() {
        if (IosPlayerPool.audible != null) playback() else ambient()
    }

    fun ambient() {
        if (current == AVAudioSessionCategoryAmbient) return
        val session = AVAudioSession.sharedInstance()
        val wasPlayback = current == AVAudioSessionCategoryPlayback
        session.setCategory(AVAudioSessionCategoryAmbient, mode = AVAudioSessionModeMoviePlayback, options = AVAudioSessionCategoryOptionMixWithOthers, error = null)
        // Hands the audio back to whatever the user had playing before.
        if (wasPlayback) session.setActive(false, withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, error = null)
        current = AVAudioSessionCategoryAmbient
    }
}
