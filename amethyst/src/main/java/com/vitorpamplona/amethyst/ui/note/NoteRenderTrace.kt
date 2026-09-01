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
package com.vitorpamplona.amethyst.ui.note

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.tracing.Trace
import com.vitorpamplona.amethyst.BuildConfig
import com.vitorpamplona.amethyst.model.ReactionRowAction

/**
 * Composition-time trace markers for the feed's note card.
 *
 * Compose runs a composable's body synchronously on the composing thread, so a
 * `beginSection`/`endSection` pair wrapped around a `content()` call measures the
 * **composition** cost of that subtree — deliberately excluding the later measure,
 * layout and draw phases, which is what makes this useful for separating "building
 * the tree" from "drawing it".
 *
 * Gated on [BuildConfig.TRACE_NOTE_RENDER], `true` only in the `benchmark` build type, so
 * the sections never execute in `debug` or `release`.
 *
 * They are **not stripped**, though — verified by grepping the R8'd release DEX, which
 * still contains the marker strings, this file's classes and a reference to
 * `androidx.tracing.Trace`. R8 cannot prove the branch dead, most likely because this is
 * a `@Composable inline` function the Compose plugin rewrites before R8 sees it. Runtime
 * cost in a shipped build is nil; APK footprint is not zero. Before this lands on `main`,
 * move the tracer behind a source-set split (no-op for debug/release, real one only in
 * `benchmark`).
 *
 * Section names are read back by `TraceSectionMetric` in
 * `:macrobenchmark`'s `FeedScrollBenchmark`, so they must stay in sync with the names
 * listed there.
 */
@Composable
inline fun TracedComposition(
    name: String,
    content: @Composable () -> Unit,
) {
    if (!BuildConfig.TRACE_NOTE_RENDER) {
        content()
        return
    }

    // No try/finally: the Compose compiler rejects a try/catch around a composable call.
    // An exception thrown during composition aborts the frame regardless, so an unbalanced
    // section in that case is not a concern.
    Trace.beginSection(name)
    content()
    Trace.endSection()
}

/**
 * Draw-phase counterpart of [TracedComposition].
 *
 * Composition markers cannot see the draw phase at all, and on a slow device draw is roughly three
 * times the whole layout phase. `drawWithContent` wraps the subtree's paint work, so the section
 * measures exactly the time spent rasterising that part of the card.
 *
 * Deliberately attached to modifiers that already exist on real composables rather than to new
 * wrapper `Box`es: adding a node would change the layout being measured. Benchmark builds only —
 * elsewhere the constant folds and this returns the receiver untouched.
 */
fun Modifier.tracedDraw(name: String): Modifier =
    if (!BuildConfig.TRACE_NOTE_RENDER) {
        this
    } else {
        drawWithContent {
            Trace.beginSection(name)
            drawContent()
            Trace.endSection()
        }
    }

/** Section names. Kept as constants so R8 cannot rewrite them apart from the metric list. */
object NoteTrace {
    const val CARD = "Amethyst:NoteCard"
    const val WATCH_EVENT = "Amethyst:WatchNoteEvent"
    const val HIDDEN_CHECK = "Amethyst:HiddenCheck"
    const val BG_COLOR = "Amethyst:BackgroundColor"
    const val AUTHOR_IMAGES = "Amethyst:AuthorImages"
    const val FIRST_ROW = "Amethyst:FirstUserInfoRow"
    const val SECOND_ROW = "Amethyst:SecondUserInfoRow"
    const val CONTENT = "Amethyst:NoteContent"
    const val REACTIONS = "Amethyst:ReactionsRow"
    const val DISPATCH = "Amethyst:KindDispatch"

    // Draw-phase sections (see Modifier.tracedDraw).
    const val DRAW_REACTIONS = "Amethyst:DrawReactions"
    const val DRAW_AUTHOR = "Amethyst:DrawAuthor"
    const val DRAW_FIRST_ROW = "Amethyst:DrawFirstRow"
    const val DRAW_RICHTEXT = "Amethyst:DrawRichText"

    /** Fires only when a feed icon actually used the shared painter — proves the fix is live. */
    const val SHARED_PAINTER = "Amethyst:SharedPainter"

    // Drill-down inside ReactionsRow, the single most expensive slot of the card.
    const val RX_INDICATORS = "Amethyst:RxIndicators"
    const val RX_ZAPRAISER = "Amethyst:RxZapraiser"
    const val RX_REPLY = "Amethyst:RxReply"
    const val RX_BOOST = "Amethyst:RxBoost"
    const val RX_LIKE = "Amethyst:RxLike"
    const val RX_ZAP = "Amethyst:RxZap"
    const val RX_SHARE = "Amethyst:RxShare"
    const val RX_PAY = "Amethyst:RxPay"

    // Drill-down inside NoteContent for a plain text note (the common feed case).
    const val TXT_REPLY = "Amethyst:TxtReplyPreview"
    const val TXT_RICHTEXT = "Amethyst:TxtRichText"
    const val TXT_HASHTAGS = "Amethyst:TxtHashtags"

    /** Section name for one reaction-row button. */
    fun forAction(action: ReactionRowAction) =
        when (action) {
            ReactionRowAction.Reply -> RX_REPLY
            ReactionRowAction.Boost -> RX_BOOST
            ReactionRowAction.Like -> RX_LIKE
            ReactionRowAction.Zap -> RX_ZAP
            ReactionRowAction.Share -> RX_SHARE
            ReactionRowAction.Pay -> RX_PAY
        }
}
