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
package com.vitorpamplona.amethyst.commons.nip64Chess

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.crypto.random.RandomInstance
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip64Chess.Color
import com.vitorpamplona.quartz.nip64Chess.jester.JesterContent
import com.vitorpamplona.quartz.nip64Chess.jester.JesterEvent
import com.vitorpamplona.quartz.nip64Chess.jester.JesterProtocol
import com.vitorpamplona.quartz.nip64Chess.jester.gameEventRef
import com.vitorpamplona.quartz.nip64Chess.jester.jesterOpponent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * The `e` reference jesterui files a direct challenge under: `sha256(sha256(pubkeyHex) +
 * START_POSITION_HASH)`, both hashes as lowercase hex strings. The challenged player finds their
 * challenges by asking for `#e` = this, which keeps them out of the public lobby.
 *
 * Protocol mechanism, so it belongs in quartz's `JesterProtocol`; it lives here until a quartz
 * release carries it.
 */
fun jesterPrivateStartRef(pubkey: HexKey): HexKey = sha256Hex(sha256Hex(pubkey) + JesterProtocol.START_POSITION_HASH)

private fun sha256Hex(value: String): HexKey = sha256(value.encodeToByteArray()).toHexKey()

/**
 * A Jester game start, the way jesterui writes one: an open challenge refers to the start
 * position, a direct one only to its opponent's [jesterPrivateStartRef], and also tags them so
 * clients that read `#p` notify them. The creator's colour rides in `playerColor`, which
 * jesterui ignores: it always seats the creator as white.
 */
fun buildJesterChallenge(
    playerColor: Color,
    opponentPubkey: HexKey?,
    createdAt: Long = TimeUtils.now(),
): EventTemplate<JesterEvent> {
    val content =
        JesterContent(
            kind = JesterProtocol.CONTENT_KIND_START,
            history = emptyList(),
            nonce = RandomInstance.bytes(4).toHexKey(),
            playerColor = if (playerColor == Color.WHITE) "white" else "black",
        )
    return eventTemplate(JesterEvent.KIND, JsonMapper.toJson(content), createdAt) {
        if (opponentPubkey == null) {
            gameEventRef(JesterProtocol.START_POSITION_HASH)
        } else {
            gameEventRef(jesterPrivateStartRef(opponentPubkey))
            jesterOpponent(opponentPubkey)
        }
    }
}

/** Signs and broadcasts a new challenge (see [buildJesterChallenge]); the game's id is the returned event's. */
suspend fun Account.publishChessChallenge(
    playerColor: Color,
    opponentPubkey: HexKey?,
): JesterEvent = signAndComputeBroadcast(buildJesterChallenge(playerColor, opponentPubkey))
