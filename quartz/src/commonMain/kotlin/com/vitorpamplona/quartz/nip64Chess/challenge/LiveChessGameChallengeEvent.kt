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
package com.vitorpamplona.quartz.nip64Chess.challenge

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip64Chess.Color
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Live Chess Game Challenge Event (Kind 30064)
 *
 * Challenge another player to a chess game or create an open challenge.
 * This is a parameterized replaceable event.
 *
 * Tags:
 * - d: game_id (unique identifier for this game)
 * - p: opponent pubkey (optional, for direct challenges)
 * - player_color: "white" or "black"
 * - time_control: optional time control (e.g., "10+0", "5+3")
 */
@Immutable
class LiveChessGameChallengeEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun playerColor() = tags.playerColor()

    fun timeControl() = tags.timeControl()

    fun opponentPubkey(): String? = tags.firstOrNull { it.size >= 2 && it[0] == "p" }?.get(1)

    companion object {
        const val KIND = 30064
        const val ALT_DESCRIPTION = "Chess game challenge"

        fun build(
            gameId: String,
            playerColor: Color,
            opponentPubkey: String? = null,
            timeControl: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<LiveChessGameChallengeEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            add(arrayOf("d", gameId))
            playerColor(playerColor)
            opponentPubkey?.let { add(arrayOf("p", it)) }
            timeControl?.let { timeControl(it) }
            alt(ALT_DESCRIPTION)
            initializer()
        }
    }
}
