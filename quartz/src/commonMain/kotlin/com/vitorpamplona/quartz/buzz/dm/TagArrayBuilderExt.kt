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
package com.vitorpamplona.quartz.buzz.dm

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.tags.dTag.DTag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupIdTag

/** One `p` tag per DM participant. */
fun <T : Event> TagArrayBuilder<T>.participants(pubKeys: List<HexKey>) = addAll(pubKeys.map { PTag.assemble(it, null) })

/** The `h` (NIP-29 group id = DM channel UUID) tag. */
fun <T : Event> TagArrayBuilder<T>.dmChannel(channelId: String) = addUnique(GroupIdTag.assemble(channelId))

/** A single `p` tag for the member being added. */
fun <T : Event> TagArrayBuilder<T>.dmMember(pubKey: HexKey) = addUnique(PTag.assemble(pubKey, null))

/** The `d` tag carrying the DM id (relay-signed [DmCreatedEvent]). */
fun <T : Event> TagArrayBuilder<T>.dmId(dmId: String) = addUnique(DTag.assemble(dmId))
