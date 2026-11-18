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

import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip04Dm.messages.EncryptedDmEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.EphemeralGiftWrapEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent

/**
 * Which events Amethyst may copy to the share relay ([com.vitorpamplona.amethyst.commons.defaults.Constants.brainstorm])
 * when the user makes a web link for them. A web link page can only render what its relay can
 * serve, so a shared note is pushed there first; but a share link must never be the path by
 * which private content reaches a public relay.
 *
 * Pushed: signed events that already live on public relays. Never pushed:
 * - unsigned rumors (NIP-17 DMs, Concord and Marmot messages, private replies): they have no
 *   signature to publish and their content is private;
 * - envelopes of private content (gift wraps, seals, NIP-04 DMs, Marmot group events, drafts):
 *   signed, but republishing them spreads ciphertext and metadata the user did not choose to spread;
 * - NIP-29 group posts (an `h` tag): a group's relay decides who reads them, and a copy elsewhere
 *   would bypass that;
 * - NIP-70 protected events (a `-` tag): only their author may publish them to a relay.
 */
object ShareRelayPolicy {
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
        for (tag in event.tags) {
            if (tag.isEmpty()) continue
            if (tag[0] == "-" || tag[0] == "h") return false
        }
        return true
    }
}
