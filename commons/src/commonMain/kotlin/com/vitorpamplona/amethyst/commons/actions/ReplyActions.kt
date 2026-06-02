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
package com.vitorpamplona.amethyst.commons.actions

import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip10Notes.tags.notify

/**
 * Pure event-building "verbs" for kind:1 short-note replies (NIP-10).
 *
 * Builds a signed [TextNoteEvent] reply but does NOT publish it. The Amethyst
 * Android UI flow does more than these builders — non-UI callers are
 * responsible for the rest:
 *
 *  * **Publish.** Hand the returned event to your relay client. Android uses
 *    `Account.sendMyPublicAndPrivateOutbox`, the desktop deck pipes through
 *    `dispatch(signed, localCache, relayManager)`, amy uses `Context.publish`.
 *  * **Writeable check.** Skip the call when the active signer is read-only
 *    (e.g. an npub-only login). Building will fail at the sign step otherwise.
 *  * **Parent kind.** Only kind:1 [TextNoteEvent] parents are well-defined here
 *    — replies to articles / comments belong on the NIP-22 path
 *    (`CommentEvent.replyBuilder`). Callers must filter; this signature enforces
 *    it via [EventHintBundle] of `TextNoteEvent`.
 *  * **Local cache update.** If your caller has a local event cache, feed the
 *    new event back in so the UI / next read sees the update without a relay
 *    round-trip.
 *
 * Canonical entry point for non-UI callers — the underlying
 * [TextNoteEvent.build] reply-aware overload handles full NIP-10 tag carry:
 * `marker=root` (parent's root e-tag if present, else parent.id),
 * `marker=reply` (parent.id), the parent's full p-tag chain plus parent.pubKey,
 * and the relay hint from [EventHintBundle].
 */
object ReplyActions {
    /**
     * Build a kind:1 [TextNoteEvent] that replies to [parent], wrapping it with
     * NIP-10-correct marked e-tags and the parent's p-tag chain.
     *
     * Returns the signed event ready to be published. The reply preserves the
     * parent's root reference so conformant clients can reconstruct the thread.
     */
    suspend fun replyTo(
        parent: EventHintBundle<TextNoteEvent>,
        content: String,
        signer: NostrSigner,
    ): TextNoteEvent {
        // Per NIP-10, replies MUST carry the p-tags of the event being replied
        // to plus the author's pubkey. TextNoteEvent.build(replyingTo=) only
        // emits the e-tag chain — p-tag carry is the caller's responsibility.
        val carriedPubKeys =
            (parent.event.linkedPubKeys() + parent.event.pubKey)
                .distinct()
                .map { PTag(it, relayHint = null) }

        val template =
            TextNoteEvent.build(content, replyingTo = parent) {
                notify(carriedPubKeys)
            }
        return signer.sign(template)
    }
}
