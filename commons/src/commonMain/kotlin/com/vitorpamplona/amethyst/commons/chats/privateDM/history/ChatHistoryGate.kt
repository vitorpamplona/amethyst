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
package com.vitorpamplona.amethyst.commons.chats.privateDM.history

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where one open conversation stands on loading its older NIP-17 history. See [ChatHistoryGate]. */
enum class ChatHistoryPhase {
    // Nothing to say: the markers aren't in view, or the last page brought this chat something.
    IDLE,

    // One automatic page is in flight from every relay because the reader scrolled up to the markers.
    AUTO,

    // The automatic page brought nothing for this chat. Offers "Keep looking".
    BUTTON,

    // "Keep looking" was pressed: paging every relay, round after round, until a message for this chat
    // turns up or nothing more is reachable. Offers "Stop".
    SEARCH,

    // Nothing more is reachable: every relay is done or stalled.
    END,

    // The chat opened with the markers already in view (a short chat). Nothing is fetched until
    // "Continue" or until the reader scrolls away and back.
    PAUSED,
}

/**
 * Decides when an open conversation pulls more of the account's NIP-17 history.
 *
 * Gift wraps only name their recipient, so a chat's older messages come from the same account-wide
 * pages as every other chat's: one page may bring a week of other people's messages and nothing for this
 * one. Paging "while the marker is on screen" therefore walks the whole inbox — every gift wrap the
 * account ever received — whenever a quiet chat leaves its marker in view. Instead, when the reader
 * scrolls up to the relay markers:
 *
 *  1. load ONE page from every relay automatically ([ChatHistoryPhase.AUTO]);
 *  2. if that brought something for this chat, the markers move above it and the next time the reader
 *     reaches them loads another ([ChatHistoryPhase.IDLE]);
 *  3. if it brought nothing, stop and offer "Keep looking" ([ChatHistoryPhase.BUTTON]), which pages every
 *     relay, round after round, until a message for this chat appears or nothing more is reachable
 *     ([ChatHistoryPhase.SEARCH] → [ChatHistoryPhase.END]).
 *
 * A chat that opens with the markers already in view starts [ChatHistoryPhase.PAUSED] and fetches nothing
 * until [resume], so flipping between short chats doesn't pull a page from every relay each time.
 *
 * Ported from Brainstorm-UI's `useChatHistory` (which in turn runs on a port of [BackwardRelayPager]).
 * One instance per open conversation; drive it from one thread (the UI's) by calling [update] whenever
 * any input changes.
 *
 * @param advanceAll steps every relay that can take one a page; true if any did.
 */
class ChatHistoryGate(
    private val advanceAll: () -> Boolean,
) {
    private val _phase = MutableStateFlow(ChatHistoryPhase.IDLE)
    val phase: StateFlow<ChatHistoryPhase> = _phase.asStateFlow()

    // Message count when the current page(s) started: more than this means a page brought this chat something.
    private var baseline = 0

    // The markers have been out of view (or Continue was pressed), so seeing them now means the reader
    // went looking for older messages.
    private var armed = false

    private var last = Inputs(markersVisible = null, busy = false, open = true, count = 0)

    /**
     * @param markersVisible whether any of the history markers is on screen; null until they've reported.
     * @param busy a page is in flight (or its events are still being opened).
     * @param open some relay can still page: not done and not stalled.
     * @param count messages in this chat that the paged protocol can bring (NIP-17 ones).
     */
    fun update(
        markersVisible: Boolean?,
        busy: Boolean,
        open: Boolean,
        count: Int,
    ) {
        last = Inputs(markersVisible, busy, open, count)
        if (markersVisible == false) armed = true
        step()
    }

    /** Page every relay, round after round, until a message for this chat turns up. */
    fun keepLooking() {
        baseline = last.count
        _phase.value = ChatHistoryPhase.SEARCH
        if (!last.busy) advanceAll()
    }

    /** Stop a [keepLooking] search; it can be picked up again with "Keep looking". */
    fun stop() {
        _phase.value = ChatHistoryPhase.BUTTON
    }

    /** "Continue" from [ChatHistoryPhase.PAUSED]: load a page now, and keep paging as the reader scrolls. */
    fun resume() {
        armed = true
        baseline = last.count
        // No step() here: the inputs still say "not busy" until the pager's flows catch up, which would
        // read the page as already settled and empty. The next [update] carries the in-flight state.
        _phase.value = if (advanceAll()) ChatHistoryPhase.AUTO else ChatHistoryPhase.IDLE
    }

    private fun step() {
        val (visible, busy, open, count) = last
        val exhausted = !open && !busy
        when (_phase.value) {
            ChatHistoryPhase.IDLE -> {
                if (exhausted) {
                    end(count)
                } else if (visible == true && !busy) {
                    if (!armed) {
                        _phase.value = ChatHistoryPhase.PAUSED
                    } else {
                        baseline = count
                        if (advanceAll()) _phase.value = ChatHistoryPhase.AUTO
                    }
                }
            }

            ChatHistoryPhase.PAUSED -> {
                // Waits for Continue, or for the reader to scroll away — after which scrolling back is a request.
                if (exhausted) {
                    end(count)
                } else if (armed) {
                    _phase.value = ChatHistoryPhase.IDLE
                    step()
                }
            }

            ChatHistoryPhase.AUTO -> {
                if (busy) return
                _phase.value =
                    when {
                        count > baseline -> ChatHistoryPhase.IDLE
                        open -> ChatHistoryPhase.BUTTON
                        else -> ChatHistoryPhase.END.also { baseline = count }
                    }
            }

            ChatHistoryPhase.BUTTON -> {
                // Messages from the last page can still be decrypting when it settles: if they turn out to
                // be for this chat, the offer to keep looking was premature.
                if (count > baseline) {
                    _phase.value = ChatHistoryPhase.IDLE
                } else if (exhausted) {
                    end(count)
                }
            }

            ChatHistoryPhase.SEARCH -> {
                if (count > baseline) {
                    _phase.value = ChatHistoryPhase.IDLE
                } else if (exhausted) {
                    end(count)
                } else if (!busy) {
                    advanceAll()
                }
            }

            ChatHistoryPhase.END -> {
                // A stalled relay came back (or a pruned band reopened a done one).
                if (open) _phase.value = if (count > baseline) ChatHistoryPhase.IDLE else ChatHistoryPhase.BUTTON
            }
        }
    }

    // Nothing more is reachable. Messages counted from here on are what a relay coming back brings.
    private fun end(count: Int) {
        baseline = count
        _phase.value = ChatHistoryPhase.END
    }

    private data class Inputs(
        val markersVisible: Boolean?,
        val busy: Boolean,
        val open: Boolean,
        val count: Int,
    )
}
