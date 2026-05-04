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
package com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParticipantTagTest {
    private val pk = "a".repeat(64)

    private fun tag(role: String?) = ParticipantTag(pubKey = pk, relayHint = null, role = role, proof = null)

    @Test
    fun roleHelpersHostModeratorSpeakerParticipant() {
        assertTrue(tag("host").isHost())
        assertFalse(tag("host").isModerator())
        assertFalse(tag("host").isSpeaker())

        assertTrue(tag("moderator").isModerator())
        assertTrue(tag("speaker").isSpeaker())
    }

    @Test
    fun roleHelpersAreCaseInsensitive() {
        assertTrue(tag("HOST").isHost())
        assertTrue(tag("Speaker").isSpeaker())
    }

    @Test
    fun unknownRoleHasNoEffectiveRole() {
        assertNull(tag("director").effectiveRole())
        assertNull(tag(null).effectiveRole())
        assertFalse(tag("director").canSpeak())
    }

    @Test
    fun canSpeakIncludesHostModeratorAndSpeakerOnly() {
        assertTrue(tag("host").canSpeak())
        assertTrue(tag("moderator").canSpeak())
        assertTrue(tag("speaker").canSpeak())
        assertFalse(tag("participant").canSpeak())
        assertFalse(tag(null).canSpeak())
    }

    @Test
    fun effectiveRoleParsesEnumValue() {
        assertEquals(ROLE.HOST, tag("host").effectiveRole())
        assertEquals(ROLE.MODERATOR, tag("moderator").effectiveRole())
        assertEquals(ROLE.SPEAKER, tag("speaker").effectiveRole())
        assertEquals(ROLE.PARTICIPANT, tag("participant").effectiveRole())
    }

    @Test
    fun nostrnestsAdminRoleIsAcceptedAsModerator() {
        // nostrnests' useAdminCommands.ts emits role="admin" on the
        // p-tag; we must recognise it for promote/demote/kick interop.
        assertEquals(ROLE.MODERATOR, tag("admin").effectiveRole())
        assertTrue(tag("admin").isModerator())
        assertTrue(tag("admin").canSpeak())
        // Case-insensitive too, like everything else.
        assertEquals(ROLE.MODERATOR, tag("Admin").effectiveRole())
        assertEquals(ROLE.MODERATOR, tag("ADMIN").effectiveRole())
    }

    @Test
    fun moderatorCodeEmitsAdminWireString() {
        // After matching nostrnests / EGG-07 the wire string is "admin";
        // any code that builds a kind-30312 p-tag from ROLE.MODERATOR.code
        // must produce "admin", not the legacy "moderator".
        assertEquals("admin", ROLE.MODERATOR.code)
        assertEquals(listOf("moderator"), ROLE.MODERATOR.legacyCodes)
    }
}
