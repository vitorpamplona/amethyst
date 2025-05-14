/**
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn

import android.util.Log
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.quartz.experimental.edits.PrivateOutboxRelayListEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip03Timestamp.OtsEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import kotlinx.coroutines.CancellationException

class PrecacheNewNotesProcessor(
    val account: Account,
    val cache: LocalCache,
) {
    fun consume(note: Note) {
        val noteEvent = note.event
        if (noteEvent != null) {
            try {
                consumeAlreadyVerified(noteEvent, note)
            } catch (e: Exception) {
                Log.e("PrecacheNewNotesProcessor", "Error processing note", e)
            }
        }
    }

    fun consumeAlreadyVerified(
        event: Event,
        note: Note,
    ) {
        when (event) {
            is OtsEvent -> {
                // verifies new OTS upon arrival
                Amethyst.instance.otsVerifCache.cacheVerify(event, account::otsResolver)
            }

            is PrivateOutboxRelayListEvent -> {
                if (event.cachedPrivateTags() == null && event.pubKey == account.signer.pubKey) {
                    event.privateTags(account.signer) { }
                }
            }

            is DraftEvent -> {
                // Avoid decrypting over and over again if the event already exist.
                if (!event.isDeleted() && event.preCachedDraft(account.signer) == null && event.pubKey == account.signer.pubKey) {
                    event.cachedDraft(account.signer) {}
                }
            }

            is GiftWrapEvent -> {
                if (event.recipientPubKey() == account.signer.pubKey) {
                    val inner = event.innerEventId
                    if (inner == null) {
                        event.unwrap(account.signer) {
                            if (cache.justConsume(it, null)) {
                                cache.copyRelaysFromTo(note, it)
                                consumeAlreadyVerified(it, note)
                            }
                        }
                    } else {
                        cache.copyRelaysFromTo(note, inner)
                        val event = cache.getOrCreateNote(inner).event
                        if (event != null) {
                            consumeAlreadyVerified(event, note)
                        }
                    }
                }
            }

            is SealedRumorEvent -> {
                val inner = event.innerEventId
                if (inner == null) {
                    event.unseal(account.signer) {
                        cache.justConsume(it, null)
                        cache.copyRelaysFromTo(note, it)
                    }
                } else {
                    cache.copyRelaysFromTo(note, inner)
                }
            }

            is LnZapEvent -> {
                event.zapRequest?.let { req ->
                    if (req.cachedPrivateZap() == null && req.isPrivateZap()) {
                        // We can't know which account this was for without going through it.
                        req.decryptPrivateZap(account.signer) {}
                    }
                }
            }

            is LnZapRequestEvent -> {
                if (event.cachedPrivateZap() == null && event.isPrivateZap()) {
                    event.decryptPrivateZap(account.signer) { }
                }
            }
        }
    }

    fun run(newNotes: Set<Note>) {
        try {
            newNotes.forEach {
                consume(it)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("PrecacheNewNotesProcessor", "This shouldn't happen", e)
        }
    }
}
