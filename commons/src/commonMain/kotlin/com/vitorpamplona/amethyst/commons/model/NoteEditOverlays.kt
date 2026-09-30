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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.quartz.buzz.stream.StreamMessageEditEvent
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChatEditEvent
import com.vitorpamplona.quartz.experimental.edits.TextNoteModificationEvent
import com.vitorpamplona.quartz.marmot.foundation.appEvents.MarmotAppEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip40Expiration.isExpirationBefore
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.TimeUtils

/*
 * Per-kind resolution of a message's edit overlay from its own `Note.edits`. Every edit that
 * targets a note is held there as a hard-referenced child (like a reaction), so these are cheap
 * in-memory folds — no cache scan, no LocalCache state involved, which is why they live on the
 * note rather than the cache.
 *
 * Every kind applies ONLY edits the message's author was entitled to make, checked here rather
 * than trusted to the relay (Buzz) or an encrypted-plane peer (Concord, Marmot), so a
 * foreign-authored edit never rewrites your message. For Concord, Marmot and kind-1010 that is the
 * note's own author. Buzz also lets the owner of an agent edit the agent's messages, and credits a
 * relay-signed message to the member it names ([buzzEffectiveAuthor], [buzzEditorsOf]).
 */

/**
 * Every kind-1010 post modification of this note, oldest first (author-only, dropping NIP-40
 * expired ones). A list because the post's EditState cycles through the original + each version.
 */
fun Note.textNoteModifications(): List<Note> {
    val noteAuthor = author ?: return emptyList()
    val now = TimeUtils.now()
    return edits
        .filter { item ->
            val e = item.event
            e is TextNoteModificationEvent && noteAuthor == item.author && !e.isExpirationBefore(now)
        }.sortedWith(compareBy({ it.createdAt() }, { it.idHex }))
}

/**
 * Who a Buzz message counts as written by. Normally its signer; but when the channel's relay signed
 * it on a member's behalf (a workflow posting, a legacy relay-attributed message) the member named in
 * its `actor` tag, else its first `p` tag. The relay's key is the author of the channel's
 * relay-signed 39000 metadata, so a user-signed message can never claim someone else this way.
 * Mirrors `effective_message_author` in Buzz's `buzz-relay/src/handlers/ingest.rs` and
 * `resolveEventAuthorPubkey` in its desktop client (which also wants the `h` tag before reading `p`).
 */
fun Note.buzzEffectiveAuthor(): HexKey? {
    val event = event ?: return author?.pubkeyHex
    val relayKey = inGatherers?.firstNotNullOfOrNull { (it as? RelayGroupChannel)?.event?.pubKey }
    if (relayKey != null && event.pubKey == relayKey) {
        event.tags.firstOrNull { it.size > 1 && it[0] == "actor" && Hex.isHex64(it[1]) }?.let { return it[1] }
        if (event.tags.any { it.size > 1 && it[0] == "h" }) {
            event.tags.firstOrNull { it.size > 1 && it[0] == "p" && Hex.isHex64(it[1]) }?.let { return it[1] }
        }
    }
    return event.pubKey
}

/**
 * Whether [signer] may edit this Buzz message: its [buzzEffectiveAuthor], or the owner that author
 * declares through a verified NIP-OA `auth` tag on its kind-0 profile (Buzz lets the owning human
 * edit its agent's messages). Mirrors `validate_edit_ownership` on the relay and
 * `isAuthorizedMessageEdit` in Buzz's desktop client.
 */
fun Note.isBuzzEditableBy(
    signer: HexKey,
    users: (HexKey) -> User? = LocalCache::getUserIfExists,
): Boolean {
    val author = buzzEffectiveAuthor() ?: return false
    return signer == author || signer == buzzOwnerOf(author, users)
}

private fun buzzOwnerOf(
    author: HexKey,
    users: (HexKey) -> User?,
): HexKey? =
    users(author)
        ?.metadataOrNull()
        ?.flow
        ?.value
        ?.nipOaOwner

/**
 * The kind-40003 Buzz edit overlaying this message, or null: the newest ([createdAt], then id) of
 * the edits its author or the author's agent owner made ([isBuzzEditableBy]).
 */
fun Note.latestBuzzEdit(users: (HexKey) -> User? = LocalCache::getUserIfExists): Note? {
    val candidates = edits.filter { it.event is StreamMessageEditEvent }
    if (candidates.isEmpty()) return null
    val author = buzzEffectiveAuthor() ?: return null
    // Resolve the owner only when some edit is not the author's own; it costs a signature check.
    val owner by lazy { buzzOwnerOf(author, users) }
    return candidates
        .filter {
            val signer = it.author?.pubkeyHex
            signer != null && (signer == author || signer == owner)
        }
        // idHex tie-break so a same-second pair resolves identically on every client.
        .maxWithOrNull(compareBy({ it.createdAt() ?: 0L }, { it.idHex }))
}

/**
 * The kind-1009 Marmot edit overlaying this message, or null.
 *
 * Marmot fixes both halves of this rule in `foundation/application-messages.md`
 * ("Message edits"): only the original author's account may replace a message,
 * and the latest `created_at` wins with the event id breaking a tie. The
 * tie-break is not decoration — two devices of one account can stamp the same
 * second, and without it two readers would render different text for the same
 * message forever.
 *
 * Authorship is by ACCOUNT, which is what `author?.pubkeyHex` already is for a
 * Marmot inner event: a second device of the same account holds a different MLS
 * leaf but the same account key, and may edit its own account's message.
 */
fun Note.latestMarmotEdit(): Note? {
    val authorHex = author?.pubkeyHex ?: return null
    return edits
        .filter { it.author?.pubkeyHex == authorHex && it.event?.kind == MarmotAppEvent.KIND_EDIT }
        .maxWithOrNull(compareBy({ it.createdAt() ?: 0L }, { it.idHex }))
}

/** The kind-3302 Concord edit overlaying this message, or null — author-only, newest by CORD-02 §4 send time. */
fun Note.latestConcordEdit(): Note? {
    val authorHex = author?.pubkeyHex ?: return null
    return edits
        .filter { it.author?.pubkeyHex == authorHex && it.event is ConcordChatEditEvent }
        .maxWithOrNull(compareBy({ (it.event as ConcordChatEditEvent).orderingMs() }, { it.idHex }))
}
