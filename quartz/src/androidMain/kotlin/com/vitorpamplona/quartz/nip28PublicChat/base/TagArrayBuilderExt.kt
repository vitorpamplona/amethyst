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
package com.vitorpamplona.quartz.nip28PublicChat.base

import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTags
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent

fun <T : IsInPublicChatChannel> TagArrayBuilder<T>.channel(rep: MarkedETag) = add(rep.toTagArray())

fun <T : IsInPublicChatChannel> TagArrayBuilder<T>.channel(rep: ETag) = add(MarkedETag.assemble(rep.eventId, rep.relay, MarkedETag.MARKER.ROOT, rep.author))

fun <T : IsInPublicChatChannel> TagArrayBuilder<T>.channel(rep: EventHintBundle<ChannelCreateEvent>) = channel(rep.toMarkedETag(MarkedETag.MARKER.ROOT))

fun TagArrayBuilder<ChannelMessageEvent>.reply(rep: EventHintBundle<ChannelMessageEvent>) = add(rep.toMarkedETag(MarkedETag.MARKER.REPLY).toTagArray())

fun TagArrayBuilder<ChannelMessageEvent>.notify(list: List<PTag>) = pTags(list)

fun TagArrayBuilder<ChannelMessageEvent>.notify(pubkey: PTag) = pTag(pubkey)
