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
package com.vitorpamplona.quartz.nip86RelayManagement.server

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.AllowedPubkey
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.BannedEvent
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.BannedPubkey
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.Nip86Method
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.Nip86Request
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.Nip86Response
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
 * Server-side dispatcher for the NIP-86 relay management API.
 *
 * Holds the [BanStore] (mutated by ban/allow methods), an [InfoHolder]
 * for the live NIP-11 doc (mutated by `changerelay*` methods, which
 * atomically swap it), an [onBan] hook so each ban method can
 * retroactively purge matching events from the relay's event store,
 * and the [allowList] of admin pubkeys authorized to call admin RPCs.
 *
 * Transport-agnostic — relay implementations call [dispatch] from
 * whatever HTTP route they expose (e.g. POST `application/nostr+json+rpc`),
 * and in-process tests can build a [Nip86Request] directly. The
 * [allowList] check is enforced inside [dispatch] so no caller can
 * accidentally bypass it; transports may also use [isAuthorized] to
 * make the decision before dispatching (e.g. to short-circuit the
 * request parse).
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
    /**
     * Called from `banpubkey` / `banevent` / `disallowkind` AFTER the
     * [BanStore] has been updated, with a [Filter] selecting all events
     * the operator just banned: respectively `Filter(authors=[pk])`,
     * `Filter(ids=[id])`, `Filter(kinds=[k])`. Production callers wire
     * this to `IEventStore.delete(filter)` so the existing offending
     * events are removed from storage, not just blocked from re-ingest.
     * Without it, a ban would only stop *future* matches while clients
     * kept seeing whatever was already there — important for spam
     * cleanup, and a real safety issue for `banevent` on illegal /
     * leaked content.
     *
     * Defaults to a no-op so unit tests for the dispatcher don't need
     * to stand up an event store. Production callers (e.g.
     * `geode/KtorRelay`) wire it to `store.delete(filter)`.
     */
    private val onBan: suspend (Filter) -> Unit = {},
    /**
     * Pubkeys allowed to invoke admin RPCs. Empty effectively disables
     * the admin API: [isAuthorized] returns false for every pubkey
     * and [dispatch] rejects everything as `not authorized`. Compared
     * case-insensitively (lowercased on entry).
     */
    allowList: Set<HexKey> = emptySet(),
) {
    private val allowList: Set<HexKey> = allowList.mapTo(HashSet()) { it.lowercase() }

    /** True when [pubkey] is on the admin allow-list. Case-insensitive. */
    fun isAuthorized(pubkey: HexKey): Boolean = pubkey.lowercase() in allowList

    /** Pluggable container so the relay's NIP-11 doc can be swapped at runtime. */
    interface InfoHolder {
        fun get(): Nip11RelayInformation

        fun set(info: Nip11RelayInformation)
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
     * Dispatches a single RPC request from [pubkey] (the caller, as
     * authenticated by the transport — NIP-98 over HTTP, NIP-42 over
     * WS, or in-process trust).
     *
     * If [pubkey] is not in [allowList], returns a `not authorized`
     * error response without executing anything. Transports that want
     * to short-circuit before parsing the request can pre-check via
     * [isAuthorized].
     */
    suspend fun dispatch(
        pubkey: HexKey,
        req: Nip86Request,
    ): Nip86Response {
        if (!isAuthorized(pubkey)) {
            return Nip86Response(error = "pubkey is not on the admin list")
        }
        return runCatching {
            when (req.method) {
                Nip86Method.SUPPORTED_METHODS -> {
                    ok(buildJsonArray { supportedMethods.forEach { add(JsonPrimitive(it)) } })
                }

                Nip86Method.BAN_PUBKEY -> {
                    withHexAndReason(req, "pubkey") { pk, reason ->
                        banStore.banPubkey(pk, reason)
                        // Purge everything the banned author had posted
                        // so REQ stops serving their history.
                        onBan(Filter(authors = listOf(pk)))
                    }
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
                        // Also remove the event from the relay's event
                        // store so REQ stops serving the offending copy.
                        onBan(Filter(ids = listOf(id)))
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
                    withInt(req, "kind") { k ->
                        banStore.disallowKind(k)
                        // Purge every event of the now-disallowed kind.
                        onBan(Filter(kinds = listOf(k)))
                    }
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

    private suspend inline fun withInt(
        req: Nip86Request,
        label: String,
        action: suspend (Int) -> Unit,
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
        infoHolder.set(transform(infoHolder.get()))
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
