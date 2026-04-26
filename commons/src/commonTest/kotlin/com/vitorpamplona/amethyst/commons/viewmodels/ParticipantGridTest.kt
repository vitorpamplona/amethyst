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
package com.vitorpamplona.amethyst.commons.viewmodels

import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ParticipantTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ROLE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParticipantGridTest {
    private val host = "a".repeat(64)
    private val speaker = "b".repeat(64)
    private val listener = "c".repeat(64)

    private fun pTag(
        pk: String,
        role: ROLE,
    ) = ParticipantTag(pubKey = pk, relayHint = null, role = role.code, proof = null)

    private fun presence(
        pk: String,
        ts: Long,
        onstage: Boolean = true,
        handRaised: Boolean = false,
        publishing: Boolean = false,
        muted: Boolean? = null,
    ) = RoomPresence(
        pubkey = pk,
        handRaised = handRaised,
        muted = muted,
        publishing = publishing,
        onstage = onstage,
        updatedAtSec = ts,
    )

    @Test
    fun hostAndSpeakerLandOnStageWhenPresenceMatches() {
        val grid =
            buildParticipantGrid(
                participants = listOf(pTag(host, ROLE.HOST), pTag(speaker, ROLE.SPEAKER)),
                presences = mapOf(host to presence(host, 100L), speaker to presence(speaker, 100L)),
            )
        assertEquals(2, grid.onStage.size)
        assertEquals(0, grid.audience.size)
        assertEquals(setOf(host, speaker), grid.onStage.map { it.pubkey }.toSet())
    }

    @Test
    fun speakerWithOnstageFalseDropsToAudience() {
        val grid =
            buildParticipantGrid(
                participants = listOf(pTag(host, ROLE.HOST), pTag(speaker, ROLE.SPEAKER)),
                presences =
                    mapOf(
                        host to presence(host, 100L),
                        speaker to presence(speaker, 100L, onstage = false),
                    ),
            )
        assertEquals(setOf(host), grid.onStage.map { it.pubkey }.toSet())
        assertEquals(setOf(speaker), grid.audience.map { it.pubkey }.toSet())
    }

    @Test
    fun pureAudienceMembersAppearInAudience() {
        val grid =
            buildParticipantGrid(
                participants = listOf(pTag(host, ROLE.HOST)),
                presences = mapOf(host to presence(host, 100L), listener to presence(listener, 100L)),
            )
        // host on stage; the kind-10312-only listener in audience.
        assertEquals(setOf(host), grid.onStage.map { it.pubkey }.toSet())
        assertEquals(setOf(listener), grid.audience.map { it.pubkey }.toSet())
        // The audience member has no role.
        assertEquals(null, grid.audience.first().role)
    }

    @Test
    fun absentParticipantTagIsAbsentTrueOnAudienceRow() {
        // Speaker whose presence we've never seen — kind-30312 says
        // they're a member, but they haven't joined yet. nostrnests
        // greys them out; we mark absent=true so the renderer can.
        val grid =
            buildParticipantGrid(
                participants = listOf(pTag(host, ROLE.HOST), pTag(speaker, ROLE.SPEAKER)),
                presences = mapOf(host to presence(host, 100L)),
            )
        // The speaker has no presence — defaults onstage=true, so
        // they DO appear on stage, marked absent.
        val onStageSpeaker = grid.onStage.first { it.pubkey == speaker }
        assertTrue(onStageSpeaker.absent, "speaker without presence should be marked absent")
    }

    @Test
    fun emptyInputProducesEmptyGrid() {
        val grid = buildParticipantGrid(emptyList(), emptyMap())
        assertEquals(0, grid.onStage.size)
        assertEquals(0, grid.audience.size)
    }
}
