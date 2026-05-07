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
package com.vitorpamplona.quartz.relay.admin

import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.AllowedPubkey
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.BannedEvent
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.BannedPubkey
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.Nip86Method
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.Nip86Request
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.Nip86Response
import com.vitorpamplona.quartz.relay.RelayInfo
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.int

/**
 * NIP-86 RPC dispatcher. Holds the [BanStore] (mutated by ban/allow
 * methods), the live [RelayInfo] handle (mutated by `changerelay*`
 * methods, which atomically swap the doc), and the underlying
 * [IEventStore] so `banevent` can also delete the offending event.
 *
 * The dispatcher is transport-agnostic — `LocalRelayServer` calls
 * [dispatch] from its HTTP route, but the same handler also works for
 * in-process tests that build a [Nip86Request] directly.
 *
 * [supportedMethods] is the canonical list this server actually
 * implements; methods returned outside of it are no-ops and a NIP-86
 * client must not advertise them.
 */
class Nip86Server(
    val banStore: BanStore,
    /**
     * Read-write access to the relay's NIP-11 info doc. The dispatcher
     * mutates this when an admin calls `changerelayname` /
     * `changerelaydescription` / `changerelayicon`. Relay code reading
     * the doc (e.g. the NIP-11 endpoint) must consult this object on
     * every request, not cache it.
     */
    private val infoHolder: InfoHolder,
    private val store: IEventStore? = null,
) {
    /** Pluggable container so the relay's NIP-11 doc can be swapped at runtime. */
    interface InfoHolder {
        fun get(): RelayInfo

        fun set(info: RelayInfo)
    }

    val supportedMethods: List<String> =
        listOf(
            Nip86Method.SUPPORTED_METHODS,
            Nip86Method.BAN_PUBKEY,
            Nip86Method.UNBAN_PUBKEY,
            Nip86Method.LIST_BANNED_PUBKEYS,
            Nip86Method.ALLOW_PUBKEY,
            Nip86Method.UNALLOW_PUBKEY,
            Nip86Method.LIST_ALLOWED_PUBKEYS,
            Nip86Method.BAN_EVENT,
            Nip86Method.ALLOW_EVENT,
            Nip86Method.LIST_BANNED_EVENTS,
            Nip86Method.ALLOW_KIND,
            Nip86Method.DISALLOW_KIND,
            Nip86Method.LIST_ALLOWED_KINDS,
            Nip86Method.CHANGE_RELAY_NAME,
            Nip86Method.CHANGE_RELAY_DESCRIPTION,
            Nip86Method.CHANGE_RELAY_ICON,
        )

    /**
     * Dispatches a single RPC request. Synchronous-looking but does
     * suspend internally for the `banevent` event-store delete path.
     */
    suspend fun dispatch(req: Nip86Request): Nip86Response =
        runCatching {
            when (req.method) {
                Nip86Method.SUPPORTED_METHODS -> {
                    ok(buildJsonArray { supportedMethods.forEach { add(JsonPrimitive(it)) } })
                }

                Nip86Method.BAN_PUBKEY -> {
                    withHexAndReason(req, "pubkey") { pk, reason -> banStore.banPubkey(pk, reason) }
                }

                Nip86Method.UNBAN_PUBKEY -> {
                    withHex(req, "pubkey") { pk -> banStore.unbanPubkey(pk) }
                }

                Nip86Method.LIST_BANNED_PUBKEYS -> {
                    ok(banStore.listBannedPubkeys().map { (pk, r) -> BannedPubkey(pk, r) }.toJsonArray(BannedPubkey.serializer()))
                }

                Nip86Method.ALLOW_PUBKEY -> {
                    withHexAndReason(req, "pubkey") { pk, reason -> banStore.allowPubkey(pk, reason) }
                }

                Nip86Method.UNALLOW_PUBKEY -> {
                    withHex(req, "pubkey") { pk -> banStore.unallowPubkey(pk) }
                }

                Nip86Method.LIST_ALLOWED_PUBKEYS -> {
                    ok(banStore.listAllowedPubkeys().map { (pk, r) -> AllowedPubkey(pk, r) }.toJsonArray(AllowedPubkey.serializer()))
                }

                Nip86Method.BAN_EVENT -> {
                    withHexAndReason(req, "event_id") { id, reason ->
                        banStore.banEvent(id, reason)
                        // Also remove the event from the store if present.
                        store?.delete(Filter(ids = listOf(id)))
                    }
                }

                Nip86Method.ALLOW_EVENT -> {
                    withHex(req, "event_id") { id -> banStore.allowEvent(id) }
                }

                Nip86Method.LIST_BANNED_EVENTS -> {
                    ok(banStore.listBannedEvents().map { (id, r) -> BannedEvent(id, r) }.toJsonArray(BannedEvent.serializer()))
                }

                Nip86Method.ALLOW_KIND -> {
                    withInt(req, "kind") { k -> banStore.allowKind(k) }
                }

                Nip86Method.DISALLOW_KIND -> {
                    withInt(req, "kind") { k -> banStore.disallowKind(k) }
                }

                Nip86Method.LIST_ALLOWED_KINDS -> {
                    ok(buildJsonArray { banStore.listAllowedKinds().forEach { add(JsonPrimitive(it)) } })
                }

                Nip86Method.CHANGE_RELAY_NAME -> {
                    withString(req, "name") { name -> rewriteInfo { it.copy(name = name) } }
                }

                Nip86Method.CHANGE_RELAY_DESCRIPTION -> {
                    withString(req, "description") { desc -> rewriteInfo { it.copy(description = desc) } }
                }

                Nip86Method.CHANGE_RELAY_ICON -> {
                    withString(req, "icon_url") { icon -> rewriteInfo { it.copy(icon = icon) } }
                }

                else -> {
                    Nip86Response(error = "method not supported: ${req.method}")
                }
            }
        }.getOrElse { e ->
            // CancellationException must propagate so structured
            // concurrency works — swallowing it would let a parent
            // cancellation be reported as a benign RPC error.
            if (e is CancellationException) throw e
            Nip86Response(error = "internal: ${e.message ?: e::class.simpleName}")
        }

    private inline fun withHex(
        req: Nip86Request,
        label: String,
        action: (String) -> Unit,
    ): Nip86Response {
        val (value, _) = req.params.stringPair() ?: return malformed("expected [$label]")
        if (!Hex.isHex64(value)) return malformed("$label must be 64-char hex")
        action(value)
        return okTrue
    }

    private suspend inline fun withHexAndReason(
        req: Nip86Request,
        label: String,
        action: suspend (String, String?) -> Unit,
    ): Nip86Response {
        val (value, reason) = req.params.stringPair() ?: return malformed("expected [$label, reason?]")
        if (!Hex.isHex64(value)) return malformed("$label must be 64-char hex")
        action(value, reason)
        return okTrue
    }

    private inline fun withInt(
        req: Nip86Request,
        label: String,
        action: (Int) -> Unit,
    ): Nip86Response {
        val v = req.params.firstInt() ?: return malformed("expected [$label]")
        action(v)
        return okTrue
    }

    private inline fun withString(
        req: Nip86Request,
        label: String,
        action: (String) -> Unit,
    ): Nip86Response {
        val v = req.params.firstString() ?: return malformed("expected [$label]")
        action(v)
        return okTrue
    }

    private fun rewriteInfo(transform: (Nip11RelayInformation) -> Nip11RelayInformation) {
        val current = infoHolder.get().document
        infoHolder.set(RelayInfo(transform(current)))
    }
}

private fun malformed(reason: String) = Nip86Response(error = "invalid params: $reason")

private fun ok(j: JsonElement) = Nip86Response(result = j, error = null)

private val okTrue = ok(JsonPrimitive(true))

private val rpcJson = Json { encodeDefaults = false }

private fun <T> List<T>.toJsonArray(serializer: KSerializer<T>): JsonElement = rpcJson.encodeToJsonElement(ListSerializer(serializer), this)

private fun JsonArray.stringPair(): Pair<String, String?>? {
    val first = (getOrNull(0) as? JsonPrimitive)?.contentOrNull() ?: return null
    val second = (getOrNull(1) as? JsonPrimitive)?.contentOrNull()
    return first to second
}

private fun JsonArray.firstString(): String? = (getOrNull(0) as? JsonPrimitive)?.contentOrNull()

private fun JsonArray.firstInt(): Int? =
    runCatching {
        (this[0] as? JsonPrimitive)?.int
    }.getOrNull()

private fun JsonPrimitive.contentOrNull(): String? = if (this == JsonNull) null else content
