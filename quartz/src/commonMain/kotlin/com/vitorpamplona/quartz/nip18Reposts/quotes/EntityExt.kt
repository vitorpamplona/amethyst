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
package com.vitorpamplona.quartz.nip18Reposts.quotes

import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote

fun NNote.toQuoteTag() = QEventTag(hex, null, null)

fun NEvent.toQuoteTag() = QEventTag(hex, relay.firstOrNull(), author)

fun NAddress.toQuoteTag() = QAddressableTag(kind, author, dTag, relay.firstOrNull())

fun NEmbed.toQuoteTag() =
    if (event is AddressableEvent) {
        QAddressableTag(event.kind, event.pubKey, event.dTag(), null)
    } else {
        QEventTag(event.id, null, event.pubKey)
    }

fun NNote.toQuoteTagArray() = QEventTag.assemble(hex, null, null)

fun NEvent.toQuoteTagArray() = QEventTag.assemble(hex, relay.firstOrNull(), author)

fun NAddress.toQuoteTagArray() = QAddressableTag.assemble(kind, author, dTag, relay.firstOrNull())

fun NEmbed.toQuoteTagArray() =
    if (event is AddressableEvent) {
        QAddressableTag.assemble(event.kind, event.pubKey, event.dTag(), null)
    } else {
        QEventTag.assemble(event.id, null, event.pubKey)
    }

fun ETag.toQTagArray() = QEventTag.assemble(eventId, relay, author)

fun ATag.toQTagArray() = QAddressableTag.assemble(kind, pubKeyHex, dTag, relay)
