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
package com.vitorpamplona.amethyst.commons.ui.notifications

import com.vitorpamplona.quartz.experimental.attestations.request.AttestationRequestEvent
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.rsvp.CalendarRSVPEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip58Badges.award.BadgeAwardEvent
import com.vitorpamplona.quartz.nip64Chess.challenge.accept.LiveChessGameAcceptEvent
import com.vitorpamplona.quartz.nip64Chess.move.LiveChessMoveEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.VideoHorizontalEvent
import com.vitorpamplona.quartz.nip71Video.VideoNormalEvent
import com.vitorpamplona.quartz.nip71Video.VideoShortEvent
import com.vitorpamplona.quartz.nip71Video.VideoVerticalEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceReplyEvent
import com.vitorpamplona.quartz.nipA4PublicMessages.PublicMessageEvent

/**
 * Centralized notification event kind constants for the notification feed.
 *
 * These define which event kinds are treated as addressable (replaceable)
 * and which are shown in the notification feed.
 */
object NotificationKinds {
    /**
     * Addressable (replaceable) event kinds that appear in notifications.
     */
    val ADDRESSABLE_KINDS =
        listOf(
            AudioTrackEvent.KIND,
            CalendarTimeSlotEvent.KIND,
            CalendarDateSlotEvent.KIND,
            CalendarRSVPEvent.KIND,
            ClassifiedsEvent.KIND,
            LiveActivitiesEvent.KIND,
            LiveChessGameAcceptEvent.KIND,
            LiveChessMoveEvent.KIND,
            LongTextNoteEvent.KIND,
            NipTextEvent.KIND,
            VideoVerticalEvent.KIND,
            VideoHorizontalEvent.KIND,
            WikiNoteEvent.KIND,
            AttestationRequestEvent.KIND,
        )

    /**
     * All event kinds that should appear in the notification feed.
     * Includes both regular and addressable kinds.
     */
    val NOTIFICATION_KINDS: Set<Int> =
        setOf(
            BadgeAwardEvent.KIND,
            ChatMessageEvent.KIND,
            ChatMessageEncryptedFileHeaderEvent.KIND,
            CommentEvent.KIND,
            GenericRepostEvent.KIND,
            GitIssueEvent.KIND,
            GitPatchEvent.KIND,
            HighlightEvent.KIND,
            TextNoteEvent.KIND,
            ReactionEvent.KIND,
            RepostEvent.KIND,
            LnZapEvent.KIND,
            LiveActivitiesChatMessageEvent.KIND,
            PictureEvent.KIND,
            PollEvent.KIND,
            ZapPollEvent.KIND,
            PrivateDmEvent.KIND,
            PublicMessageEvent.KIND,
            VideoNormalEvent.KIND,
            VideoShortEvent.KIND,
            VoiceEvent.KIND,
            VoiceReplyEvent.KIND,
        ) + ADDRESSABLE_KINDS
}
