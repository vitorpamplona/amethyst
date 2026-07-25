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
package com.vitorpamplona.quartz.buzz.workspace

import com.vitorpamplona.quartz.buzz.huddles.HuddleEndedEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleParticipantJoinedEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleParticipantLeftEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleStartedEvent
import com.vitorpamplona.quartz.buzz.jobs.JobAcceptedEvent
import com.vitorpamplona.quartz.buzz.jobs.JobCancelEvent
import com.vitorpamplona.quartz.buzz.jobs.JobErrorEvent
import com.vitorpamplona.quartz.buzz.jobs.JobProgressEvent
import com.vitorpamplona.quartz.buzz.jobs.JobRequestEvent
import com.vitorpamplona.quartz.buzz.jobs.JobResultEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageDiffEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageV2Event
import com.vitorpamplona.quartz.buzz.stream.SystemMessageEvent
import com.vitorpamplona.quartz.nip01Core.core.Event

/**
 * True when this Buzz event is a **chat-timeline row** — one that `LocalCache.consumeBuzzTimelineEvent`
 * attaches to its `h`-scoped channel and that the channel chat renders as a bubble, a centered system
 * line, a diff card or an agent-activity row. These are the Buzz dialect's analog of a NIP-29 kind-9
 * chat message, so each is eligible to be a room's newest message on the Messages list and to light its
 * unread dot (see `Event.isGroupChatContent`).
 *
 * The set mirrors exactly what the chat feed renders (`ChatMessageCompose`):
 * - kind 40002 [StreamMessageV2Event] — the stream-channel chat message (plain text)
 * - kind 40008 [StreamMessageDiffEvent] — a code/text diff pushed into the channel
 * - kind 40099 [SystemMessageEvent] — relay-narrated "topic changed" / "X joined" system lines
 * - kinds 43001-43006 — agent job lifecycle ([JobRequestEvent] … [JobErrorEvent])
 * - kinds 48100-48103 — huddle lifecycle ([HuddleStartedEvent] … [HuddleEndedEvent])
 *
 * Deliberately EXCLUDES Buzz kinds that are not chat rows: stream edits (40003, folded into their
 * target message), canvas (40100), and the forum kinds (45001-45003, which belong to the Threads tab
 * or are store-only). Their `content` is often JSON or a diff blob, so a caller that shows this as a
 * preview must summarize it (see the Buzz preview-summary helpers in the UI) rather than print `content`.
 */
fun Event.isBuzzChatTimelineContent(): Boolean =
    this is StreamMessageV2Event ||
        this is StreamMessageDiffEvent ||
        this is SystemMessageEvent ||
        this is JobRequestEvent ||
        this is JobAcceptedEvent ||
        this is JobProgressEvent ||
        this is JobResultEvent ||
        this is JobCancelEvent ||
        this is JobErrorEvent ||
        this is HuddleStartedEvent ||
        this is HuddleParticipantJoinedEvent ||
        this is HuddleParticipantLeftEvent ||
        this is HuddleEndedEvent
