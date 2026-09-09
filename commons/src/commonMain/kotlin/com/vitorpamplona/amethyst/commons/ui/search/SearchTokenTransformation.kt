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
package com.vitorpamplona.amethyst.commons.ui.search

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.vitorpamplona.amethyst.commons.search.KindRegistry
import com.vitorpamplona.amethyst.commons.search.SearchSegment
import com.vitorpamplona.amethyst.commons.search.SearchTokenizer
import com.vitorpamplona.amethyst.commons.search.rawText

/**
 * One rewrite of a stretch of the typed text into what is drawn over it.
 *
 * [original] is the span in the value the reader is actually editing; [replacement] is what the
 * field shows there. They are usually the same string — a hashtag draws as itself, in colour —
 * and differ only where the typed form is unreadable: a 63-character npub draws as its owner's
 * name, a NIP-19 pointer as a short form.
 */
@Immutable
private data class Rewrite(
    val start: Int,
    val end: Int,
    val replacement: String,
)

/**
 * Maps caret offsets across a list of non-overlapping replacements, in both directions.
 *
 * This is the whole reason a chip can shorten an npub without breaking the field. Compose asks
 * this mapping where the caret goes on every keystroke, every selection and every click, and an
 * answer outside the transformed string crashes the text field — so each direction clamps to the
 * span it lands in rather than interpolating inside a replacement, which would put the caret in
 * the middle of a name that has no middle in the underlying text.
 */
private class RewriteOffsetMapping(
    private val rewrites: List<Rewrite>,
    private val originalLength: Int,
    private val transformedLength: Int,
) : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int {
        val at = offset.coerceIn(0, originalLength)
        var shift = 0
        rewrites.forEach { r ->
            when {
                // Wholly before the caret: its full change in length applies.
                r.end <= at -> shift += r.replacement.length - (r.end - r.start)
                // Inside a rewrite: the caret goes to its far end, never into the middle of a
                // name whose characters do not exist in the value.
                r.start < at -> return (r.start + shift + r.replacement.length).coerceIn(0, transformedLength)
            }
        }
        return (at + shift).coerceIn(0, transformedLength)
    }

    override fun transformedToOriginal(offset: Int): Int {
        val at = offset.coerceIn(0, transformedLength)
        var shift = 0
        rewrites.forEach { r ->
            val drawnStart = r.start + shift
            val drawnEnd = drawnStart + r.replacement.length
            when {
                drawnEnd <= at -> shift += r.replacement.length - (r.end - r.start)
                drawnStart < at -> return r.end.coerceIn(0, originalLength)
            }
        }
        return (at - shift).coerceIn(0, originalLength)
    }
}

/**
 * Draws the tokens the search language holds as chips, over the plain text that stays the field's
 * real value.
 *
 * The value is never rewritten — every rendering is a view of it — so undo, IME composition, text
 * selection and a paste all keep working on exactly what the reader typed, and copying the field
 * yields a query that can be pasted back.
 *
 * A token only settles into a chip once the caret has left it: [caret] is passed straight through
 * to [SearchTokenizer.drawable], so a half-typed `#bit` stays plain text under the cursor rather
 * than reflowing on every keystroke. Pass null for a field nobody is typing in.
 *
 * [displayName] is asked for a key token's owner and [groupName] for a group's id; returning null
 * leaves the token short-formed rather than named, which is what something that has not arrived
 * yet should look like — an invented name would be worse than a visible id.
 */
@Immutable
class SearchTokenTransformation(
    private val caret: Int?,
    private val styles: SearchTokenStyles,
    private val displayName: (String) -> String?,
    private val groupName: (String) -> String? = { null },
    /**
     * A NIP-73 scope's human name, given its field and value — a `geo:` geohash's city, say.
     * Synchronous by contract: this runs inside a text transformation, so it may only read what
     * is already resolved and must return null rather than wait for anything.
     */
    private val scopeName: (String, String) -> String? = { _, _ -> null },
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val segments = SearchTokenizer.drawable(text.text, caret)
        val rewrites = mutableListOf<Rewrite>()
        val builder = AnnotatedString.Builder()
        var at = 0

        segments.forEach { seg ->
            val raw = seg.rawText
            val start = at
            at += raw.length
            val replacement = drawnForm(seg, raw)
            // Only a length change needs a mapping entry; a same-length restyle leaves offsets alone.
            if (replacement.length != raw.length) rewrites.add(Rewrite(start, at, replacement))
            val from = builder.length
            builder.append(replacement)
            styles.styleFor(seg)?.let { builder.addStyle(it, from, builder.length) }
        }

        return TransformedText(
            builder.toAnnotatedString(),
            RewriteOffsetMapping(rewrites, originalLength = text.text.length, transformedLength = builder.length),
        )
    }

    /** What a segment draws as. Everything but a key and a pointer draws as it was typed. */
    private fun drawnForm(
        seg: SearchSegment,
        raw: String,
    ): String =
        when (seg) {
            is SearchSegment.Key -> {
                val name = displayName(seg.pubkey)
                val shown = name?.let { clip(it, 32) } ?: shortBech32(rawKeyOf(raw))
                seg.field?.let { "${it.token}:$shown" } ?: shown
            }

            is SearchSegment.Pointer -> "to:${shortBech32(raw.substringAfter(':'))}"
            // A group id is a stranger's opaque string; its name is the only part a reader can
            // check against the room they meant. The id stays the value, so the query is unchanged.
            is SearchSegment.Group -> "group:${groupName(seg.id)?.let { clip(it, 32) } ?: seg.id}"
            // Likewise a geohash: "9q8yy" says nothing, "San Francisco" says what was filtered on.
            is SearchSegment.Scope -> scopeName(seg.field, seg.value)?.let { "${seg.field}:${clip(it, 32)}" } ?: raw
            // A kind typed as a number draws under the name the registry has for it, so a screen
            // that seeds `kind:20` shows "kind:picture" — the only form a reader can check. Only
            // an exact one-token match is used: `kind:30312` must not draw as the wider `live`.
            is SearchSegment.Kind -> "kind:${seg.pseudoKind ?: KindRegistry.tokenize(seg.kinds).singleOrNull() ?: seg.alias}"
            else -> raw
        }

    /** The bech32 half of a `from:`/`to:` key token, or the whole thing when it is a bare npub. */
    private fun rawKeyOf(raw: String) = raw.substringAfter(':', raw)

    private fun shortBech32(value: String) = if (value.length <= 16) value else value.take(10) + "…" + value.takeLast(5)

    private fun clip(
        value: String,
        max: Int,
    ) = if (value.length <= max) value else value.take(max - 1) + "…"
}
