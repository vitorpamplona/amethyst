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
package com.vitorpamplona.quartz.cordn.spec02Envelopes

/**
 * The `kind` values a cordn chat uses, and what they mean.
 *
 * `spec/02.md` §6 names three of these (9 chat, 1111 threaded reply, 7
 * reaction) as conventions to reuse "when their semantics match", and then says
 * plainly that there is **no required set**. Edits, deletions and pins are
 * therefore not protocol at all — they are application conventions, and the
 * reference client picked numbers for them.
 *
 * **We follow cordn-web.** Being the second implementation of an unspecified
 * convention is how it becomes a specification; inventing a third numbering
 * would leave two clients that silently no-op on each other's edits. The cost
 * is that these differ from Marmot's, which is fine — the two protocols do not
 * interoperate and each carries its own table:
 *
 * | Purpose | cordn (here) | Marmot |
 * | ------- | ------------ | ------ |
 * | Chat | 9 | 9 |
 * | Threaded reply | 1111 | 1111 |
 * | Reaction | 7 | 7 |
 * | Edit | **1010** | 1009 |
 * | Deletion | 5 | — |
 * | Pin | **1011** | — |
 * | System row | derived, never sent | 1210, on the wire |
 *
 * Worth asking upstream to write the last three down.
 */
object CordnMessageKinds {
    /** NIP-C7 chat message. `spec/02.md` §6. */
    const val TEXT = 9

    /** NIP-22 comment. §6 prefers this over NIP-C7's reply convention. */
    const val THREAD_REPLY = 1111

    /** NIP-25 reaction; `content` is the emoji. §6. */
    const val REACTION = 7

    /** In-place replacement of a message's text. Not in any spec. */
    const val EDIT = 1010

    /** NIP-09-shaped deletion of one message. Not in any spec. */
    const val DELETION = 5

    /** Group-scoped pin/unpin, carrying an `op` tag. Not in any spec. */
    const val PIN = 1011

    /**
     * A row derived from an MLS Commit — "X added Y", "the name changed".
     *
     * Negative because it is **not a wire kind**: nothing sends it, and nothing
     * should. cordn-web derives these client-side from the Commits it applies,
     * and that is strictly better than Marmot's on-the-wire kind 1210 for one
     * reason — a derived row cannot disagree with the MLS state it describes,
     * because it *is* that state. It also costs no bytes and cannot be forged
     * by a member who simply sends one.
     */
    const val SYSTEM = -1

    /**
     * Kinds that modify another message rather than being one.
     *
     * An annotation never gets its own row in the stream; it is folded into its
     * target by [CordnAnnotationIndex]. Getting this set wrong shows up as
     * reactions rendering as blank messages.
     */
    fun isAnnotation(kind: Int): Boolean = kind == REACTION || kind == EDIT || kind == DELETION || kind == PIN

    fun isSystem(kind: Int): Boolean = kind == SYSTEM
}
