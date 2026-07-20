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
package com.vitorpamplona.amethyst.commons.model.privateChats

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent

/**
 * True when this event's `content` field holds ciphertext instead of readable text.
 *
 * These are exactly the kinds whose plaintext has to come out of a decryption cache; their
 * raw `content` is a base64 blob (NIP-04 `<ct>?iv=<iv>` or a NIP-44 payload) and must never
 * reach the screen. Anything else — including a NIP-17 rumor, whose content is already
 * plaintext by the time it lands in the cache — is safe to render verbatim.
 *
 * Deliberately narrower than [Event.isContentEncoded], which also covers merely
 * *machine-readable* content (kind:0 JSON, OTS blobs, marketplace payloads) that callers
 * legitimately read raw.
 */
fun Event.hasEncryptedContent(): Boolean =
    when (this) {
        is PrivateDmEvent -> true
        is DraftWrapEvent -> true
        is SealedRumorEvent -> true
        is GiftWrapEvent -> true
        is LnZapRequestEvent -> isPrivateZap()
        else -> false
    }

/**
 * What a chat row should render for a message, once the raw ciphertext is off the table.
 *
 * [Decrypting] and [Undecryptable] are kept apart on purpose: a message that is merely
 * still being decrypted resolves on its own and must not be labelled undecryptable.
 */
sealed interface ChatPreview {
    /** Readable text, ready to render. */
    data class Body(
        val text: String,
    ) : ChatPreview

    /** Encrypted, decryptable by this account, plaintext not available yet. */
    data object Decrypting : ChatPreview

    /** Encrypted and this account can never read it (no key, or not a party to the DM). */
    data object Undecryptable : ChatPreview

    /** No event at all — the row is referencing something we never received. */
    data object Missing : ChatPreview
}

/**
 * Pure classifier for the preview text of a chat message.
 *
 * @param event the message, or null when the note carries no event yet.
 * @param decrypted plaintext already available from the decryption cache, if any.
 * @param myPubKey the logged-in account's pubkey; null when unknown.
 * @param canDecrypt whether the account holds (or can reach) a key at all — i.e.
 *   `Account.isWriteable()`. A read-only npub login is false.
 */
fun chatPreviewOf(
    event: Event?,
    decrypted: String?,
    myPubKey: HexKey?,
    canDecrypt: Boolean,
): ChatPreview {
    if (event == null) return ChatPreview.Missing

    if (!event.hasEncryptedContent()) return ChatPreview.Body(decrypted ?: event.content)

    // Never trust `event.content` from here down: it is ciphertext.
    if (decrypted != null) return ChatPreview.Body(decrypted)

    if (!canDecrypt) return ChatPreview.Undecryptable

    // A kind:4 addressed to neither me nor from me can't be opened with my key, ever.
    if (event is PrivateDmEvent && myPubKey != null && !event.isIncluded(myPubKey)) {
        return ChatPreview.Undecryptable
    }

    return ChatPreview.Decrypting
}
