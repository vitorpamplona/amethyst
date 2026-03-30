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
package com.vitorpamplona.quartz.nip86RelayManagement.rpc

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray

@Serializable
class Nip86Request(
    val method: String,
    val params: JsonArray = JsonArray(emptyList()),
) {
    companion object {
        fun supportedMethods() =
            Nip86Request(
                method = Nip86Method.SUPPORTED_METHODS,
            )

        fun banPubkey(
            pubkey: String,
            reason: String? = null,
        ) = Nip86Request(
            method = Nip86Method.BAN_PUBKEY,
            params = buildParams(pubkey, reason),
        )

        fun unbanPubkey(
            pubkey: String,
            reason: String? = null,
        ) = Nip86Request(
            method = Nip86Method.UNBAN_PUBKEY,
            params = buildParams(pubkey, reason),
        )

        fun listBannedPubkeys() =
            Nip86Request(
                method = Nip86Method.LIST_BANNED_PUBKEYS,
            )

        fun allowPubkey(
            pubkey: String,
            reason: String? = null,
        ) = Nip86Request(
            method = Nip86Method.ALLOW_PUBKEY,
            params = buildParams(pubkey, reason),
        )

        fun unallowPubkey(
            pubkey: String,
            reason: String? = null,
        ) = Nip86Request(
            method = Nip86Method.UNALLOW_PUBKEY,
            params = buildParams(pubkey, reason),
        )

        fun listAllowedPubkeys() =
            Nip86Request(
                method = Nip86Method.LIST_ALLOWED_PUBKEYS,
            )

        fun listEventsNeedingModeration() =
            Nip86Request(
                method = Nip86Method.LIST_EVENTS_NEEDING_MODERATION,
            )

        fun allowEvent(
            eventId: String,
            reason: String? = null,
        ) = Nip86Request(
            method = Nip86Method.ALLOW_EVENT,
            params = buildParams(eventId, reason),
        )

        fun banEvent(
            eventId: String,
            reason: String? = null,
        ) = Nip86Request(
            method = Nip86Method.BAN_EVENT,
            params = buildParams(eventId, reason),
        )

        fun listBannedEvents() =
            Nip86Request(
                method = Nip86Method.LIST_BANNED_EVENTS,
            )

        fun changeRelayName(newName: String) =
            Nip86Request(
                method = Nip86Method.CHANGE_RELAY_NAME,
                params = buildJsonArray { add(JsonPrimitive(newName)) },
            )

        fun changeRelayDescription(newDescription: String) =
            Nip86Request(
                method = Nip86Method.CHANGE_RELAY_DESCRIPTION,
                params = buildJsonArray { add(JsonPrimitive(newDescription)) },
            )

        fun changeRelayIcon(newIconUrl: String) =
            Nip86Request(
                method = Nip86Method.CHANGE_RELAY_ICON,
                params = buildJsonArray { add(JsonPrimitive(newIconUrl)) },
            )

        fun allowKind(kind: Int) =
            Nip86Request(
                method = Nip86Method.ALLOW_KIND,
                params = buildJsonArray { add(JsonPrimitive(kind)) },
            )

        fun disallowKind(kind: Int) =
            Nip86Request(
                method = Nip86Method.DISALLOW_KIND,
                params = buildJsonArray { add(JsonPrimitive(kind)) },
            )

        fun listAllowedKinds() =
            Nip86Request(
                method = Nip86Method.LIST_ALLOWED_KINDS,
            )

        fun blockIp(
            ip: String,
            reason: String? = null,
        ) = Nip86Request(
            method = Nip86Method.BLOCK_IP,
            params = buildParams(ip, reason),
        )

        fun unblockIp(ip: String) =
            Nip86Request(
                method = Nip86Method.UNBLOCK_IP,
                params = buildJsonArray { add(JsonPrimitive(ip)) },
            )

        fun listBlockedIps() =
            Nip86Request(
                method = Nip86Method.LIST_BLOCKED_IPS,
            )

        private fun buildParams(
            primary: String,
            reason: String? = null,
        ): JsonArray =
            buildJsonArray {
                add(JsonPrimitive(primary))
                reason?.let { add(JsonPrimitive(it)) }
            }
    }
}
