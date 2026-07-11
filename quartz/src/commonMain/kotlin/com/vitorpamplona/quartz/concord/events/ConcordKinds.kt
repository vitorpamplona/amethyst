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
package com.vitorpamplona.quartz.concord.events

/**
 * Every Nostr event kind used by the Concord protocol, pinned to the Concord v2
 * reference client (Soapbox Armada, `concord-v2/lib/kinds.ts`) for wire interop.
 *
 * Rumor kinds (9, 1111, 3302, 3303, 3306, 3308, 3309, 3310, 3312, 3313, 23311,
 * 23313, 7, 5) never appear on the wire on their own — they are always sealed
 * and wrapped by [com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope].
 * Only the wrap (1059/21059) and the bare bookkeeping kinds (33301, 13302, 13303)
 * are published directly.
 */
object ConcordKinds {
    // Envelope (CORD-01)
    const val WRAP = 1059
    const val WRAP_EPHEMERAL = 21059
    const val SEAL_ENCRYPTED = 20013
    const val SEAL_PLAINTEXT = 20014

    // Chat Plane rumors (CORD-03).
    // Messages (kind 9), replies (9 + q), reactions (7), and deletes (5) are standard
    // Nostr events — Concord reuses ChatEvent / ReactionEvent / DeletionEvent and only
    // adds the channel/epoch binding (see cord03Channels/ChannelChat + tags/), so they
    // are NOT aliased here. Only the Concord-specific chat kinds remain.
    const val EDIT = 3302
    const val WEBXDC = 3310
    const val TYPING = 23311
    const val VOICE_PRESENCE = 23313

    // Guestbook Plane rumors (CORD-02)
    const val JOIN_LEAVE = 3306
    const val KICK = 3309
    const val SNAPSHOT = 3312

    // Person-addressed rumor (CORD-05)
    const val DIRECT_INVITE = 3313

    // Rekey rumor (CORD-06). Control 3308 now lives on ControlEditionEvent.KIND.
    const val REKEY = 3303

    // Bare bookkeeping events. Community-list 13302 (ConcordCommunityListEvent.KIND) and
    // invite-bundle 33301 (ConcordInviteBundleEvent.KIND) now own their literals.
    const val INVITE_LIST = 13303 // private self-list, CORD-05
}
