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

import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.quotes.QAddressableTag
import com.vitorpamplona.quartz.nip18Reposts.quotes.toQTagArray
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent

/**
 * NIP-18 quote posts: a new kind:1 whose text ends in a `nostr:` link to the quoted
 * event, carrying a `q` tag for it and a `p` tag for its author. Builds and signs;
 * the caller publishes (same contract as [ReplyActions]).
 */
object QuoteActions {
    /** The `nostr:` URI the quote embeds: `naddr` for addressable events (so edits follow), `nevent` otherwise. */
    fun quoteUri(target: EventHintBundle<Event>): String {
        val event = target.event
        val code =
            if (event is AddressableEvent) {
                NAddress.create(event.kind, event.pubKey, event.dTag(), target.relay)
            } else {
                NEvent.create(event.id, event.pubKey, event.kind, target.relay)
            }
        return "nostr:$code"
    }

    suspend fun quote(
        target: EventHintBundle<Event>,
        text: String,
        signer: NostrSigner,
    ): TextNoteEvent {
        check(signer.isWriteable()) { "Cannot quote: signer is not writeable" }
        // A quote would put a private rumor's id on public relays.
        check(target.event.sig.isNotEmpty()) { "Cannot quote a private rumor" }

        val event = target.event
        val content = listOf(text.trimEnd(), quoteUri(target)).filter { it.isNotEmpty() }.joinToString("\n\n")

        val template =
            TextNoteEvent.build(content) {
                if (event is AddressableEvent) {
                    addUniqueValueIfNew(QAddressableTag.assemble(event.address(), target.relay))
                } else {
                    addUniqueValueIfNew(target.toQTagArray())
                }
                addUniqueValueIfNew(PTag(event.pubKey, target.authorHomeRelay).toTagArray())
                contentTags(text)
            }
        return signer.sign(template)
    }
}
