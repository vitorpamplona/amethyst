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
package com.vitorpamplona.quartz.experimental.nests.admin

import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminCommandEventTest {
    private val host = "a".repeat(64)
    private val target = "b".repeat(64)
    private val roomATag = ATag(kind = 30312, pubKeyHex = host, dTag = "rt-1", relay = null)

    @Test
    fun kickTemplateUsesActionTagWithEmptyContent() {
        // Spec / nostrnests wire format: ["action", "kick"] tag,
        // empty content. An older Amethyst layout put the verb in
        // content; the reader still accepts that for compat but
        // emission is tag-only.
        val template = AdminCommandEvent.kick(roomATag, target, createdAt = 100L)
        assertEquals(AdminCommandEvent.KIND, template.kind)
        assertEquals("", template.content)
        val actionTag = template.tags.firstOrNull { it.firstOrNull() == AdminCommandEvent.ACTION_TAG }
        assertEquals(AdminCommandEvent.Action.KICK.code, actionTag?.getOrNull(1))
        // Single `a` tag pointing at the room.
        val aTag = template.tags.firstOrNull { it.firstOrNull() == "a" }
        assertEquals(roomATag.toTag(), aTag?.getOrNull(1))
        // Single `p` tag pointing at the kicked user.
        val pTag = template.tags.firstOrNull { it.firstOrNull() == "p" }
        assertEquals(target, pTag?.getOrNull(1))
    }

    @Test
    fun forceMuteTemplateUsesActionTag() {
        val template = AdminCommandEvent.forceMute(roomATag, target, createdAt = 100L)
        assertEquals("", template.content)
        val actionTag = template.tags.firstOrNull { it.firstOrNull() == AdminCommandEvent.ACTION_TAG }
        assertEquals(AdminCommandEvent.Action.MUTE.code, actionTag?.getOrNull(1))
    }

    @Test
    fun parseRoundTripExposesRoomTargetAction() {
        val template = AdminCommandEvent.kick(roomATag, target, createdAt = 100L)
        val event =
            AdminCommandEvent(
                id = "0".repeat(64),
                pubKey = host,
                createdAt = template.createdAt,
                tags = template.tags,
                content = template.content,
                sig = "0".repeat(128),
            )
        assertEquals(AdminCommandEvent.Action.KICK, event.action())
        assertEquals(target, event.targetPubkey())
        assertTrue(event.room()!!.startsWith("30312:"))
    }

    @Test
    fun legacyContentVerbStillParsedForBackwardsCompat() {
        // An Amethyst build briefly emitted the verb in content. After
        // we switched to the tag form (matching nostrnests / EGG-07)
        // the reader still accepts the old layout so any in-flight
        // event during the rollout window is honored.
        val event =
            AdminCommandEvent(
                id = "0".repeat(64),
                pubKey = host,
                createdAt = 100L,
                tags = arrayOf(arrayOf("a", "30312:host:rt"), arrayOf("p", target)),
                content = "kick",
                sig = "0".repeat(128),
            )
        assertEquals(AdminCommandEvent.Action.KICK, event.action())
    }

    @Test
    fun actionTagWinsOverContentWhenBothPresent() {
        // Defensive: if both forms are present (e.g. mixed-source
        // event), the tag — the spec-correct location — is preferred.
        val event =
            AdminCommandEvent(
                id = "0".repeat(64),
                pubKey = host,
                createdAt = 100L,
                tags = arrayOf(arrayOf("a", "30312:host:rt"), arrayOf("p", target), arrayOf("action", "mute")),
                content = "kick",
                sig = "0".repeat(128),
            )
        assertEquals(AdminCommandEvent.Action.MUTE, event.action())
    }

    @Test
    fun unknownActionReturnsNull() {
        val event =
            AdminCommandEvent(
                id = "0".repeat(64),
                pubKey = host,
                createdAt = 100L,
                tags = arrayOf(arrayOf("a", "30312:host:rt"), arrayOf("p", target), arrayOf("action", "haunt")),
                content = "",
                sig = "0".repeat(128),
            )
        assertNull(event.action())
    }

    @Test
    fun missingTagsReturnNullAccessors() {
        val event =
            AdminCommandEvent(
                id = "0".repeat(64),
                pubKey = host,
                createdAt = 100L,
                tags = arrayOf(),
                content = "kick",
                sig = "0".repeat(128),
            )
        assertNull(event.targetPubkey())
        assertNull(event.room())
    }
}
