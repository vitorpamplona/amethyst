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
package com.vitorpamplona.amethyst.ui.actions

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer

/**
 * Rejects any edit that partially modifies a previously-complete Nostr mention
 * (`@npub1…`, `nostr:npub1…`, `@nprofile1…`, `nostr:nprofile1…`).
 *
 * Background: when an `OutputTransformation` collapses an underlying npub into a
 * short `@DisplayName`, Compose's auto-derived offset mapping uses identity inside
 * the wedge. Some IMEs — notably Microsoft SwiftKey — re-enter "word edit mode"
 * over a previously-committed display token after autocorrect-on-space, then issue
 * `setComposingText` with a shortened version. Because the mapping is identity
 * inside the wedge, the IME's replacement only overwrites the leading characters
 * of the underlying bech32, leaving an orphan tail that no longer matches the
 * mention regex. The wedge collapses, the orphan bech32 becomes visible, and the
 * cursor lands in the middle of it. Gboard never enters this state because it
 * does not recompose previously-committed tokens.
 *
 * This guard runs on every input change. If the change's original-text range
 * partially intersects a complete mention but does not fully cover it, the entire
 * change is reverted. The mention stays atomic; the IME re-reads the unchanged
 * buffer and moves on.
 */
object MentionPreservingInputTransformation : InputTransformation {
    private val mentionRegex =
        Regex("(?:@|nostr:)(?:npub1[a-z0-9]{58}|nprofile1[a-z0-9]+)")

    override fun TextFieldBuffer.transformInput() {
        val changeCount = changes.changeCount
        if (changeCount == 0) return

        val original = originalText.toString()
        if (original.isEmpty()) return

        val mentions = mentionRegex.findAll(original).toList()
        if (mentions.isEmpty()) return

        for (i in 0 until changeCount) {
            val origRange = changes.getOriginalRange(i)
            val origStart = origRange.min
            val origEnd = origRange.max
            for (mention in mentions) {
                val mStart = mention.range.first
                val mEndExclusive = mention.range.last + 1
                val overlaps = origStart < mEndExclusive && origEnd > mStart
                val fullyCovers = origStart <= mStart && origEnd >= mEndExclusive
                if (overlaps && !fullyCovers) {
                    revertAllChanges()
                    return
                }
            }
        }
    }
}
