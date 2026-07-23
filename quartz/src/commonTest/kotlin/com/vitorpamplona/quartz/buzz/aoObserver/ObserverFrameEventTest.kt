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
package com.vitorpamplona.quartz.buzz.aoObserver

import com.vitorpamplona.quartz.buzz.aoObserver.tags.FrameTag
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ObserverFrameEventTest {
    private val agent = NostrSignerInternal(KeyPair())
    private val owner = NostrSignerInternal(KeyPair())

    @Test
    fun telemetryRoundTripsBothWays() =
        runTest {
            val payload =
                ObserverTelemetryPayload(
                    seq = 7,
                    timestamp = "2026-07-01T20:11:03.213Z",
                    kind = "turn_started",
                    channelId = "12345678-1234-1234-1234-123456789abc",
                    turnId = "turn-1",
                    payload = buildJsonObject { put("model", JsonPrimitive("claude-sonnet-4-5")) },
                )

            val event = ObserverFrameEvent.createTelemetry(payload, owner.pubKey, agent)

            assertEquals(24200, event.kind)
            assertEquals(agent.pubKey, event.pubKey)
            assertEquals(agent.pubKey, event.agentPubKey())
            assertEquals(owner.pubKey, event.recipientPubKey())
            assertEquals(FrameTag.TELEMETRY, event.frame())

            // Author (agent) reads it, and the recipient (owner) reads it too — symmetric key.
            assertEquals(payload, event.decryptTelemetry(agent))
            assertEquals(payload, event.decryptTelemetry(owner))
        }

    @Test
    fun controlRoundTripsBothWays() =
        runTest {
            val payload =
                ObserverControlPayload(
                    type = ObserverControlPayload.SWITCH_MODEL,
                    channelId = "12345678-1234-1234-1234-123456789abc",
                    modelId = "claude-opus-4",
                )

            // Owner authors a control frame addressed to the agent.
            val event = ObserverFrameEvent.createControl(payload, agent.pubKey, owner)

            assertEquals(24200, event.kind)
            assertEquals(owner.pubKey, event.pubKey)
            assertEquals(agent.pubKey, event.agentPubKey())
            assertEquals(agent.pubKey, event.recipientPubKey())
            assertEquals(FrameTag.CONTROL, event.frame())

            assertEquals(payload, event.decryptControl(owner))
            assertEquals(payload, event.decryptControl(agent))
        }

    @Test
    fun ephemeralKindIsInRange() {
        assertTrue(ObserverFrameEvent.KIND in 20000..29999, "24200 must be ephemeral")
    }

    @Test
    fun wrongShapeDecryptReturnsNull() =
        runTest {
            val control =
                ObserverControlPayload(type = ObserverControlPayload.CANCEL_TURN, channelId = "c")
            val event = ObserverFrameEvent.createControl(control, agent.pubKey, owner)

            // Decoding a control body as telemetry fails required fields (seq/timestamp/kind).
            assertNull(event.decryptTelemetryOrNull(owner))
        }
}
