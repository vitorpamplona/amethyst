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
package com.vitorpamplona.geode.config

import cc.ekblad.toml.decode
import cc.ekblad.toml.tomlMapper
import com.vitorpamplona.geode.RelayInfo
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import java.io.File

/**
 * Operator-facing **boot-time** configuration. Parsed once from a TOML
 * file at startup and treated as immutable thereafter — anything that
 * needs to change while the relay is running lives in [RuntimeConfig].
 *
 * Section layout matches nostr-rs-relay's `config.toml` so existing
 * configs can be ported with little churn.
 *
 * Every section is optional; values not set fall back to sensible
 * defaults (or, for fields also exposed on the CLI, the CLI value wins).
 */
data class StaticConfig(
    val info: InfoSection = InfoSection(),
    val network: NetworkSection = NetworkSection(),
    val database: DatabaseSection = DatabaseSection(),
    val options: OptionsSection = OptionsSection(),
    val authorization: AuthorizationSection = AuthorizationSection(),
    val admin: AdminSection = AdminSection(),
    val negentropy: NegentropySection = NegentropySection(),
) {
    /**
     * Maps the `[info]` section into a [RelayInfo] used by the NIP-11
     * endpoint. `relay_url` and CLI overrides take precedence.
     */
    fun resolveInfo(advertisedUrl: NormalizedRelayUrl): RelayInfo =
        RelayInfo(
            Nip11RelayInformation(
                name = info.name ?: RelayInfo.NAME,
                description = info.description ?: RelayInfo.DESCRIPTION,
                pubkey = info.pubkey,
                contact = info.contact,
                icon = info.icon,
                software = info.software ?: RelayInfo.SOFTWARE,
                version = info.version ?: RelayInfo.VERSION,
                supported_nips =
                    info.supported_nips?.map(Int::toString)
                        ?: RelayInfo.SUPPORTED_NIPS,
                privacy_policy = info.privacy_policy,
                terms_of_service = info.terms_of_service,
                relay_countries = info.relay_countries,
                language_tags = info.language_tags,
                tags = info.tags,
            ),
        ).also {
            // Touch [advertisedUrl] so the parameter isn't unused — we keep
            // it in the signature because future fields (e.g. self-pubkey
            // selection, fee URLs) will want it.
            advertisedUrl.url
        }

    data class InfoSection(
        val relay_url: String? = null,
        val name: String? = null,
        val description: String? = null,
        val pubkey: String? = null,
        val contact: String? = null,
        val icon: String? = null,
        val software: String? = null,
        val version: String? = null,
        /** NIP numbers as ints (e.g. `[1, 9, 11]`). Stringified at render time. */
        val supported_nips: List<Int>? = null,
        val privacy_policy: String? = null,
        val terms_of_service: String? = null,
        val relay_countries: List<String>? = null,
        val language_tags: List<String>? = null,
        val tags: List<String>? = null,
    )

    data class NetworkSection(
        val host: String = "0.0.0.0",
        val port: Int = 7447,
        val path: String = "/",
        /**
         * Ktor CIO acceptor-thread count. `null` (default) keeps Ktor's
         * default sizing — fine up to a few thousand concurrent
         * connections. On big-VM deployments targeting 10k+
         * connections, lift this to roughly half the available cores
         * so the acceptor doesn't starve workers.
         */
        val connection_group_size: Int? = null,
        /**
         * Ktor CIO worker-thread count (handles socket I/O). `null`
         * keeps Ktor's default. Each connection's WebSocket read/write
         * is dispatched onto this pool; for many idle long-lived
         * connections the pool can stay small, but 10k+ connections
         * benefit from sizing this to the full CPU count.
         */
        val worker_group_size: Int? = null,
        /**
         * Ktor CIO call-handling thread count. `null` keeps Ktor's
         * default. Sized higher than [worker_group_size] because each
         * call (incl. WebSocket upgrade) may suspend on I/O — at
         * 10k+ connections, ~4× cores is a reasonable starting point.
         */
        val call_group_size: Int? = null,
    )

    data class DatabaseSection(
        /** True keeps an in-memory SQLite db (default — events vanish on restart). */
        val in_memory: Boolean = true,
        /** Filesystem path for a persistent SQLite db. Ignored when [in_memory] is true. */
        val file: String? = null,
    )

    data class OptionsSection(
        /** Reject events whose `created_at` is more than this many seconds in the future. */
        val reject_future_seconds: Int? = null,
        /** Require NIP-42 AUTH for REQ/EVENT/COUNT. */
        val require_auth: Boolean = false,
        /**
         * Drop events whose Schnorr signature does not verify. **Defaults
         * to `true`**: any relay accepting traffic from real clients
         * should verify signatures, and verifying-by-default closes the
         * footgun of forgetting the flag. Set explicitly to `false` only
         * for trusted-input scenarios (test fixtures, mirror replays).
         */
        val verify_signatures: Boolean = true,
        /**
         * Run signature verification in parallel inside the IngestQueue
         * (CPU fan-out across `Dispatchers.Default`) instead of serially
         * on each connection's WebSocket pump. Tier-3 of the
         * `event-ingestion-batching` plan. Wins scale with how many
         * EVENTs a single connection sends back-to-back: ~CPU_COUNT×
         * verify-step speed-up on burst publishes. Set false to keep
         * the legacy in-policy verify path.
         *
         * Only takes effect when [verify_signatures] is also true.
         */
        val parallel_verify: Boolean = true,
    )

    /**
     * NIP-77 negentropy tuning. Defaults track strfry
     * (`hoytech/strfry`) so a Geode relay accepts the same workload
     * shape and exchanges the same NEG-MSG round-trip size as
     * strfry — the de-facto reference implementation.
     *
     * - [frame_size_limit] mirrors strfry's hard-coded
     *   `Negentropy ne(storage, 500'000)` in `RelayNegentropy.cpp`.
     *   Hex-encoded that's ~1 MB on the wire per NEG-MSG. Ktor's
     *   WebSocket layer does not impose a default frame cap, so this
     *   payload is delivered intact unless a reverse proxy is
     *   misconfigured.
     * - [max_sync_events] mirrors strfry's
     *   `relay__negentropy__maxSyncEvents`. NEG-OPEN whose snapshot
     *   exceeds this returns
     *   `["NEG-ERR", "<subId>", "blocked: too many query results"]`.
     * - [max_sessions_per_connection] caps concurrent NEG-OPEN
     *   sessions held by a single connection. strfry shares its
     *   200-cap with REQ subs via `relay__maxSubsPerConnection`;
     *   Geode counts NEG independently for now (REQ has no cap yet).
     *   Overflow returns NOTICE
     *   `"too many concurrent NEG requests"` (matches strfry).
     */
    data class NegentropySection(
        val frame_size_limit: Long = 500_000L,
        val max_sync_events: Int = 1_000_000,
        val max_sessions_per_connection: Int = 200,
    )

    data class AuthorizationSection(
        val pubkey_whitelist: List<String> = emptyList(),
        val pubkey_blacklist: List<String> = emptyList(),
        val kind_whitelist: List<Int> = emptyList(),
        val kind_blacklist: List<Int> = emptyList(),
    )

    /**
     * NIP-86 relay management API. When [pubkeys] is non-empty,
     * `KtorRelay` exposes a POST endpoint at the relay path
     * that accepts JSON-RPC admin requests authenticated via NIP-98
     * HTTP-Auth. Only requests signed by one of these pubkeys are
     * dispatched.
     *
     * [public_url] is the canonical URL the relay is reachable at,
     * e.g. `https://relay.example.com/`. NIP-98's URL binding compares
     * the signed `u` tag against this — without it, an attacker can
     * spoof the `Host` header to bind their signature to any URL.
     * Required when running behind TLS termination or a reverse proxy.
     */
    data class AdminSection(
        val pubkeys: List<String> = emptyList(),
        val public_url: String? = null,
        /**
         * Path for the JSON snapshot that backs [RuntimeConfig] —
         * NIP-86 admin state (ban lists + the live NIP-11 doc) that
         * survives restarts. When unset, runtime config is in-memory
         * only.
         *
         * Convention: place next to the SQLite event-store file —
         * e.g. `[database].file = "/var/lib/geode/events.db"`
         * pairs with `[admin].state_file = "/var/lib/geode/events.db.admin.json"`.
         */
        val state_file: String? = null,
    )

    companion object {
        private val mapper = tomlMapper { }

        /** Parse a TOML string. */
        fun fromToml(toml: String): StaticConfig = mapper.decode<StaticConfig>(toml)

        /** Load a TOML config file. */
        fun fromFile(file: File): StaticConfig = mapper.decode<StaticConfig>(file.toPath())

        /**
         * Returns the URL the relay advertises in NIP-11 and NIP-42
         * challenges. Picks (in order):
         *   1. `info.relay_url` from the config
         *   2. The `network` section's host/port/path (with 0.0.0.0 → 127.0.0.1)
         *   3. The CLI override (handled in `Main.kt`).
         */
        fun advertisedUrl(config: StaticConfig): NormalizedRelayUrl =
            (
                config.info.relay_url
                    ?: defaultUrl(config.network)
            ).normalizeRelayUrl()

        private fun defaultUrl(net: NetworkSection): String {
            val host = if (net.host == "0.0.0.0") "127.0.0.1" else net.host
            return "ws://$host:${net.port}${net.path}"
        }
    }
}
