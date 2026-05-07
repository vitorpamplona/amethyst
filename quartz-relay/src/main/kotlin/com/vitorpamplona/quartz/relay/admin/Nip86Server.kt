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
                    result(buildJsonArray { supportedMethods.forEach { add(JsonPrimitive(it)) } })
                }

                Nip86Method.BAN_PUBKEY -> {
                    val (pk, reason) = req.params.stringPair() ?: return malformed("expected [pubkey, reason?]")
                    if (!isHex64(pk)) return malformed("pubkey must be 64-char hex")
                    banStore.banPubkey(pk, reason)
                    result(JsonPrimitive(true))
                }

                Nip86Method.UNBAN_PUBKEY -> {
                    val (pk, _) = req.params.stringPair() ?: return malformed("expected [pubkey]")
                    if (!isHex64(pk)) return malformed("pubkey must be 64-char hex")
                    banStore.unbanPubkey(pk)
                    result(JsonPrimitive(true))
                }

                Nip86Method.LIST_BANNED_PUBKEYS -> {
                    result(
                        banStore
                            .listBannedPubkeys()
                            .map { (pk, r) -> BannedPubkey(pk, r) }
                            .toJsonArray(BannedPubkey.serializer()),
                    )
                }

                Nip86Method.ALLOW_PUBKEY -> {
                    val (pk, reason) = req.params.stringPair() ?: return malformed("expected [pubkey, reason?]")
                    if (!isHex64(pk)) return malformed("pubkey must be 64-char hex")
                    banStore.allowPubkey(pk, reason)
                    result(JsonPrimitive(true))
                }

                Nip86Method.UNALLOW_PUBKEY -> {
                    val (pk, _) = req.params.stringPair() ?: return malformed("expected [pubkey]")
                    if (!isHex64(pk)) return malformed("pubkey must be 64-char hex")
                    banStore.unallowPubkey(pk)
                    result(JsonPrimitive(true))
                }

                Nip86Method.LIST_ALLOWED_PUBKEYS -> {
                    result(
                        banStore
                            .listAllowedPubkeys()
                            .map { (pk, r) -> AllowedPubkey(pk, r) }
                            .toJsonArray(AllowedPubkey.serializer()),
                    )
                }

                Nip86Method.BAN_EVENT -> {
                    val (id, reason) = req.params.stringPair() ?: return malformed("expected [event_id, reason?]")
                    if (!isHex64(id)) return malformed("event_id must be 64-char hex")
                    banStore.banEvent(id, reason)
                    // Also remove the event from the store if it's there.
                    store?.delete(Filter(ids = listOf(id)))
                    result(JsonPrimitive(true))
                }

                Nip86Method.ALLOW_EVENT -> {
                    val (id, _) = req.params.stringPair() ?: return malformed("expected [event_id]")
                    if (!isHex64(id)) return malformed("event_id must be 64-char hex")
                    banStore.allowEvent(id)
                    result(JsonPrimitive(true))
                }

                Nip86Method.LIST_BANNED_EVENTS -> {
                    result(
                        banStore
                            .listBannedEvents()
                            .map { (id, r) -> BannedEvent(id, r) }
                            .toJsonArray(BannedEvent.serializer()),
                    )
                }

                Nip86Method.ALLOW_KIND -> {
                    val k = req.params.firstInt() ?: return malformed("expected [kind]")
                    banStore.allowKind(k)
                    result(JsonPrimitive(true))
                }

                Nip86Method.DISALLOW_KIND -> {
                    val k = req.params.firstInt() ?: return malformed("expected [kind]")
                    banStore.disallowKind(k)
                    result(JsonPrimitive(true))
                }

                Nip86Method.LIST_ALLOWED_KINDS -> {
                    result(buildJsonArray { banStore.listAllowedKinds().forEach { add(JsonPrimitive(it)) } })
                }

                Nip86Method.CHANGE_RELAY_NAME -> {
                    val name = req.params.firstString() ?: return malformed("expected [name]")
                    rewriteInfo { it.copy(name = name) }
                    result(JsonPrimitive(true))
                }

                Nip86Method.CHANGE_RELAY_DESCRIPTION -> {
                    val desc = req.params.firstString() ?: return malformed("expected [description]")
                    rewriteInfo { it.copy(description = desc) }
                    result(JsonPrimitive(true))
                }

                Nip86Method.CHANGE_RELAY_ICON -> {
                    val icon = req.params.firstString() ?: return malformed("expected [icon_url]")
                    rewriteInfo { it.copy(icon = icon) }
                    result(JsonPrimitive(true))
                }

                else -> {
                    Nip86Response(error = "method not supported: ${req.method}")
                }
            }
        }.getOrElse { e ->
            // CancellationException must propagate so structured
            // concurrency works — swallowing it would let a parent
            // cancellation be reported as a benign RPC error.
            if (e is kotlinx.coroutines.CancellationException) throw e
            Nip86Response(error = "internal: ${e.message ?: e::class.simpleName}")
        }

    private fun rewriteInfo(transform: (Nip11RelayInformation) -> Nip11RelayInformation) {
        val current = infoHolder.get().document
        infoHolder.set(RelayInfo(transform(current)))
    }

    /** [Nip11RelayInformation] is not a `data class`; do a manual field-by-field copy. */
    private fun Nip11RelayInformation.copy(
        name: String? = this.name,
        description: String? = this.description,
        icon: String? = this.icon,
    ) = Nip11RelayInformation(
        id = this.id,
        name = name,
        description = description,
        icon = icon,
        pubkey = this.pubkey,
        self = this.self,
        contact = this.contact,
        supported_nips = this.supported_nips,
        supported_nip_extensions = this.supported_nip_extensions,
        software = this.software,
        version = this.version,
        limitation = this.limitation,
        relay_countries = this.relay_countries,
        language_tags = this.language_tags,
        tags = this.tags,
        posting_policy = this.posting_policy,
        privacy_policy = this.privacy_policy,
        terms_of_service = this.terms_of_service,
        payments_url = this.payments_url,
        retention = this.retention,
        fees = this.fees,
        nip50 = this.nip50,
        supported_grasps = this.supported_grasps,
    )
}

private fun malformed(reason: String) = Nip86Response(error = "invalid params: $reason")

private fun result(j: JsonElement) = Nip86Response(result = j, error = null)

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

private val HEX64 = Regex("[0-9a-fA-F]{64}")

private fun isHex64(s: String): Boolean = HEX64.matches(s)
