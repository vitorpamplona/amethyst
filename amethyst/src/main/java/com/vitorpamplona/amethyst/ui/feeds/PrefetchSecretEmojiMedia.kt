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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.vitorpamplona.amethyst.commons.richtext.BechSegment
import com.vitorpamplona.amethyst.commons.richtext.HashIndexEventSegment
import com.vitorpamplona.amethyst.commons.richtext.RichTextViewerState
import com.vitorpamplona.amethyst.commons.richtext.Segment
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNote
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Warms everything a secret emoji reveals **before** the user taps it.
 *
 * A secret emoji hides an entire post body in its trailing variation selectors: it
 * renders as a single glyph and only expands on tap. Without this, that tap is the
 * first moment anything inside starts loading — images download, quoted events get
 * requested from relays — so the reveal pops in piece by piece.
 *
 * Given the already-decoded-and-parsed [secretContent], this:
 *
 *  1. **Prefetches the hidden body's media** — inline images, video poster frames,
 *     and (for non-media URLs) OpenGraph link previews — through the same
 *     [WarmTargets] pipeline the feed prefetcher uses, so the same data-saver gates
 *     (`showImages()` / `showUrlPreview()`) and aspect-ratio capture apply. Nested
 *     secret emoji inside the hidden body are unwrapped too.
 *  2. **Observes the posts quoted inside it** — `nostr:` bech32 entities and `#[n]`
 *     event tags — via [observeNote], which both subscribes to the relays for a
 *     missing event and watches [LocalCache] for its arrival. The moment the quoted
 *     event lands, *its* media is prefetched as well, so the inline quote card is
 *     drawn complete instead of as an empty placeholder.
 *
 * Call it from wherever a secret emoji is on screen in a preview-capable context —
 * the collapsed glyph in a note body, or a secret emoji used as a reaction. It runs
 * off the main thread and renders nothing.
 *
 * The feed prefetcher covers the same media for notes just outside the viewport
 * (see `WarmTargets.harvest`); this composable covers the on-screen case, and is
 * the only path that can subscribe to relays for the quoted events, since those
 * subscriptions are scoped to what is currently composed.
 */
@Composable
fun PrefetchSecretEmojiMedia(
    secretContent: RichTextViewerState?,
    accountViewModel: AccountViewModel,
) {
    val context = LocalContext.current

    LaunchedEffect(secretContent, accountViewModel) {
        if (secretContent == null) return@LaunchedEffect
        withContext(Dispatchers.Default) {
            WarmTargets().apply { harvest(secretContent) }.warm(context, accountViewModel)
        }
    }

    PrefetchSecretEmojiQuotes(secretContent, accountViewModel)
}

/**
 * Resolves the hidden body's quoted events to [Note]s (creating the placeholder in
 * [LocalCache] when the event hasn't arrived) and keeps one observer per note.
 */
@Composable
private fun PrefetchSecretEmojiQuotes(
    secretContent: RichTextViewerState?,
    accountViewModel: AccountViewModel,
) {
    val quotes = remember(secretContent) { secretContent?.quotedEventSegments() ?: persistentListOf() }

    if (quotes.isEmpty()) return

    val notes by produceState<ImmutableList<Note>>(persistentListOf(), quotes, accountViewModel) {
        value =
            withContext(Dispatchers.IO) {
                quotes.mapNotNull { it.resolveQuotedNote(accountViewModel) }.toImmutableList()
            }
    }

    notes.forEach { note ->
        key(note.idHex) {
            PrefetchQuotedNoteMedia(note, accountViewModel)
        }
    }
}

/**
 * Subscribes to [note] — pulling the event down when it is only a placeholder — and
 * prefetches its media as soon as an event is there, once per event id (a note's
 * state also churns on reactions and zaps, which change nothing we warm).
 */
@Composable
private fun PrefetchQuotedNoteMedia(
    note: Note,
    accountViewModel: AccountViewModel,
) {
    val context = LocalContext.current
    val noteState by observeNote(note, accountViewModel)
    val warmed = remember(note) { mutableSetOf<HexKey>() }

    LaunchedEffect(noteState, accountViewModel) {
        val loaded = noteState.note
        val eventId = loaded.event?.id ?: return@LaunchedEffect
        if (!warmed.add(eventId)) return@LaunchedEffect

        withContext(Dispatchers.Default) {
            loaded.warm(context, accountViewModel)
        }
    }
}

/** The words that render as an inline quote of another post: `nostr:` entities and `#[n]` event tags. */
private fun RichTextViewerState.quotedEventSegments(): ImmutableList<Segment> =
    paragraphs
        .flatMap { paragraph ->
            paragraph.words.filter { it is BechSegment || it is HashIndexEventSegment }
        }.toImmutableList()

/**
 * The [Note] this segment quotes, or null when it points at something that isn't a
 * post (an npub/nprofile, or an unparseable entity). Reuses the same
 * `bechLinkCache` the renderer resolves through, so the tap is a cache hit.
 */
private suspend fun Segment.resolveQuotedNote(accountViewModel: AccountViewModel): Note? =
    when (this) {
        is HashIndexEventSegment -> LocalCache.checkGetOrCreateNote(hex)
        is BechSegment -> accountViewModel.bechLinkCache.update(segmentText)?.baseNote
        else -> null
    }
