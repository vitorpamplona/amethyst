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
package com.vitorpamplona.quartz.nip42RelayAuth

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip42RelayAuth.tags.ChallengeTag
import com.vitorpamplona.quartz.nip42RelayAuth.tags.RelayTag
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class RelayAuthEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun relay() = tags.firstNotNullOfOrNull(RelayTag::parse)

    fun challenge() = tags.firstNotNullOfOrNull(ChallengeTag::parse)

    companion object {
        const val KIND = 22242

        fun build(
            relay: NormalizedRelayUrl,
            challenge: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<RelayAuthEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            relay(relay)
            challenge(challenge)
            initializer()
        }

        suspend fun create(
            relay: NormalizedRelayUrl,
            challenge: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): RelayAuthEvent {
            val content = ""
            val tags =
                arrayOf(
                    RelayTag.assemble(relay),
                    ChallengeTag.assemble(challenge),
                )
            return signer.sign(createdAt, KIND, tags, content)
        }

        suspend fun create(
            relays: List<NormalizedRelayUrl>,
            challenge: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): RelayAuthEvent {
            val content = ""
            val tags =
                relays
                    .map { RelayTag.assemble(it) }
                    .plusElement(ChallengeTag.assemble(challenge))
                    .toTypedArray()
            return signer.sign(createdAt, KIND, tags, content)
        }
    }
}
