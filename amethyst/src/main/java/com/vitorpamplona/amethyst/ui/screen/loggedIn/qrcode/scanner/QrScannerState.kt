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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.scanner

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Everything the scanner UI draws, and the decision of what a frame means.
 *
 * Kept out of the composable so the accept/dedupe/multi-code rules are one readable block rather
 * than conditions scattered through a camera callback.
 */
@Stable
class QrScannerState {
    private val sequence = StructuredAppendAccumulator()

    /** The size of the image the decoder saw, for mapping [ScanResult.bounds] onto the preview. */
    var frame by mutableStateOf(ScanFrame(0, 0))
        private set

    /**
     * Codes currently visible. One entry means we take it; several mean we draw them all and wait
     * for a tap, because silently picking one of several codes is how you scan the poster next to
     * the one you meant.
     */
    var candidates by mutableStateOf<List<ScanResult>>(emptyList())
        private set

    /** Set while a Structured Append payload is half-captured: captured count to total. */
    var sequenceProgress by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    /** True when the scene has been too dark for [DARK_DWELL_MS]; the UI offers the torch. */
    var isDark by mutableStateOf(false)
        private set

    var torchOn by mutableStateOf(false)
    var torchAvailable by mutableStateOf(false)

    var zoomRatio by mutableFloatStateOf(1f)
    var maxZoomRatio by mutableFloatStateOf(1f)

    /**
     * Auto-zoom stops for good the first time the user pinches. Someone who has framed the shot
     * themselves does not want the camera arguing about it.
     */
    var autoZoomEnabled by mutableStateOf(true)
        private set

    /** A payload we decoded but the caller could not use; drives the explain-what-happened sheet. */
    var rejected by mutableStateOf<ScannedPayload?>(null)

    /** A transient message (no code in that picture, empty clipboard, decoder unavailable). */
    var notice by mutableStateOf<String?>(null)

    private var lastSubmittedText: String? = null
    private var lastSubmittedAt = 0L
    private var darkSinceMs = 0L

    /** Milliseconds since anything at all was decoded — what auto-zoom watches. */
    var msSinceLastDetection by mutableLongStateOf(0L)
        private set

    private var lastDetectionMs = 0L

    fun onPinch() {
        autoZoomEnabled = false
    }

    fun resetZoom() {
        zoomRatio = 1f
    }

    /**
     * Folds one analysed frame into the UI state and decides whether we have an answer.
     *
     * Returns the payload to hand back to the caller, or null to keep scanning. Returning null is
     * the normal case — nothing in frame, several codes in frame, a half-finished multi-part
     * code, or the same code we just submitted.
     */
    fun onFrame(
        scan: FrameScan,
        nowMs: Long,
    ): String? {
        frame = scan.frame
        updateDarkness(scan.brightness, nowMs)

        // Checked on every frame, not just on empty ones: an abandoned half-capture is abandoned
        // whether or not the camera is busy reading something else.
        if (sequence.dropIfStale(nowMs)) sequenceProgress = null

        // Seed the clock on the first frame. Left at zero, the very first empty frame would read
        // as "nothing decoded since the epoch" and send auto-zoom hunting before the user has had
        // a chance to aim.
        if (lastDetectionMs == 0L) lastDetectionMs = nowMs

        // Nothing is decided while the "we can't open this" sheet is up: the user is reading it,
        // and the offending code is very probably still sitting in front of the lens.
        if (rejected != null) {
            candidates = emptyList()
            return null
        }

        val found = scan.results.distinctBy { it.text }
        if (found.isEmpty()) {
            candidates = emptyList()
            msSinceLastDetection = nowMs - lastDetectionMs
            return null
        }

        lastDetectionMs = nowMs
        msSinceLastDetection = 0
        candidates = found

        // A multi-part code is never complete on its first part, so it can't be a single answer.
        found.firstOrNull { it.isPartOfSequence }?.let { part ->
            val joined = sequence.add(part, nowMs)
            sequenceProgress = if (joined == null) sequence.captured to sequence.total else null
            return joined?.let { accept(it, nowMs) }
        }

        if (found.size > 1) return null

        return accept(found.first().text, nowMs)
    }

    /** Taking one of several visible codes, because the user tapped it. */
    fun onCandidateTapped(
        result: ScanResult,
        nowMs: Long,
    ): String? {
        candidates = emptyList()
        // Deliberately bypasses the dedupe window. That window exists to stop ONE code decoding
        // thirty times a second from firing the caller thirty times; a tap is one decision by a
        // person, and swallowing it makes the highlight a target that can be tapped with nothing
        // happening. The latch is still armed, so the camera frames that follow -- the tapped
        // code is very probably still in view -- do not fire it again.
        lastSubmittedText = result.text
        lastSubmittedAt = nowMs
        return result.text
    }

    /**
     * Debounced hand-off.
     *
     * A code held in front of the lens decodes ~30 times a second. Without this the caller's
     * handler fires 30 times, which for a navigation target means 30 stacked screens.
     */
    private fun accept(
        text: String,
        nowMs: Long,
    ): String? {
        if (text == lastSubmittedText && nowMs - lastSubmittedAt < DEDUPE_MS) return null
        lastSubmittedText = text
        lastSubmittedAt = nowMs
        return text
    }

    /** Called when the caller rejects a payload, so the same code does not re-fire immediately. */
    fun onRejected(payload: ScannedPayload) {
        rejected = payload
        candidates = emptyList()
    }

    /**
     * Dismissing the sheet resumes scanning.
     *
     * The dedupe latch is deliberately LEFT in place. Clearing it - so the user could retry the
     * very same code - meant that the offending code, still sitting in front of the lens, decoded
     * again on the next frame and re-opened the sheet immediately: "Scan again" became a button
     * that could not be escaped. Keeping the latch lets the camera run; pointing at the same code
     * again after [DEDUPE_MS] still re-triggers it, which is the retry that was actually wanted.
     */
    fun dismissRejection(nowMs: Long) {
        rejected = null
        // Restart the latch from now, so the grace period is measured from when the user dismissed
        // the sheet rather than from when the code was first read.
        lastSubmittedAt = nowMs
    }

    private fun updateDarkness(
        brightness: Float,
        nowMs: Long,
    ) {
        if (brightness > DARK_THRESHOLD) {
            darkSinceMs = 0
            isDark = false
            return
        }
        if (darkSinceMs == 0L) darkSinceMs = nowMs
        isDark = nowMs - darkSinceMs >= DARK_DWELL_MS
    }

    companion object {
        /** Long enough that one steady code fires once; short enough to rescan on purpose. */
        const val DEDUPE_MS = 1_500L

        /** Mean luminance below this reads as "the torch would help". */
        const val DARK_THRESHOLD = 0.18f

        /** Don't offer the torch for a thumb over the lens or a moment of shadow. */
        const val DARK_DWELL_MS = 1_000L

        /** How long with nothing decoded before auto-zoom starts hunting. */
        const val AUTO_ZOOM_AFTER_MS = 1_200L

        /** Ceiling for the auto-zoom sweep — past this, focus and shake beat the extra reach. */
        const val AUTO_ZOOM_MAX = 2.5f
    }
}
