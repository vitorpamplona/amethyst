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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The JSON content of a Buzz [SystemMessageEvent] (`kind:40099`) — a relay-authored
 * record of a channel state change (join, leave, rename, archive, delete, ...).
 *
 * Field names mirror the `serde_json::json!` payloads emitted by `emit_system_message`
 * and its callers in Buzz's `crates/buzz-relay/src/handlers/`. Only [type] is always
 * present; [actor] is present on every variant the relay emits today, and the rest are
 * variant-specific. Unknown keys are ignored for forward compatibility, so a relay that
 * grows a new field or a new [type] degrades to a plain line instead of failing to parse.
 *
 * The complete vocabulary, read off `side_effects.rs` / `command_executor.rs`:
 *
 * | [type] | carries | emitted when |
 * |--|--|--|
 * | [MEMBER_JOINED] | [actor], [target] | someone was added ([actor] added [target]) or joined on their own ([actor] == [target]) |
 * | [MEMBER_LEFT] | [actor], optional [target] | a member left; the explicit-leave path omits [target] |
 * | [MEMBER_REMOVED] | [actor], [target] | [actor] removed [target] |
 * | [TOPIC_CHANGED] | [actor], [topic] | the channel topic was set |
 * | [PURPOSE_CHANGED] | [actor], [purpose] | the channel purpose was set |
 * | [VISIBILITY_CHANGED] | [actor], [visibility] | flipped between [VISIBILITY_OPEN] and [VISIBILITY_PRIVATE] |
 * | [TTL_CHANGED] | [actor], [ttlSeconds] | disappearing messages set; `null` [ttlSeconds] means cleared (permanent) |
 * | [CHANNEL_ARCHIVED] / [CHANNEL_UNARCHIVED] | [actor] | archive flag flipped |
 * | [CHANNEL_CREATED] / [CHANNEL_DELETED] | [actor] | channel lifecycle |
 * | [MESSAGE_DELETED] | [actor], [targetEventId], optional [actionId] / [reasonCode] / [publicReason] | a message was deleted (moderation tombstone) |
 * | [DM_CREATED] | [actor], [participants] | a DM channel was opened |
 */
@Serializable
data class SystemMessagePayload(
    val type: String,
    val actor: String? = null,
    val target: String? = null,
    val topic: String? = null,
    val purpose: String? = null,
    val visibility: String? = null,
    @SerialName("ttl_seconds") val ttlSeconds: Long? = null,
    /** The deleted message's id on [MESSAGE_DELETED]. */
    @SerialName("target_event_id") val targetEventId: String? = null,
    /** Moderation-action id linking the tombstone back to the action that caused it. */
    @SerialName("action_id") val actionId: String? = null,
    /** Machine-readable moderation reason (e.g. `spam`), when the deleter gave one. */
    @SerialName("reason_code") val reasonCode: String? = null,
    /** Human-readable moderation reason the relay is willing to show everyone. */
    @SerialName("public_reason") val publicReason: String? = null,
    /** Every participant of a newly opened DM, [actor] included. */
    val participants: List<String>? = null,
) {
    fun encodeToJson(): String = JSON.encodeToString(this)

    /**
     * The pubkey the sentence is *about* — whose avatar to show. Membership changes are about the
     * member who joined/left/was removed; everything else is about whoever performed the action.
     */
    fun subject(): String? =
        when (type) {
            MEMBER_JOINED, MEMBER_LEFT, MEMBER_REMOVED -> target ?: actor
            else -> actor
        }

    companion object {
        val JSON =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            }

        fun decodeFromJson(json: String): SystemMessagePayload = JSON.decodeFromString(json)

        const val MEMBER_JOINED = "member_joined"
        const val MEMBER_LEFT = "member_left"
        const val MEMBER_REMOVED = "member_removed"
        const val TOPIC_CHANGED = "topic_changed"
        const val PURPOSE_CHANGED = "purpose_changed"
        const val VISIBILITY_CHANGED = "visibility_changed"
        const val TTL_CHANGED = "ttl_changed"
        const val CHANNEL_ARCHIVED = "channel_archived"
        const val CHANNEL_UNARCHIVED = "channel_unarchived"
        const val CHANNEL_CREATED = "channel_created"
        const val CHANNEL_DELETED = "channel_deleted"
        const val MESSAGE_DELETED = "message_deleted"
        const val DM_CREATED = "dm_created"

        /** Searchable, anyone can join. */
        const val VISIBILITY_OPEN = "open"

        /** Hidden, invite-only. */
        const val VISIBILITY_PRIVATE = "private"
    }
}
