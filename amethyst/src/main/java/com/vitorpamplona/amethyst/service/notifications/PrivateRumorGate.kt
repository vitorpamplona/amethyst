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
package com.vitorpamplona.amethyst.service.notifications

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallOfferEvent

/**
 * Keeps private rumors off the public notification paths.
 *
 * A message decrypted out of a private envelope (a Marmot group's inner event, a Concord channel
 * rumor, a NIP-59 gift-wrapped rumor) is an unsigned event that lands in `LocalCache` like any
 * other, so the cache observer sees it too. A kind-1111 thread message in a Marmot group p-tags the
 * members it mentions (MDK does), and a generic "tags me and replies to my note" check would treat
 * it as a public NIP-22 reply: plaintext in the REPLY channel past the privacy lock, and a Reply
 * action that copies the group's root tags into a comment broadcast to public relays.
 *
 * The marker is the signature. Every event that came from a relay was verified on its way into the
 * cache; a rumor carries none (an empty `sig`, or a leftover value nobody checked), so only an
 * event its author chose to sign, and so to make public, passes. The only unsigned kinds the
 * dispatcher routes on purpose are the NIP-17 DM rumors and the call offers, which go to their own
 * private renderers ([isPrivatelyRoutedRumorKind]); Marmot group messages notify through
 * `MarmotGroupNotifier` instead.
 */
object PrivateRumorGate {
    /** The unsigned kinds whose notifications are private by construction (DM and call renderers). */
    fun isPrivatelyRoutedRumorKind(event: Event): Boolean = event is ChatMessageEvent || event is ChatMessageEncryptedFileHeaderEvent || event is CallOfferEvent

    /**
     * True when [event] carries a valid signature by its author over its own id, and the id is the
     * hash of its fields: a public event, not a rumor. Checking the id too means a rumor cannot pass
     * by borrowing the id and signature of some public event.
     */
    fun isSigned(event: Event): Boolean = event.verify()

    /** Whether [event] may reach a notification path built for public events. */
    fun mayNotifyPublicly(event: Event): Boolean = isPrivatelyRoutedRumorKind(event) || isSigned(event)
}
