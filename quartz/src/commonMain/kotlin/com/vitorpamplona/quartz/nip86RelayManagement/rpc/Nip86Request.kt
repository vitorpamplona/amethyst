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

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray

@Serializable
class Nip86Request(
    val method: String,
    // NIP-86 requests always carry `params`, even when empty (`[]`).
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
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

        /**
         * NIP-86 `createrole`: `[id, label, description, color, order]`. Every
         * position is always sent so the relay can read them positionally; a
         * missing optional value goes out as JSON `null`. [color] is a hue
         * (0..360) and [order] a display-only sort key, both sent as numbers.
         */
        fun createRole(
            id: String,
            label: String? = null,
            description: String? = null,
            color: Int? = null,
            order: Int? = null,
        ) = Nip86Request(
            method = Nip86Method.CREATE_ROLE,
            params = roleParams(id, label, description, color, order),
        )

        /** NIP-86 `editrole`: same `[id, label, description, color, order]` shape as [createRole]. */
        fun editRole(
            id: String,
            label: String? = null,
            description: String? = null,
            color: Int? = null,
            order: Int? = null,
        ) = Nip86Request(
            method = Nip86Method.EDIT_ROLE,
            params = roleParams(id, label, description, color, order),
        )

        fun deleteRole(id: String) =
            Nip86Request(
                method = Nip86Method.DELETE_ROLE,
                params = buildJsonArray { add(JsonPrimitive(id)) },
            )

        fun assignRole(
            pubkey: String,
            roleId: String,
        ) = Nip86Request(
            method = Nip86Method.ASSIGN_ROLE,
            params = buildParams(pubkey, roleId),
        )

        fun unassignRole(
            pubkey: String,
            roleId: String,
        ) = Nip86Request(
            method = Nip86Method.UNASSIGN_ROLE,
            params = buildParams(pubkey, roleId),
        )

        fun listClaims() =
            Nip86Request(
                method = Nip86Method.LIST_CLAIMS,
            )

        fun createClaim(claim: String) =
            Nip86Request(
                method = Nip86Method.CREATE_CLAIM,
                params = buildJsonArray { add(JsonPrimitive(claim)) },
            )

        fun deleteClaim(claim: String) =
            Nip86Request(
                method = Nip86Method.DELETE_CLAIM,
                params = buildJsonArray { add(JsonPrimitive(claim)) },
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

        fun unallowEvent(
            eventId: String,
            reason: String? = null,
        ) = Nip86Request(
            method = Nip86Method.UNALLOW_EVENT,
            params = buildParams(eventId, reason),
        )

        fun banEvent(
            eventId: String,
            reason: String? = null,
        ) = Nip86Request(
            method = Nip86Method.BAN_EVENT,
            params = buildParams(eventId, reason),
        )

        fun unbanEvent(
            eventId: String,
            reason: String? = null,
        ) = Nip86Request(
            method = Nip86Method.UNBAN_EVENT,
            params = buildParams(eventId, reason),
        )

        fun listBannedEvents() =
            Nip86Request(
                method = Nip86Method.LIST_BANNED_EVENTS,
            )

        fun listAllowedEvents() =
            Nip86Request(
                method = Nip86Method.LIST_ALLOWED_EVENTS,
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

        fun listDisallowedKinds() =
            Nip86Request(
                method = Nip86Method.LIST_DISALLOWED_KINDS,
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

        private fun roleParams(
            id: String,
            label: String?,
            description: String?,
            color: Int?,
            order: Int?,
        ): JsonArray =
            buildJsonArray {
                add(JsonPrimitive(id))
                add(label?.let { JsonPrimitive(it) } ?: JsonNull)
                add(description?.let { JsonPrimitive(it) } ?: JsonNull)
                add(color?.let { JsonPrimitive(it) } ?: JsonNull)
                add(order?.let { JsonPrimitive(it) } ?: JsonNull)
            }

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
