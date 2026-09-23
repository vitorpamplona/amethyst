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
package com.vitorpamplona.quartz.cyberspace.deck0003Sno

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * DECK-0003 §3.1 — a standalone Simple Nostr Object, kind 33331.
 *
 * `content` is the payload of §1 serialized as JSON; [sno] reads it. The `d`
 * tag is the object's identifier, chosen by its author and stable across
 * edits, and an optional `name` tag duplicates the payload's name so a relay
 * query can filter on it without parsing the content.
 *
 * 33331 falls in NIP-01's addressable range, so relays keep the newest event
 * per `(pubkey, kind, d)` and an author edits an object in place. That is the
 * right class for a thing someone iterates on in a modeling tool, and the deck
 * states its cost plainly: reactions, zaps and comments reference an event id,
 * and an address resolves to whatever its author last published, so a payment
 * made against an object can end up pointing at content that changed after it.
 *
 * There is deliberately no regular twin of this kind. An object that must stay
 * as it was found travels as a `kind 3330` bag item instead (§3.2), which this
 * client does not read — a current bag item is encrypted to a cyberspace
 * region key (`CYBERSPACE_V2.md` §7.6) and is not fetchable on its own.
 *
 * @see <a href="https://github.com/arkin0x/cyberspace/blob/master/decks/DECK-0003-sno.md">DECK-0003</a>
 */
@Immutable
class SnoObjectEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    /**
     * The object, or the rule it broke. A client MUST NOT render a payload that
     * fails §1.9 and SHOULD say why rather than failing silently (§3.1), which
     * is why this hands back the failure instead of a null.
     */
    fun sno(fetchedPalette: SnoPalette? = null): SnoResult = SnoParser.parse(content, fetchedPalette)

    fun snoOrNull(fetchedPalette: SnoPalette? = null): SnoPayload? = sno(fetchedPalette).payloadOrNull()

    /** The `name` tag, which duplicates the payload's name for relay-side filtering. */
    fun nameTag(): String? = tags.firstOrNull { it.size > 1 && it[0] == "name" }?.get(1)

    companion object {
        const val KIND = 33331
        const val ALT = "A 3D object"

        fun build(
            dTag: String,
            payloadJson: String,
            name: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<SnoObjectEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, payloadJson, createdAt) {
            dTag(dTag)
            name?.let { add(arrayOf("name", it)) }
            alt(name?.let { "$ALT: $it" } ?: ALT)
            initializer()
        }
    }
}
