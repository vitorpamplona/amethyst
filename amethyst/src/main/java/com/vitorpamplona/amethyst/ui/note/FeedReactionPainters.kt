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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.vitorpamplona.amethyst.commons.icons.Like
import com.vitorpamplona.amethyst.commons.icons.Reply
import com.vitorpamplona.amethyst.commons.icons.Reposted

/**
 * Vector painters for the three [ImageVector][androidx.compose.ui.graphics.vector.ImageVector]
 * icons a feed note's reaction row draws, created once and shared by every card on screen.
 *
 * `Icon(imageVector = …)` calls `rememberVectorPainter` internally, so each call site builds its
 * own [VectorPainter], and a painter rasterises its vector into a cached graphics layer **per
 * instance**. A feed therefore re-rasterised the same three glyphs once for every card scrolled in
 * — measured at roughly 1.8 ms of draw per card, the single largest draw cost in the note.
 *
 * Sharing is safe here because `Icon` applies `tint` as a draw-time `ColorFilter` rather than
 * baking it into the painter, so one painter serves the tinted and untinted states alike.
 *
 * **Scoped deliberately to the feed row.** A [VectorPainter] caches its raster by draw size, so the
 * same instance drawn at two sizes in one frame would re-rasterise on every draw and end up slower
 * than not sharing at all. These icons appear at 18/19/20/28 dp in different screens
 * (`UserReactionsRow`, `MultiSetCompose`, the reaction gallery); this local is provided only around
 * the feed, where each icon has exactly one size, so those other call sites keep their own painters
 * and cannot thrash this cache.
 */
@Immutable
class FeedReactionPainters(
    val reply: VectorPainter,
    val reposted: VectorPainter,
    val like: VectorPainter,
)

/** Null when no feed provided painters — call sites then fall back to their own, as before. */
val LocalFeedReactionPainters = staticCompositionLocalOf<FeedReactionPainters?> { null }

@Composable
fun rememberFeedReactionPainters(): FeedReactionPainters =
    FeedReactionPainters(
        reply = rememberVectorPainter(Reply),
        reposted = rememberVectorPainter(Reposted),
        like = rememberVectorPainter(Like),
    )
