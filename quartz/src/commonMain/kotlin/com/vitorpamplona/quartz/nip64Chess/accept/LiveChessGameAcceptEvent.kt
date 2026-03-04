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
package com.vitorpamplona.quartz.nip64Chess.accept

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip64Chess.baseEvent.BaseChessEvent
import com.vitorpamplona.quartz.nip64Chess.challenge.LiveChessGameChallengeEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Live Chess Game Accept Event (Kind 30065)
 *
 * Accept a chess game challenge
 *
 * Tags:
 * - d: game_id (same as challenge)
 * - e: challenge event ID
 * - p: challenger pubkey
 */
@Immutable
class LiveChessGameAcceptEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseChessEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun challengeEventId() = tags.challengeEventId()

    companion object {
        const val KIND = 30065
        const val ALT_DESCRIPTION = "Chess game acceptance"

        fun build(
            gameId: String,
            challengeEvent: EventHintBundle<LiveChessGameChallengeEvent>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<LiveChessGameAcceptEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            dTag(gameId)
            challenge(challengeEvent)
            challenger(challengeEvent)
            alt(ALT_DESCRIPTION)
            initializer()
        }
    }
}
