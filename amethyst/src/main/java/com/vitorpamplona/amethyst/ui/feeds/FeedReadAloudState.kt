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
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.richtext.BechSegment
import com.vitorpamplona.amethyst.commons.richtext.HashIndexEventSegment
import com.vitorpamplona.amethyst.commons.richtext.HashIndexUserSegment
import com.vitorpamplona.amethyst.commons.richtext.HashTagSegment
import com.vitorpamplona.amethyst.commons.richtext.RegularTextSegment
import com.vitorpamplona.amethyst.commons.richtext.RichTextViewerState
import com.vitorpamplona.amethyst.commons.richtext.Segment
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.CachedRichTextParser
import com.vitorpamplona.amethyst.service.tts.TextToSpeechHelper
import com.vitorpamplona.quartz.nip18Reposts.BaseRepostEvent
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
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

        val labels =
            SpeechLabels(
                mediaFallback = context.getString(R.string.read_feed_aloud_media_fallback),
                quoteIntro = context.getString(R.string.read_feed_aloud_quote_intro),
                repostLabel = context.getString(R.string.read_feed_aloud_repost_label),
            )

        scope.launch {
            val speech = withContext(Dispatchers.Default) { buildSpeech(note, labels, QUOTE_DEPTH) }
            if (!isPlaying) return@launch

            if (speech.isBlank()) {
                // Nothing worth reading (e.g. a reaction/empty event) — skip to the next.
                speakNoteAt(index + 1, context, owner, scope)
                return@launch
            }

            TextToSpeechHelper
                .getInstance(context)
                .registerLifecycle(owner)
                // When the lifecycle (background/stop) tears the engine down, reflect that in our
                // state so the top-bar button returns to play instead of being stuck on stop.
                .setCustomActionForDestroy { isPlaying = false }
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

    /** The localized words the speech builder splices in (so the recursion stays Context-free). */
    private class SpeechLabels(
        val mediaFallback: String,
        val quoteIntro: String,
        val repostLabel: String,
    )

    private fun buildSpeech(
        note: Note,
        labels: SpeechLabels,
        quotesLeft: Int,
    ): String {
        val event = note.event ?: return ""

        // Reposts (kind 6 / 16) carry the original note in replyTo; read "<reposter> reposted"
        // then the original. Reposting doesn't count against the quote-nesting budget.
        repostedNote(note)?.let { reposted ->
            val inner = buildSpeech(reposted, labels, quotesLeft)
            if (inner.isBlank()) return ""
            val reposter = note.author?.let { realNameOrNull(it) }
            return if (reposter != null) "$reposter ${labels.repostLabel}. $inner" else inner
        }

        val author = note.author?.let { realNameOrNull(it) }

        val parsed: RichTextViewerState =
            CachedRichTextParser.parseText(
                content = event.content,
                tags = event.tags.toImmutableListOfLists(),
                callbackUri = null,
                authorPubKey = note.author?.pubkeyHex,
            )

        val body = flattenToSpeech(parsed, labels, quotesLeft)
        val text =
            if (body.isNotBlank()) {
                body
            } else if (parsed.mediaList.isNotEmpty()) {
                labels.mediaFallback
            } else {
                ""
            }

        if (text.isBlank()) return ""

        return if (author != null) "$author. $text" else text
    }

    private fun repostedNote(note: Note): Note? = if (note.event is BaseRepostEvent) note.replyTo?.lastOrNull() else null

    /**
     * Flattens parsed rich text to something worth speaking: plain text + hashtags, with `nostr:`
     * mentions replaced by the mentioned person's name and quoted notes read inline (up to
     * [QUOTE_DEPTH] levels). URLs, media, invoices, cashu tokens, custom emoji and the like are
     * dropped so the reader doesn't recite long unreadable tokens out loud.
     */
    private fun flattenToSpeech(
        state: RichTextViewerState,
        labels: SpeechLabels,
        quotesLeft: Int,
    ): String {
        val sb = StringBuilder()
        state.paragraphs.forEach { paragraph ->
            paragraph.words.forEach { word ->
                speak(word, labels, quotesLeft)?.let { sb.append(it).append(' ') }
            }
            sb.append('\n')
        }
        return sb.toString().replace(WHITESPACE_RUN, " ").trim()
    }

    /** The spoken form of a single segment, or null when it has nothing worth reading aloud. */
    private fun speak(
        segment: Segment,
        labels: SpeechLabels,
        quotesLeft: Int,
    ): String? =
        when (segment) {
            is RegularTextSegment -> segment.segmentText
            is HashTagSegment -> segment.segmentText
            // Legacy NIP-08 `#[n]` mentions/quotes carry the hex directly.
            is HashIndexUserSegment -> mentionName(segment.hex)
            is HashIndexEventSegment -> quotedNote(segment.hex, labels, quotesLeft)
            // Modern `nostr:` references (npub/nprofile/note/nevent/naddr) arrive as BechSegment.
            is BechSegment -> resolveBech(segment.segmentText, labels, quotesLeft)
            // The base class is emitted for plain whitespace/punctuation runs between words.
            else -> if (segment::class == Segment::class) segment.segmentText else null
        }

    private fun resolveBech(
        word: String,
        labels: SpeechLabels,
        quotesLeft: Int,
    ): String? {
        val entity = Nip19Parser.uriToRoute(word)?.entity ?: return null
        return when (entity) {
            is NPub -> mentionName(entity.hex)
            is NProfile -> mentionName(entity.hex)
            is NNote -> quotedNote(entity.hex, labels, quotesLeft)
            is NEvent -> quotedNote(entity.hex, labels, quotesLeft)
            is NAddress -> quotedNote(entity.aTag(), labels, quotesLeft)
            else -> null
        }
    }

    /** The mentioned user's display name, or null if we don't have their profile cached. */
    private fun mentionName(hex: String): String? = LocalCache.getUserIfExists(hex)?.let { realNameOrNull(it) }

    /** A user's real (metadata) name, never the hex/npub fallback — speaking raw keys is useless. */
    private fun realNameOrNull(user: User): String? = user.metadataOrNull()?.bestName()?.takeIf { it.isNotBlank() }

    private fun quotedNote(
        hex: String,
        labels: SpeechLabels,
        quotesLeft: Int,
    ): String? {
        if (quotesLeft <= 0) return null
        val note = LocalCache.getNoteIfExists(hex) ?: return null
        val inner = buildSpeech(note, labels, quotesLeft - 1)
        return if (inner.isBlank()) null else "${labels.quoteIntro} $inner"
    }

    companion object {
        private val WHITESPACE_RUN = Regex("\\s+")

        // How many levels of quoted-note nesting to read before stopping (1 = read direct quotes).
        private const val QUOTE_DEPTH = 1
    }
}
