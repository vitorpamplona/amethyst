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
package com.vitorpamplona.quartz.buzz.stream

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SystemMessageEventTest {
    private val channelId = "3f2504e0-4f89-41d3-9a0c-0305e82c3301"
    private val actor = "aaaa000000000000000000000000000000000000000000000000000000000001"
    private val target = "bbbb000000000000000000000000000000000000000000000000000000000002"

    @Test
    fun buildEncodesPayloadAsJsonContent() {
        val payload = SystemMessagePayload(type = "member_joined", actor = actor, target = target)
        val template =
            SystemMessageEvent.build(
                channelId = channelId,
                payload = payload,
                createdAt = 1_700_000_000L,
            )

        assertEquals(SystemMessageEvent.KIND, template.kind)
        assertEquals(40099, template.kind)
        assertEquals(channelId, template.tags.single { it[0] == "h" }[1])

        val decoded = SystemMessagePayload.decodeFromJson(template.content)
        assertEquals("member_joined", decoded.type)
        assertEquals(actor, decoded.actor)
        assertEquals(target, decoded.target)
    }

    @Test
    fun payloadAccessorDecodesVariantFields() {
        val json = """{"type":"ttl_changed","actor":"$actor","ttl_seconds":3600}"""
        val event =
            SystemMessageEvent(
                id = "00",
                pubKey = "00",
                createdAt = 0L,
                tags = arrayOf(arrayOf("h", channelId)),
                content = json,
                sig = "00",
            )

        assertEquals(channelId, event.channel())
        val payload = event.payload()
        assertNotNull(payload)
        assertEquals("ttl_changed", payload.type)
        assertEquals(actor, payload.actor)
        assertEquals(3600L, payload.ttlSeconds)
    }

    /**
     * Every variant the relay emits, with the exact JSON shape from `emit_system_message`'s callers
     * in `crates/buzz-relay/src/handlers/`. The UI words each one differently — "was added by" vs
     * "joined", "made this private" vs a raw token — so a field silently dropping to null here is a
     * sentence losing its subject on screen.
     */
    @Test
    fun everyRelayVariantKeepsItsFields() {
        fun decode(json: String) = SystemMessagePayload.decodeFromJson(json)

        val added = decode("""{"type":"member_joined","actor":"$actor","target":"$target"}""")
        assertEquals(SystemMessagePayload.MEMBER_JOINED, added.type)
        assertEquals(target, added.target)

        // The self-join path sends the SAME type with actor == target; that equality is the only
        // thing separating "Bob joined" from "Bob was added by Alice".
        val selfJoin = decode("""{"type":"member_joined","actor":"$actor","target":"$actor"}""")
        assertEquals(selfJoin.actor, selfJoin.target)

        // The explicit-leave path omits `target` entirely.
        val left = decode("""{"type":"member_left","actor":"$actor"}""")
        assertEquals(null, left.target)

        val removed = decode("""{"type":"member_removed","actor":"$actor","target":"$target"}""")
        assertEquals(target, removed.target)

        assertEquals("Standup", decode("""{"type":"topic_changed","actor":"$actor","topic":"Standup"}""").topic)
        assertEquals("Ship it", decode("""{"type":"purpose_changed","actor":"$actor","purpose":"Ship it"}""").purpose)

        val private = decode("""{"type":"visibility_changed","actor":"$actor","visibility":"private"}""")
        assertEquals(SystemMessagePayload.VISIBILITY_PRIVATE, private.visibility)
        val open = decode("""{"type":"visibility_changed","actor":"$actor","visibility":"open"}""")
        assertEquals(SystemMessagePayload.VISIBILITY_OPEN, open.visibility)

        // Clearing the TTL sends an explicit null, which must read as "no TTL", not as a parse failure.
        assertEquals(null, decode("""{"type":"ttl_changed","actor":"$actor","ttl_seconds":null}""").ttlSeconds)
        assertEquals(604800L, decode("""{"type":"ttl_changed","actor":"$actor","ttl_seconds":604800}""").ttlSeconds)

        for (type in listOf("channel_archived", "channel_unarchived", "channel_created", "channel_deleted")) {
            assertEquals(actor, decode("""{"type":"$type","actor":"$actor"}""").actor)
        }

        val deleted =
            decode(
                """{"type":"message_deleted","actor":"$actor","target_event_id":"cc","action_id":"a1","reason_code":"spam","public_reason":"off topic"}""",
            )
        assertEquals("cc", deleted.targetEventId)
        assertEquals("a1", deleted.actionId)
        assertEquals("spam", deleted.reasonCode)
        assertEquals("off topic", deleted.publicReason)

        val dm = decode("""{"type":"dm_created","actor":"$actor","participants":["$actor","$target"]}""")
        assertEquals(listOf(actor, target), dm.participants)
    }

    /** The avatar shown beside the line: the member a membership change is about, the actor otherwise. */
    @Test
    fun subjectIsTheMemberForMembershipChangesAndTheActorOtherwise() {
        assertEquals(target, SystemMessagePayload(type = SystemMessagePayload.MEMBER_JOINED, actor = actor, target = target).subject())
        assertEquals(target, SystemMessagePayload(type = SystemMessagePayload.MEMBER_REMOVED, actor = actor, target = target).subject())
        assertEquals(actor, SystemMessagePayload(type = SystemMessagePayload.MEMBER_LEFT, actor = actor).subject())
        assertEquals(actor, SystemMessagePayload(type = SystemMessagePayload.VISIBILITY_CHANGED, actor = actor, visibility = "open").subject())
        assertEquals(actor, SystemMessagePayload(type = SystemMessagePayload.TOPIC_CHANGED, actor = actor, topic = "x").subject())
    }

    /** An unknown type or extra key must survive as a line, not blow up the whole message. */
    @Test
    fun unknownTypeAndUnknownKeysStillParse() {
        val payload = SystemMessagePayload.decodeFromJson("""{"type":"pinned_changed","actor":"$actor","brand_new":"x"}""")
        assertEquals("pinned_changed", payload.type)
        assertEquals(actor, payload.actor)
    }
}
