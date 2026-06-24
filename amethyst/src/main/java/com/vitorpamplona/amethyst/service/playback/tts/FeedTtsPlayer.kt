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
package com.vitorpamplona.amethyst.service.playback.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.vitorpamplona.quartz.utils.Log
import java.util.Locale

/**
 * A Media3 [Player] whose "playback" is the device [TextToSpeech] engine reading a playlist of
 * posts. Each [MediaItem] is one post; the text to speak rides in its metadata
 * ([MediaMetadata.extras] under [KEY_TTS_TEXT]). Wrapping TTS in a Player lets the existing
 * `PlaybackService` (a `MediaSessionService`) expose it through the system: lockscreen + Android
 * Auto transport controls, the foreground notification, play/pause/skip and speed — all for free.
 *
 * The engine has no notion of position within an utterance, so items report an unknown duration and
 * are not seekable; transport is whole-post next/previous. Auto-advance happens on utterance
 * completion. Audio focus is requested while speaking so we duck music and pause for calls.
 *
 * Runs entirely on the main thread (the application looper), as [SimpleBasePlayer] requires.
 */
@OptIn(UnstableApi::class)
class FeedTtsPlayer(
    context: Context,
) : SimpleBasePlayer(Looper.getMainLooper()) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var playlist: List<MediaItemData> = emptyList()
    private var currentIndex = 0
    private var playWhenReady = false
    private var playbackState = Player.STATE_IDLE
    private var speed = 1f

    private var ttsReady = false
    private var pendingSpeak = false

    // Monotonic token so a late onDone from an utterance we've since superseded (skip, stop,
    // playlist change) doesn't advance the wrong post.
    private var utteranceToken = 0

    private var audioFocusRequest: AudioFocusRequest? = null

    private val tts: TextToSpeech =
        TextToSpeech(appContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts.setSpeechRate(speed)
                if (pendingSpeak) {
                    pendingSpeak = false
                    speakCurrent()
                }
            } else {
                Log.w("FeedTtsPlayer") { "TextToSpeech init failed: $status" }
            }
        }

    init {
        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    val token = utteranceId?.toIntOrNull() ?: return
                    handler.post { onUtteranceDone(token) }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    val token = utteranceId?.toIntOrNull() ?: return
                    // Skip the offending post rather than aborting the session.
                    handler.post { onUtteranceDone(token) }
                }
            },
        )
    }

    override fun getState(): State {
        val builder =
            State
                .Builder()
                .setAvailableCommands(AVAILABLE_COMMANDS)
                .setPlaybackState(playbackState)
                .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .setPlaylist(playlist)
                .setPlaybackParameters(PlaybackParameters(speed))
        if (playlist.isNotEmpty()) {
            builder.setCurrentMediaItemIndex(currentIndex.coerceIn(0, playlist.lastIndex))
        }
        return builder.build()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        if (playbackState == Player.STATE_IDLE && playlist.isNotEmpty()) {
            playbackState = Player.STATE_READY
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        this.playWhenReady = playWhenReady
        if (playWhenReady) {
            if (requestAudioFocus()) {
                if (playbackState == Player.STATE_ENDED) {
                    currentIndex = 0
                    playbackState = if (playlist.isEmpty()) Player.STATE_IDLE else Player.STATE_READY
                }
                speakCurrent()
            } else {
                // Couldn't get focus (e.g. an active call) — bounce back to paused.
                this.playWhenReady = false
            }
        } else {
            stopSpeaking()
            abandonAudioFocus()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<*> {
        if (playlist.isNotEmpty()) {
            currentIndex = mediaItemIndex.coerceIn(0, playlist.lastIndex)
            playbackState = Player.STATE_READY
            if (playWhenReady) speakCurrent()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<*> {
        playlist = mediaItems.map(::toItemData)
        currentIndex = if (startIndex == C.INDEX_UNSET) 0 else startIndex.coerceIn(0, maxOf(0, playlist.lastIndex))
        playbackState = if (playlist.isEmpty()) Player.STATE_IDLE else Player.STATE_READY
        if (playWhenReady && playlist.isNotEmpty()) speakCurrent()
        return Futures.immediateVoidFuture()
    }

    override fun handleAddMediaItems(
        index: Int,
        mediaItems: List<MediaItem>,
    ): ListenableFuture<*> {
        val insertAt = index.coerceIn(0, playlist.size)
        playlist = playlist.toMutableList().apply { addAll(insertAt, mediaItems.map(::toItemData)) }
        // Appending more posts at the end (auto-load-more) revives a feed that had run dry.
        if (playbackState == Player.STATE_ENDED && playWhenReady && currentIndex < playlist.lastIndex) {
            currentIndex += 1
            playbackState = Player.STATE_READY
            speakCurrent()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> {
        speed = playbackParameters.speed.coerceIn(0.25f, 4f)
        if (ttsReady) tts.setSpeechRate(speed)
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        stopSpeaking()
        abandonAudioFocus()
        playWhenReady = false
        playbackState = if (playlist.isEmpty()) Player.STATE_IDLE else Player.STATE_READY
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        stopSpeaking()
        abandonAudioFocus()
        tts.shutdown()
        return Futures.immediateVoidFuture()
    }

    private fun toItemData(mediaItem: MediaItem): MediaItemData =
        MediaItemData
            .Builder(mediaItem.mediaId)
            .setMediaItem(mediaItem)
            .setMediaMetadata(mediaItem.mediaMetadata)
            .setDurationUs(C.TIME_UNSET)
            .setIsSeekable(false)
            .setIsDynamic(false)
            .build()

    private fun speakCurrent() {
        if (!playWhenReady) return
        val item = playlist.getOrNull(currentIndex) ?: return
        if (!ttsReady) {
            pendingSpeak = true
            return
        }
        val text =
            item.mediaItem.mediaMetadata.extras
                ?.getString(KEY_TTS_TEXT)
        if (text.isNullOrBlank()) {
            // Nothing to read for this post — move on immediately.
            handler.post { advance() }
            return
        }
        utteranceToken += 1
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceToken.toString())
    }

    private fun onUtteranceDone(token: Int) {
        if (token != utteranceToken) return
        advance()
    }

    private fun advance() {
        if (!playWhenReady) return
        if (currentIndex < playlist.lastIndex) {
            currentIndex += 1
            playbackState = Player.STATE_READY
            invalidateState()
            speakCurrent()
        } else {
            // End of the loaded playlist. Stay paused at the end until more is appended.
            playbackState = Player.STATE_ENDED
            playWhenReady = false
            abandonAudioFocus()
            invalidateState()
        }
    }

    private fun stopSpeaking() {
        utteranceToken += 1
        pendingSpeak = false
        if (ttsReady) tts.stop()
    }

    private fun requestAudioFocus(): Boolean {
        audioFocusRequest?.let { return true }
        val attrs =
            AudioAttributes
                .Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        val request =
            AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener({ change -> handler.post { onFocusChange(change) } }, handler)
                .build()
        val granted =
            runCatching { audioManager.requestAudioFocus(request) }.getOrNull() ==
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (granted) audioFocusRequest = request
        return granted
    }

    private fun abandonAudioFocus() {
        val request = audioFocusRequest ?: return
        runCatching { audioManager.abandonAudioFocusRequest(request) }
        audioFocusRequest = null
    }

    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> {
                // TTS can't duck gracefully, so pause on any loss (a call, nav prompt, other media).
                if (playWhenReady) {
                    playWhenReady = false
                    stopSpeaking()
                    invalidateState()
                }
            }
        }
    }

    companion object {
        // MediaMetadata.extras key carrying the resolved text to speak for a post.
        const val KEY_TTS_TEXT = "tts_text"

        val DEFAULT_LOCALE: Locale = Locale.getDefault()

        private val AVAILABLE_COMMANDS =
            Player.Commands
                .Builder()
                .addAll(
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_PREPARE,
                    Player.COMMAND_STOP,
                    Player.COMMAND_RELEASE,
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                    Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                    Player.COMMAND_GET_TIMELINE,
                    Player.COMMAND_GET_METADATA,
                    Player.COMMAND_SET_MEDIA_ITEM,
                    Player.COMMAND_CHANGE_MEDIA_ITEMS,
                    Player.COMMAND_SET_SPEED_AND_PITCH,
                ).build()

        fun buildMediaItem(
            noteId: String,
            speechText: String,
            title: String?,
            artworkUri: String?,
        ): MediaItem {
            val extras = Bundle().apply { putString(KEY_TTS_TEXT, speechText) }
            val metadata =
                androidx.media3.common.MediaMetadata
                    .Builder()
                    .setTitle(title)
                    .setExtras(extras)
                    .apply { artworkUri?.let { setArtworkUri(android.net.Uri.parse(it)) } }
                    .build()
            return MediaItem
                .Builder()
                .setMediaId(noteId)
                .setMediaMetadata(metadata)
                .build()
        }
    }
}
