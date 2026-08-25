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
package com.vitorpamplona.quartz.experimental.trustedLists.events

import com.vitorpamplona.quartz.experimental.trustedLists.events.tags.EventMemberTag
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag

fun TagArrayBuilder<EventTrustedListEvent>.member(
    eventId: HexKey,
    relayHint: NormalizedRelayUrl? = null,
    score: Int? = null,
) = add(EventMemberTag.assemble(eventId, relayHint, score))

fun TagArrayBuilder<EventTrustedListEvent>.member(member: EventMemberTag) = add(EventMemberTag.assemble(member))

fun TagArrayBuilder<EventTrustedListEvent>.members(members: List<EventMemberTag>) = addAll(EventMemberTag.assemble(members))

fun TagArrayBuilder<EventTrustedListEvent>.aboutAddress(address: ATag) = addUniqueValueIfNew(address.toATagArray())

fun TagArrayBuilder<EventTrustedListEvent>.aboutPubKey(pubKey: PTag) = addUniqueValueIfNew(pubKey.toTagArray())

fun TagArrayBuilder<EventTrustedListEvent>.aboutPubKey(
    pubKey: HexKey,
    relayHint: NormalizedRelayUrl? = null,
) = addUniqueValueIfNew(PTag.assemble(pubKey, relayHint))
