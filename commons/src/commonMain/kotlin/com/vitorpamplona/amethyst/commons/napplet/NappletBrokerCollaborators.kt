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
package com.vitorpamplona.amethyst.commons.napplet

import com.vitorpamplona.amethyst.commons.napplet.permissions.GrantState
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletRequest
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter

/**
 * Asks the user to decide a capability the napplet does not yet have a standing grant for.
 * The implementation drives the consent UI (on Android, an Activity in the **main** process)
 * and suspends until the user answers. Returning [GrantState.DENY] (or any non-allowing
 * state) blocks the in-flight request.
 */
fun interface NappletConsentPrompt {
    suspend fun request(
        identity: NappletIdentity,
        capability: NappletCapability,
        request: NappletRequest,
    ): GrantState
}

/**
 * Bridges the broker to the user's relays for the [NappletCapability.RELAY] capability —
 * publishing the user's events and reading events back. Supplied by the host (an
 * OkHttp/NostrClient-backed implementation in `amethyst`); kept as an interface so the broker
 * stays platform-agnostic and testable. A `null` gateway makes the broker answer relay
 * requests with [com.vitorpamplona.amethyst.commons.napplet.protocol.NappletResponse.Unsupported].
 */
interface NappletRelayGateway {
    /** Publishes [event] and returns the relay URLs that accepted it (empty = nowhere reached). */
    suspend fun publish(event: Event): List<String>

    /** Returns events matching [filter] (e.g. from the local cache and/or a bounded relay fetch). */
    suspend fun query(filter: Filter): List<Event>
}

/**
 * A per-applet sandboxed key-value store for the [NappletCapability.STORAGE] capability. The
 * broker namespaces every call by the applet's coordinate, so one napplet can never read or
 * overwrite another's data — and none of it is the app's own storage.
 */
interface NappletStorage {
    suspend fun get(
        coordinate: String,
        key: String,
    ): String?

    suspend fun set(
        coordinate: String,
        key: String,
        value: String,
    )

    suspend fun remove(
        coordinate: String,
        key: String,
    )

    /** Lists the keys this applet (identified by [coordinate]) has stored. */
    suspend fun keys(coordinate: String): List<String>
}

/**
 * Bridges the broker to the user's wallet for the [NappletCapability.VALUE] capability. A
 * `null` gateway makes value requests answer with `Unsupported` — there is intentionally no
 * default payment path, since a money-moving bridge must be verified end-to-end before it ships.
 */
fun interface NappletWalletGateway {
    /** Pays a BOLT-11 [invoice] and returns the preimage on success, or `null` if unconfirmed. */
    suspend fun payInvoice(invoice: String): String?
}

/** A fetched resource: its [bytes] and best-effort [contentType]. */
class NappletResource(
    val bytes: ByteArray,
    val contentType: String,
)

/**
 * Bridges the broker to sandboxed resource fetching for [NappletCapability.RESOURCE]
 * (`resource.bytes`). The host fetches https/blossom/nostr/data URLs on the applet's behalf —
 * the applet itself has no direct network (CSP `connect-src 'none'`). Returns `null` for an
 * unsupported scheme or a failed fetch.
 */
fun interface NappletResourceGateway {
    suspend fun fetch(url: String): NappletResource?
}

/**
 * Bridges the broker to Blossom upload for [NappletCapability.UPLOAD]. Returns the URL the blob
 * can be fetched from, or `null` on failure. A `null` gateway answers with `Unsupported`.
 */
fun interface NappletUploadGateway {
    suspend fun upload(
        bytes: ByteArray,
        contentType: String,
    ): String?
}
