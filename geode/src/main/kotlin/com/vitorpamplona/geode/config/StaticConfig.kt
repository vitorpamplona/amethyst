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
 * configs can be ported with little churn. Every section is optional;
 * CLI flags override file values where both exist.
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
    fun resolveInfo(): RelayInfo =
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
        )

    data class InfoSection(
        val relay_url: String? = null,
        val name: String? = null,
        val description: String? = null,
        val pubkey: String? = null,
        val contact: String? = null,
        val icon: String? = null,
        val software: String? = null,
        val version: String? = null,
        val supported_nips: List<Int>? = null,
        val privacy_policy: String? = null,
        val terms_of_service: String? = null,
        val relay_countries: List<String>? = null,
        val language_tags: List<String>? = null,
        val tags: List<String>? = null,
    )

    /**
     * Bind config + Ktor CIO thread-pool sizes. The three `*_size`
     * fields default to Ktor's per-CPU sizing; lift them on big-VM
     * deployments targeting 10k+ concurrent connections.
     */
    data class NetworkSection(
        val host: String = "0.0.0.0",
        val port: Int = 7447,
        val path: String = "/",
        val connection_group_size: Int? = null,
        val worker_group_size: Int? = null,
        val call_group_size: Int? = null,
    )

    data class DatabaseSection(
        /** True keeps an in-memory SQLite db (default — events vanish on restart). */
        val in_memory: Boolean = true,
        val file: String? = null,
    )

    data class OptionsSection(
        val reject_future_seconds: Int? = null,
        val require_auth: Boolean = false,
        /**
         * Defaults to `true`: any relay accepting real traffic should
         * verify Schnorr signatures, and verifying-by-default closes
         * the footgun of forgetting the flag. Set false only for
         * trusted-input scenarios (test fixtures, mirror replays).
         */
        val verify_signatures: Boolean = true,
        /** CPU fan-out verification in the IngestQueue. No-op when [verify_signatures] is false. */
        val parallel_verify: Boolean = true,
    )

    /**
     * NIP-77 negentropy tuning. Defaults track strfry (`hoytech/strfry`)
     * so a Geode relay accepts the same workload shape and exchanges
     * the same NEG-MSG round-trip size:
     *
     *  - [frame_size_limit]: strfry's hard-coded `Negentropy ne(storage, 500'000)`.
     *  - [max_sync_events]: strfry's `relay__negentropy__maxSyncEvents`;
     *    NEG-OPEN over this returns `["NEG-ERR", "<subId>", "blocked: too many query results"]`.
     *  - [max_sessions_per_connection]: NEG sessions held by one connection;
     *    overflow returns NOTICE `"too many concurrent NEG requests"`.
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
     * NIP-86 admin. [pubkeys] non-empty opens the POST endpoint at the
     * relay path; only NIP-98 tokens signed by these pubkeys dispatch.
     * The NIP-98 URL binding uses `[info].relay_url` with the scheme
     * swapped (`ws(s)://` → `http(s)://`) per NIP-86 — set
     * `[info].relay_url` to the canonical public URL when behind TLS
     * termination or a reverse proxy.
     */
    data class AdminSection(
        val pubkeys: List<String> = emptyList(),
        /**
         * JSON snapshot path for [RuntimeConfig] (ban lists + live
         * NIP-11 doc). Convention: place next to the SQLite event file,
         * e.g. `events.db` ↔ `events.db.admin.json`.
         */
        val state_file: String? = null,
    )

    companion object {
        private val mapper = tomlMapper { }

        fun fromToml(toml: String): StaticConfig = mapper.decode<StaticConfig>(toml)

        fun fromFile(file: File): StaticConfig = mapper.decode<StaticConfig>(file.toPath())

        /**
         * URL the relay advertises in NIP-11 and NIP-42 challenges:
         * `info.relay_url` if set, else built from `network.host:port/path`
         * with `0.0.0.0` rewritten to `127.0.0.1`.
         */
        fun advertisedUrl(config: StaticConfig): NormalizedRelayUrl = (config.info.relay_url ?: defaultUrl(config.network)).normalizeRelayUrl()

        private fun defaultUrl(net: NetworkSection): String {
            val host = if (net.host == "0.0.0.0") "127.0.0.1" else net.host
            return "ws://$host:${net.port}${net.path}"
        }
    }
}
