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
package com.vitorpamplona.quartz.buzz.erReminders

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The decrypted plaintext of a Buzz Event Reminder (NIP-ER, `kind:30300`).
 *
 * Field names mirror the NIP-ER content object exactly (all lowercase on the wire).
 * A pending reminder MUST carry either a [target] reference or a non-empty [note];
 * both are optional here so a note-only or a target-only reminder round-trips. Unknown
 * JSON fields are ignored for forward compatibility, and an unrecognized [status] value
 * decodes to null via [statusOrNull] rather than dropping the payload.
 */
@Serializable
data class EventReminderPayload(
    val target: ReminderTarget? = null,
    val status: String,
    val note: String? = null,
) {
    /** Maps the wire [status] to a [ReminderStatus], or null if unrecognized. */
    fun statusOrNull(): ReminderStatus? = ReminderStatus.fromWire(status)

    fun encodeToJson(): String = JSON.encodeToString(this)

    companion object {
        val JSON =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            }

        fun decodeFromJson(json: String): EventReminderPayload = JSON.decodeFromString(json)
    }
}

/**
 * A reference to what the reminder is about. Every field is optional: `a` is the preferred
 * addressable coordinate, `id` a snapshot fallback event id, `relays` are hints only, and
 * `preview` a cached label. Mirrors the `target` object in NIP-ER content.
 */
@Serializable
data class ReminderTarget(
    val id: String? = null,
    val a: String? = null,
    val relays: List<String>? = null,
    val preview: String? = null,
)

/** Reminder lifecycle status. Wire values are lowercase; unknown decodes to null. */
enum class ReminderStatus {
    @SerialName("pending")
    PENDING,

    @SerialName("done")
    DONE,

    @SerialName("cancelled")
    CANCELLED,
    ;

    companion object {
        fun fromWire(value: String): ReminderStatus? =
            when (value) {
                "pending" -> PENDING
                "done" -> DONE
                "cancelled" -> CANCELLED
                else -> null
            }
    }
}
