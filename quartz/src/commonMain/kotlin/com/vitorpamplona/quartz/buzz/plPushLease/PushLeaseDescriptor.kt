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
package com.vitorpamplona.quartz.buzz.plPushLease

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * The decrypted plaintext of a Buzz Push Lease (NIP-PL, `kind:30350`).
 *
 * Field names mirror `LeasePlaintext` in `buzz-relay/src/handlers/push_lease.rs` (snake_case
 * on the wire — note [appProfile] serializes as `app_profile`). When [active] is false the
 * transport fields ([appProfile], [transport], [endpoint], [subscriptions]) may be omitted.
 * Unknown JSON fields are ignored for forward compatibility.
 */
@Serializable
data class PushLeaseDescriptor(
    val v: Long,
    val origin: String,
    val generation: Long,
    val active: Boolean,
    @SerialName("app_profile") val appProfile: String? = null,
    val transport: String? = null,
    val endpoint: String? = null,
    val subscriptions: List<PushSubscription>? = null,
) {
    fun encodeToJson(): String = JSON.encodeToString(this)

    companion object {
        val JSON =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            }

        fun decodeFromJson(json: String): PushLeaseDescriptor = JSON.decodeFromString(json)
    }
}

/**
 * One push subscription inside a lease. [filter] is a raw Nostr filter object, [className]
 * (wire `class`) names the notification class, [ignore] holds sub-filters to exclude, and
 * [suppress] optionally caps noisy fan-out. Mirrors `Subscription` in `push_lease.rs`.
 */
@Serializable
data class PushSubscription(
    val filter: JsonObject,
    @SerialName("class") val className: String,
    val ignore: List<JsonObject> = emptyList(),
    val suppress: PushSuppress? = null,
)

/** Suppression policy for a subscription. Mirrors `Suppress` in `push_lease.rs`. */
@Serializable
data class PushSuppress(
    @SerialName("p_tags_max") val pTagsMax: Long,
)
