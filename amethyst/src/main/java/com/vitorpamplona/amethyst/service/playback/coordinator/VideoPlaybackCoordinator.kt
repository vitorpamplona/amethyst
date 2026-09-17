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
package com.vitorpamplona.amethyst.service.playback.coordinator

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What a slot needs a player for. Carries only what the pool keys a checkout on. */
data class VideoRequest(
    val proxyPort: Int?,
    val mediaId: String,
    val repeatMode: Boolean,
)

/**
 * Decides who owns a checked-out video player, and therefore who gives it back.
 *
 * Two kinds of holder exist and they overlap:
 *
 *  - a **slot** — one on-screen video, alive for as long as its composable is;
 *  - the **promotion** — the single playback the user detached from the feed (picture-in-picture),
 *    which by design outlives the composable that started it.
 *
 * A checkout goes back to the pool exactly when *neither* holds it. That one rule replaces the
 * hand-off bookkeeping that used to live inside the Compose producer, where it was both easy to get
 * wrong and impossible to test: releasing twice under-counts the decoder budget and lets the app
 * hand out more concurrent MediaCodec instances than the device grants (surfacing as NO_MEMORY and
 * "can't play this video"), while never releasing leaks one for the life of the process.
 *
 * Because a promotion does not evict the slot's own claim, demoting while the video is still on
 * screen needs no re-acquire at all — the slot never stopped holding it, so the video drops back
 * inline with its buffer, position and decoder untouched.
 *
 * Generic over the checkout type so the rules are unit-testable with no media3 or Android objects
 * involved. Not thread safe: every caller runs on the main thread.
 */
class VideoPlaybackCoordinator<C : Any>(
    private val acquire: (VideoRequest) -> C,
    private val release: (C) -> Unit,
) {
    private val slots = mutableMapOf<Any, C>()
    private val _promoted = MutableStateFlow<C?>(null)

    /** The promoted checkout, or null. Observed by the media session host and by the feed. */
    val promoted: StateFlow<C?> = _promoted.asStateFlow()

    /**
     * Checks a player out for [slot], or returns the one it already holds. The caller must use a
     * fresh slot key per [VideoRequest] — a slot is a place on screen showing one specific video,
     * not a place on screen.
     */
    fun attach(
        slot: Any,
        request: VideoRequest,
    ): C = slots.getOrPut(slot) { acquire(request) }

    /** The slot is gone. Releases its checkout unless the promotion is also holding it. */
    fun detach(slot: Any) {
        val checkout = slots.remove(slot) ?: return
        releaseIfUnheld(checkout)
    }

    /**
     * Hands [checkout] the system media surface. Any playback it displaces is released here if no
     * slot is still showing it — the composable that acquired it has already let go, so this is the
     * only remaining owner.
     */
    fun promote(checkout: C) {
        val previous = _promoted.value
        if (previous === checkout) return
        _promoted.value = checkout
        previous?.let { releaseIfUnheld(it) }
    }

    /**
     * Promotes a playback that no slot is showing — the picture-in-picture window reopening after
     * the process that promoted it was reclaimed.
     */
    fun promoteDetached(request: VideoRequest): C {
        val checkout = acquire(request)
        promote(checkout)
        return checkout
    }

    /** Gives the promotion up. Releases unless a slot is still showing it. */
    fun demote() {
        val checkout = _promoted.value ?: return
        _promoted.value = null
        releaseIfUnheld(checkout)
    }

    /** Gives the promotion up only if the promoted checkout matches. */
    fun demoteIf(predicate: (C) -> Boolean) {
        val checkout = _promoted.value ?: return
        if (predicate(checkout)) demote()
    }

    fun isPromoted(checkout: C?): Boolean = checkout != null && _promoted.value === checkout

    /** Teardown. Every live checkout goes back exactly once, however many holders it had. */
    fun releaseAll() {
        val all = ArrayList<C>()
        _promoted.value?.let { all.add(it) }
        slots.values.forEach { checkout -> if (all.none { it === checkout }) all.add(checkout) }

        slots.clear()
        _promoted.value = null

        all.forEach(release)
    }

    private fun releaseIfUnheld(checkout: C) {
        if (_promoted.value === checkout) return
        if (slots.values.any { it === checkout }) return
        release(checkout)
    }
}
