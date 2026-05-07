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
package com.vitorpamplona.quartz.relay.config

import cc.ekblad.toml.decode
import cc.ekblad.toml.tomlMapper
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import com.vitorpamplona.quartz.relay.RelayInfo
import java.io.File

/**
 * Operator-facing configuration. Section layout matches nostr-rs-relay's
 * `config.toml` so existing configs can be ported with little churn.
 *
 * Every section is optional; values not set fall back to sensible
 * defaults (or, for fields also exposed on the CLI, the CLI value wins).
 */
data class RelayConfig(
    val info: InfoSection = InfoSection(),
    val network: NetworkSection = NetworkSection(),
    val database: DatabaseSection = DatabaseSection(),
    val options: OptionsSection = OptionsSection(),
    val limits: LimitsSection = LimitsSection(),
    val authorization: AuthorizationSection = AuthorizationSection(),
) {
    /**
     * Maps the `[info]` section into a [RelayInfo] used by the NIP-11
     * endpoint. `relay_url` and CLI overrides take precedence.
     */
    fun resolveInfo(advertisedUrl: NormalizedRelayUrl): RelayInfo =
        RelayInfo(
            Nip11RelayInformation(
                name = info.name ?: "quartz-relay",
                description = info.description ?: "Embedded Nostr relay from the Amethyst quartz library.",
                pubkey = info.pubkey,
                contact = info.contact,
                icon = info.icon,
                software =
                    info.software
                        ?: "https://github.com/vitorpamplona/amethyst/tree/main/quartz-relay",
                version = info.version ?: "1.08.0",
                supported_nips =
                    info.supported_nips?.map(Int::toString)
                        ?: listOf("1", "9", "11", "40", "42", "45", "50", "62"),
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
    )

    data class LimitsSection(
        val max_ws_message_bytes: Int? = null,
        val max_ws_frame_bytes: Int? = null,
    )

    data class AuthorizationSection(
        val pubkey_whitelist: List<String> = emptyList(),
        val pubkey_blacklist: List<String> = emptyList(),
        val kind_whitelist: List<Int> = emptyList(),
        val kind_blacklist: List<Int> = emptyList(),
    )

    companion object {
        private val mapper = tomlMapper { }

        /** Parse a TOML string. */
        fun fromToml(toml: String): RelayConfig = mapper.decode<RelayConfig>(toml)

        /** Load a TOML config file. */
        fun fromFile(file: File): RelayConfig = mapper.decode<RelayConfig>(file.toPath())

        /**
         * Returns the URL the relay advertises in NIP-11 and NIP-42
         * challenges. Picks (in order):
         *   1. `info.relay_url` from the config
         *   2. The `network` section's host/port/path (with 0.0.0.0 → 127.0.0.1)
         *   3. The CLI override (handled in `Main.kt`).
         */
        fun advertisedUrl(config: RelayConfig): NormalizedRelayUrl =
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
