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
 * Result of an ElectrumX name_show query.
 *
 * Maps to the JSON fields returned by Namecoin Core / Electrum-NMC:
 *   { "name": "d/example", "value": "{...}", "txid": "abc...", "height": 12345, ... }
 */
@Serializable
data class NameShowResult(
    val name: String,
    val value: String,
    val txid: String? = null,
    val height: Int? = null,
    val expiresIn: Int? = null,
)

/**
 * Represents a single ElectrumX server endpoint.
 */
data class ElectrumxServer(
    val host: String,
    val port: Int,
    val useSsl: Boolean = true,
    /** If true, accept any certificate (self-signed, expired, etc.) */
    val trustAllCerts: Boolean = false,
)

/**
 * Specific exception types for Namecoin resolution failures.
 * Allows callers to distinguish "name doesn't exist" from "servers unreachable".
 */
sealed class NamecoinLookupException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /** The name was queried successfully but does not exist on the blockchain. */
    class NameNotFound(
        val name: String,
    ) : NamecoinLookupException("Name not found: $name")

    /** The name has expired (>36000 blocks since last update). */
    class NameExpired(
        val name: String,
    ) : NamecoinLookupException("Name expired: $name")

    /** All ElectrumX servers were unreachable or returned errors. */
    class ServersUnreachable(
        val lastError: Throwable? = null,
    ) : NamecoinLookupException("All ElectrumX servers unreachable", lastError)
}

/**
 * Result of testing connectivity to a single ElectrumX server.
 */
data class ServerTestResult(
    val server: ElectrumxServer,
    val success: Boolean,
    val responseTimeMs: Long,
    val error: String? = null,
    val tlsVersion: String? = null,
    /** PEM-encoded server certificate, captured during test for TOFU pinning. */
    val serverCertPem: String? = null,
    /** SHA-256 fingerprint of the server certificate. */
    val certFingerprint: String? = null,
)

/** Well-known public Namecoin ElectrumX servers (clearnet). */
val DEFAULT_ELECTRUMX_SERVERS =
    listOf(
        ElectrumxServer("electrumx.testls.space", 50002, useSsl = true, trustAllCerts = true),
        ElectrumxServer("nmc2.bitcoins.sk", 57002, useSsl = true, trustAllCerts = true),
        ElectrumxServer("46.229.238.187", 57002, useSsl = true, trustAllCerts = true),
    )

/** Tor-preferred server list: onion primary, clearnet fallback. */
val TOR_ELECTRUMX_SERVERS =
    listOf(
        ElectrumxServer(
            "i665jpwsq46zlsdbnj4axgzd3s56uzey5uhotsnxzsknzbn36jaddsid.onion",
            50002,
            useSsl = true,
            trustAllCerts = true,
        ),
        ElectrumxServer("electrumx.testls.space", 50002, useSsl = true, trustAllCerts = true),
        ElectrumxServer("nmc2.bitcoins.sk", 57002, useSsl = true, trustAllCerts = true),
    )
