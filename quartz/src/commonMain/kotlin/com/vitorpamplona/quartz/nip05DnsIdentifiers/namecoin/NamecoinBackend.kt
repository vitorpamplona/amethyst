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
package com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin

import kotlinx.serialization.Serializable

/**
 * Primary backend used to resolve Namecoin names.
 *
 * - [ELECTRUMX] (default): query one or more ElectrumX servers via the
 *   light-client name index protocol. Privacy-preserving for queries but
 *   relies on third-party ElectrumX operators (or your own).
 * - [NAMECOIN_CORE_RPC]: query a Namecoin Core full node directly via
 *   JSON-RPC `name_show`. Most sovereign option — no trust in any
 *   third party once you have a synced node.
 *
 * When [NAMECOIN_CORE_RPC] is selected, [NamecoinCoreRpcConfig] describes
 * how to reach the node. Optional fallback to ElectrumX is controlled
 * by [NamecoinFallbackPolicy].
 */
@Serializable
enum class NamecoinBackend {
    ELECTRUMX,
    NAMECOIN_CORE_RPC,
}

/**
 * Connection details for a Namecoin Core JSON-RPC endpoint.
 *
 * The URL is expected to be the root of the RPC server (e.g.
 * `http://192.168.1.42:8336/`, `http://<onion>.onion:8336/`, or
 * `https://lan-host/` when StartOS or umbrel terminate TLS for you).
 * The user supplies username and password; cookie-auth is not
 * supported because the user typically isn't running this on the same
 * host as the node.
 *
 * Tor onion endpoints are resolved through the same `socketFactoryForNip05`
 * path as the existing ElectrumX traffic, so they obey the user's Tor
 * settings without extra plumbing.
 *
 * `usePinnedTrustStore` mirrors the ElectrumX flag: set true to route
 * the HTTPS request through the pinned-cert socket factory (used for
 * StartOS / umbrel LAN endpoints with self-signed root, etc.).
 */
@Serializable
data class NamecoinCoreRpcConfig(
    /**
     * Full URL to the RPC endpoint. Must include scheme. The path is
     * preserved (Namecoin Core ignores the request path; useful if a
     * reverse proxy mounts the RPC under a sub-path).
     */
    val url: String = "",
    /** RPC username (StartOS "RPC Credentials" → Username, umbrel 'Connect From Outside' → RPC User). */
    val username: String = "",
    /** RPC password (StartOS "RPC Credentials" → Password, umbrel 'Connect From Outside' → RPC Password). */
    val password: String = "",
    /**
     * Per-call timeout in milliseconds. Defaults to 15 seconds, well
     * under the 20-second [NamecoinNameResolver] outer timeout so a
     * single slow node doesn't burn the whole budget.
     */
    val timeoutMs: Long = 15_000L,
    /** Use the pinned trust store (self-signed StartOS / umbrel LAN cert, etc.). */
    val usePinnedTrustStore: Boolean = false,
) {
    val isUsable: Boolean
        get() = url.isNotBlank() && (url.startsWith("http://") || url.startsWith("https://"))
}

/**
 * Controls how the composite backend falls back when a query fails.
 *
 * Failures here mean:
 * - **Core RPC** unreachable / errored, OR
 * - **Custom ElectrumX** servers all failed.
 *
 * For each kind of failure the user can independently decide whether
 * to fall through to the next tier:
 *
 * ```
 *  primary (Core RPC or custom ElectrumX)
 *    └ on failure → custom ElectrumX (if Core RPC primary AND
 *                   fallbackToCustomElectrumx)
 *      └ on failure → default ElectrumX (if fallbackToDefaultElectrumx)
 * ```
 *
 * When Custom ElectrumX is the primary backend (i.e. backend=ELECTRUMX
 * with custom servers configured), `fallbackToCustomElectrumx` is
 * ignored — that step doesn't exist in the chain. `fallbackToDefaultElectrumx`
 * still applies and lets default public ElectrumX servers be tried if
 * every custom server fails. This is opt-in: by default custom servers
 * are used **exclusively** (matches existing behaviour).
 */
@Serializable
data class NamecoinFallbackPolicy(
    /**
     * If primary is Namecoin Core RPC and it fails, also try the user's
     * custom ElectrumX servers before giving up. Off by default —
     * privacy-conscious users who picked Core RPC usually don't want
     * their queries silently leaking to ElectrumX.
     */
    val fallbackToCustomElectrumx: Boolean = false,
    /**
     * If everything above fails, try the hardcoded public ElectrumX
     * defaults. Off by default — defaults expose lookups to public
     * operators, so opt-in only.
     */
    val fallbackToDefaultElectrumx: Boolean = false,
) {
    companion object {
        val NONE = NamecoinFallbackPolicy()
    }
}
