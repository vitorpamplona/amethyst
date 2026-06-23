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
package com.vitorpamplona.amethyst.ui.feeds

import android.content.Context
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LifecycleOwner
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.richtext.HashTagSegment
import com.vitorpamplona.amethyst.commons.richtext.RegularTextSegment
import com.vitorpamplona.amethyst.commons.richtext.RichTextViewerState
import com.vitorpamplona.amethyst.commons.richtext.Segment
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.service.CachedRichTextParser
import com.vitorpamplona.amethyst.service.tts.TextToSpeechHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives the "read the feed aloud" accessibility/driving mode.
 *
 * One instance is held by the [AccountViewModel][com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel]
 * so both the shared top bar (which renders the play/stop button) and the feed bodies (which
 * register themselves) talk to the same object without any CompositionLocal plumbing.
 *
 * Whatever feed screen is currently composed calls [register] with its [FeedContentState] and live
 * [LazyListState]; when it leaves composition it [unregister]s. Pressing the button speaks the loaded
 * posts of the registered feed top-to-bottom, scrolling so the post being read stays on screen, and
 * stops when it reaches the end (or when the user presses stop again). There is no background service:
 * [TextToSpeechHelper] auto-stops when the app is paused.
 */
@Stable
class FeedReadAloudState {
    /** True while a feed screen is on-screen and has posts to read — gates the top-bar button. */
    var hasReadableFeed by mutableStateOf(false)
        private set

    /** True while actively speaking the feed. Drives the play vs stop icon. */
    var isPlaying by mutableStateOf(false)
        private set

    private var feed: FeedContentState? = null
    private var listState: LazyListState? = null

    fun register(
        feed: FeedContentState,
        listState: LazyListState,
    ) {
        this.feed = feed
        this.listState = listState
        hasReadableFeed = true
    }

    fun unregister(feed: FeedContentState) {
        // Identity-checked: in a HorizontalPager an arriving page registers before the leaving page
        // disposes, so only clear if we're still the holder for this exact feed.
        if (this.feed === feed) {
            this.feed = null
            this.listState = null
            hasReadableFeed = false
            stop()
        }
    }

    fun toggle(
        context: Context,
        owner: LifecycleOwner,
        scope: CoroutineScope,
    ) {
        if (isPlaying) {
            stop()
        } else {
            start(context, owner, scope)
        }
    }

    private fun start(
        context: Context,
        owner: LifecycleOwner,
        scope: CoroutineScope,
    ) {
        val feed = feed ?: return
        if (feed.visibleNotes().isEmpty()) return

        isPlaying = true

        val startNoteIndex = currentTopNoteIndex(feed.visibleNotes().size)
        speakNoteAt(startNoteIndex, context, owner, scope)
    }

    fun stop() {
        if (!isPlaying) return
        isPlaying = false
        try {
            TextToSpeechHelper.getInstance(appContextOrNull ?: return).destroy()
        } catch (_: Exception) {
        }
    }

    // Kept so stop() can reach the singleton even without a fresh Context; set on each speak.
    private var appContextOrNull: Context? = null

    private fun speakNoteAt(
        index: Int,
        context: Context,
        owner: LifecycleOwner,
        scope: CoroutineScope,
    ) {
        if (!isPlaying) return

        val feed =
            feed ?: run {
                stop()
                return
            }
        val notes = feed.visibleNotes()
        if (index >= notes.size) {
            // Reached the end of the loaded feed — stop (the chosen v1 behavior, no auto-load).
            stop()
            return
        }

        val note = notes[index]
        appContextOrNull = context.applicationContext

        // Keep the post being read on screen.
        listState?.let { state ->
            scope.launch {
                runCatching { state.animateScrollToItem(lazyIndexFor(index, notes.size, state)) }
            }
        }

        val mediaFallback = context.getString(R.string.read_feed_aloud_media_fallback)

        scope.launch {
            val speech = withContext(Dispatchers.Default) { buildSpeech(note, mediaFallback) }
            if (!isPlaying) return@launch

            if (speech.isBlank()) {
                // Nothing worth reading (e.g. a reaction/empty event) — skip to the next.
                speakNoteAt(index + 1, context, owner, scope)
                return@launch
            }

            TextToSpeechHelper
                .getInstance(context)
                .registerLifecycle(owner)
                .speak(speech)
                .onDone { speakNoteAt(index + 1, context, owner, scope) }
                .onError {
                    // Skip the offending post rather than aborting the whole session.
                    speakNoteAt(index + 1, context, owner, scope)
                }
        }
    }

    /** Best-effort note index of whatever post is currently at the top of the viewport. */
    private fun currentTopNoteIndex(noteCount: Int): Int {
        val state = listState ?: return 0
        val offset = (state.layoutInfo.totalItemsCount - noteCount).coerceAtLeast(0)
        return (state.firstVisibleItemIndex - offset).coerceIn(0, (noteCount - 1).coerceAtLeast(0))
    }

    /**
     * Maps a note index to a LazyColumn item index. Feeds may render header items (e.g. the Home
     * live-activity bubbles) before the post list, so the offset is the difference between the total
     * item count and the number of posts.
     */
    private fun lazyIndexFor(
        noteIndex: Int,
        noteCount: Int,
        state: LazyListState,
    ): Int {
        val offset = (state.layoutInfo.totalItemsCount - noteCount).coerceAtLeast(0)
        return noteIndex + offset
    }

    private fun buildSpeech(
        note: Note,
        mediaFallback: String,
    ): String {
        val event = note.event ?: return ""
        val author = note.author?.toBestDisplayName()?.takeIf { it.isNotBlank() }

        val parsed: RichTextViewerState =
            CachedRichTextParser.parseText(
                content = event.content,
                tags = event.tags.toImmutableListOfLists(),
                callbackUri = null,
                authorPubKey = note.author?.pubkeyHex,
            )

        val body = flattenToSpeech(parsed)
        val text =
            if (body.isNotBlank()) {
                body
            } else if (parsed.mediaList.isNotEmpty()) {
                mediaFallback
            } else {
                ""
            }

        if (text.isBlank()) return ""

        return if (author != null) "$author. $text" else text
    }

    /**
     * Flattens parsed rich text to something worth speaking: plain text + hashtags. URLs, media,
     * bech32/nostr references, invoices, cashu tokens, custom emoji and the like are dropped so the
     * reader doesn't recite long unreadable tokens out loud.
     */
    private fun flattenToSpeech(state: RichTextViewerState): String {
        val sb = StringBuilder()
        state.paragraphs.forEach { paragraph ->
            paragraph.words.forEach { word ->
                if (isSpeakable(word)) {
                    sb.append(word.segmentText).append(' ')
                }
            }
            sb.append('\n')
        }
        return sb.toString().replace(WHITESPACE_RUN, " ").trim()
    }

    private fun isSpeakable(segment: Segment): Boolean =
        when (segment) {
            is RegularTextSegment -> true
            is HashTagSegment -> true
            // The base class is emitted for plain whitespace/punctuation runs between words.
            else -> segment::class == Segment::class
        }

    companion object {
        private val WHITESPACE_RUN = Regex("\\s+")
    }
}
