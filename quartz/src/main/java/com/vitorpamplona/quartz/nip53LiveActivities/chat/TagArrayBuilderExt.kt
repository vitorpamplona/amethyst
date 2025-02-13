/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.nip53LiveActivities.chat

import com.vitorpamplona.quartz.nip01Core.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.tags.addressables.ATag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTags
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent

fun TagArrayBuilder<LiveActivitiesChatMessageEvent>.activity(rep: ATag) = addUnique(rep.toATagArray())

fun TagArrayBuilder<LiveActivitiesChatMessageEvent>.activity(rep: EventHintBundle<LiveActivitiesEvent>) = addUnique(rep.toATag().toATagArray())

fun TagArrayBuilder<LiveActivitiesChatMessageEvent>.reply(rep: EventHintBundle<LiveActivitiesChatMessageEvent>) = addUnique(rep.toMarkedETag(MarkedETag.MARKER.REPLY).toTagArray())

fun TagArrayBuilder<LiveActivitiesChatMessageEvent>.notify(list: List<PTag>) = pTags(list)

fun TagArrayBuilder<LiveActivitiesChatMessageEvent>.notify(pubkey: PTag) = pTag(pubkey)
