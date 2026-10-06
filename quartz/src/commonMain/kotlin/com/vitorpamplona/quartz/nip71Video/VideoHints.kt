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
package com.vitorpamplona.quartz.nip71Video

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip71Video.tags.TextTrackTag

/*
 * The references a NIP-71 video carries, shared by both video bases
 * ([RegularVideoEvent], [AddressableVideoEvent]) so their hint providers cannot drift:
 *
 * - `p`: participants and person credits (`["p", <pk>, <relay>, "inspired-by"]`, or the
 *   relay-less `["p", <pk>, "Collaborator"]`) — [PTag] only reads slot 2+ as a hint when
 *   it is a relay URL, so a role label never becomes one.
 * - `e`: event credits (divine.video's reused "audio" source). A bare `e` is still a pointer,
 *   so every `e` is linked, not only the labelled ones [credits][VideoEvent.credits] keeps.
 * - `a`: credited videos, plus `text-track` refs that are `39307:` coordinates rather than URLs.
 *
 * Unlike [ATag.parseAsHint], the `a` hint requires slot 2 to be a relay URL: the credit
 * convention puts a role label there when no relay is given, and the relay normalizer would
 * otherwise turn `inspired-by` into `wss://inspired-by/`.
 */

fun TagArray.videoPubKeyHints(): List<PubKeyHint> = mapNotNull(PTag::parseAsHint)

fun TagArray.videoLinkedPubKeys(): List<HexKey> = mapNotNull(PTag::parseKey)

fun TagArray.videoEventHints(): List<EventIdHint> = mapNotNull(ETag::parseAsHint)

fun TagArray.videoLinkedEventIds(): List<HexKey> = mapNotNull(ETag::parseId)

fun TagArray.videoAddressHints(): List<AddressHint> = mapNotNull(::creditedAddressAsHint) + mapNotNull(TextTrackTag::parseAddressAsHint)

fun TagArray.videoLinkedAddressIds(): List<String> = mapNotNull(ATag::parseValidAddress) + mapNotNull(TextTrackTag::parseAddressId)

private fun creditedAddressAsHint(tag: Array<String>): AddressHint? {
    if (!tag.has(2) || tag[0] != ATag.TAG_NAME) return null
    val relay = RelayUrlNormalizer.normalizeHintOrNull(tag[2]) ?: return null
    val address = ATag.parseValidAddress(tag) ?: return null
    return AddressHint(address, relay)
}
