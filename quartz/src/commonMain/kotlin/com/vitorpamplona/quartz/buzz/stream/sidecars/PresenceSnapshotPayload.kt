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
package com.vitorpamplona.quartz.buzz.stream.sidecars

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Best-effort JSON content of a Buzz [PresenceSnapshotEvent] (`kind:40902`) — bulk
 * presence state for a set of users, signed by the relay as a read-only sidecar.
 *
 * WARNING: `KIND_PRESENCE_SNAPSHOT` is declared in Buzz's `buzz-core/src/kind.rs` as a
 * relay-only sidecar "generated on demand", but the relay's `synthesize_presence`
 * (`buzz-relay/src/api/bridge.rs`) actually answers such queries with individual
 * `kind:20001` presence-update events (content = status, `p` tag = subject) rather than
 * a single 40902 document — no 40902 emitter or content struct exists in the current
 * Buzz source, so this schema is UNCONFIRMED. It is modeled as a list of
 * `(pubkey, status)` entries mirroring the `get_presence_bulk` map; unknown keys are
 * ignored. See report uncertainties.
 */
@Serializable
data class PresenceSnapshotPayload(
    val entries: List<PresenceEntry> = emptyList(),
) {
    fun encodeToJson(): String = JSON.encodeToString(this)

    companion object {
        val JSON =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            }

        fun decodeFromJson(json: String): PresenceSnapshotPayload = JSON.decodeFromString(json)
    }
}

/** One user's presence within a [PresenceSnapshotPayload]. */
@Serializable
data class PresenceEntry(
    val pubkey: String,
    val status: String,
    @SerialName("last_seen_at") val lastSeenAt: Long? = null,
)
