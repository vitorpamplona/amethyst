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

import com.vitorpamplona.amethyst.commons.util.ShareSite
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip04Dm.messages.EncryptedDmEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.EphemeralGiftWrapEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent

/**
 * Which events Amethyst copies to the share relay ([ShareSite.RELAYS]) when the user shares a web
 * link for them. A web link page can only render what its relay can serve, so a shared note is
 * copied there; but a share link must never be the path by which private content reaches a public
 * relay.
 *
 * Copied: signed events already seen on a public relay, that the share relay does not have yet.
 * Never copied:
 * - unsigned rumors (NIP-17 DMs, Concord and Marmot messages, private replies): they have no
 *   signature to publish and their content is private;
 * - envelopes of private content (gift wraps, seals, NIP-04 DMs, Marmot group events, drafts):
 *   signed, but republishing them spreads ciphertext and metadata the user did not choose to spread;
 * - NIP-29 group posts (an `h` tag) and the metadata of private or hidden groups: a group's relay
 *   decides who reads them, and a copy elsewhere would bypass that;
 * - private NIP-34 repositories, which name the only relays they may live on;
 * - NIP-70 protected events (a `-` tag): only their author may publish them to a relay;
 * - events seen only on relays that are not public (`isPublicRelay`: the user's local and private
 *   storage relays, relays that require AUTH): the user may be sharing with someone who has access,
 *   and copying would publish them to everyone.
 */
object ShareRelayPolicy {
    /**
     * What sharing [note]'s link copies: the note, the note it reposts or replies to (Brainstorm's
     * page shows that one too), and its author's profile and relay list (so the page shows their
     * name and picture and can follow their outbox). Nothing at all when [note] itself may not be
     * copied. [profileOf] finds a user's cached kind 0.
     */
    fun eventsToPush(
        note: Note,
        profileOf: (User) -> Note?,
        isPublicRelay: (NormalizedRelayUrl) -> Boolean,
    ): List<Event> {
        if (!mayCopy(note, isPublicRelay)) return emptyList()
        val candidates =
            buildList {
                add(note)
                note.replyTo?.lastOrNull()?.let { add(it) }
                note.author?.let { addAll(authorNotes(it, profileOf)) }
            }
        return toPush(candidates, isPublicRelay)
    }

    /** What sharing [user]'s profile link copies: their profile and relay list. */
    fun eventsToPush(
        user: User,
        profileOf: (User) -> Note?,
        isPublicRelay: (NormalizedRelayUrl) -> Boolean,
    ): List<Event> = toPush(authorNotes(user, profileOf), isPublicRelay)

    private fun authorNotes(
        user: User,
        profileOf: (User) -> Note?,
    ) = listOfNotNull(profileOf(user), user.nip65RelayListNote)

    private fun toPush(
        candidates: List<Note>,
        isPublicRelay: (NormalizedRelayUrl) -> Boolean,
    ): List<Event> =
        candidates
            .filter { mayCopy(it, isPublicRelay) && !isOnShareRelay(it) }
            .mapNotNull { it.event }
            .distinctBy { it.id }

    /** True when [note] may be copied: a public kind of event, seen on at least one public relay. */
    fun mayCopy(
        note: Note,
        isPublicRelay: (NormalizedRelayUrl) -> Boolean,
    ): Boolean = canPush(note) && note.relays.any(isPublicRelay)

    /** True when the share relay already serves [note]: it sent it to us, or accepted it from us. */
    fun isOnShareRelay(note: Note): Boolean = note.relays.any { it in ShareSite.RELAYS }

    fun canPush(note: Note): Boolean {
        if (note.rumorHost != null) return false
        val event = note.event ?: return false
        return canPush(event)
    }

    fun canPush(event: Event): Boolean {
        if (event.sig.isEmpty()) return false
        if (event is GiftWrapEvent ||
            event is EphemeralGiftWrapEvent ||
            event is SealEvent ||
            event is EncryptedDmEvent ||
            event is GroupEvent ||
            event is DraftWrapEvent
        ) {
            return false
        }
        if (event is GitRepositoryEvent && event.isPrivate()) return false
        if (event is GroupMetadataEvent && (event.isPrivate() || event.isHidden())) return false
        for (tag in event.tags) {
            if (tag.isEmpty()) continue
            if (tag[0] == "-" || tag[0] == "h") return false
        }
        return true
    }
}
