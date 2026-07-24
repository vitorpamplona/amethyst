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
    /** `[[mirror]]` entries — upstream relays this relay streams from. */
    val mirror: List<MirrorSection> = emptyList(),
) {
    fun resolveInfo(fullTextSearch: Boolean = true): RelayInfo =
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
                        // An explicit [info] nips list is operator-authoritative;
                        // the default list stays honest about search.
                        ?: if (fullTextSearch) RelayInfo.SUPPORTED_NIPS else RelayInfo.SUPPORTED_NIPS - "50",
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
        /**
         * Which [com.vitorpamplona.quartz.nip01Core.store.IEventStore]
         * implementation backs the relay. Recognised keywords (case-
         * insensitive):
         *
         *  - `"sqlite"` (default): quartz's SQLite-backed
         *    [com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore] —
         *    honours every `[database]` knob below ([in_memory]/[file],
         *    [readers], [mmap_size], …). The right choice for real traffic.
         *  - `"fs"`: quartz's filesystem
         *    [com.vitorpamplona.quartz.nip01Core.store.fs.FsEventStore],
         *    one JSON file per event under the directory named by [file]
         *    (which becomes a directory, not a db file, for this backend).
         *    Human-inspectable with `cat`/`jq`; the SQLite-only knobs are
         *    ignored.
         *
         * Any other value is treated as the fully-qualified class name of a
         * custom `IEventStore` on the classpath, instantiated reflectively —
         * see [com.vitorpamplona.geode.StoreFactory]. This is the "plug in
         * any implementation" escape hatch: the class must implement
         * `IEventStore` and expose one of the public constructors
         * `(NormalizedRelayUrl?, IndexingStrategy)`, `(NormalizedRelayUrl?)`,
         * or `()`.
         */
        val backend: String = "sqlite",
        /** True keeps an in-memory SQLite db (default — events vanish on restart). */
        val in_memory: Boolean = true,
        val file: String? = null,
        /**
         * Reader-connection pool size. `null` keeps quartz's default (4).
         * Only meaningful for file-backed stores; in-memory databases
         * share the single writer connection regardless.
         */
        val readers: Int? = null,
        /**
         * `PRAGMA mmap_size` in bytes, e.g. `268435456` for 256 MiB.
         * Maps the database file into memory so reads skip the pread
         * syscall + page-cache copy. `null` keeps SQLite's default (off).
         */
        val mmap_size: Long? = null,
        /**
         * `PRAGMA temp_store = MEMORY` — keeps sort/temp b-trees for
         * large queries in RAM instead of temp files.
         */
        val temp_store_memory: Boolean = false,
        /**
         * Refresh query-planner statistics (`PRAGMA analysis_limit;
         * PRAGMA optimize`) every this many seconds. Incremental and
         * usually a no-op, but keeps the planner from drifting onto the
         * wrong index as the corpus grows/changes shape. `null` = never.
         */
        val optimize_interval_seconds: Long? = null,
    )

    data class OptionsSection(
        val reject_future_seconds: Int? = null,
        val require_auth: Boolean = false,
        /**
         * Advertise NIP-42 AUTH without requiring it: the relay sends the
         * challenge and records clients that authenticate, but EVENT/REQ/COUNT
         * still work for clients that never do. Ignored when [require_auth] is
         * true (mandatory AUTH already sends the challenge).
         */
        val optional_auth: Boolean = false,
        /**
         * Defaults to `true`: any relay accepting real traffic should
         * verify Schnorr signatures, and verifying-by-default closes
         * the footgun of forgetting the flag. Set false only for
         * trusted-input scenarios (test fixtures, mirror replays).
         */
        val verify_signatures: Boolean = true,
        /** CPU fan-out verification in the IngestQueue. No-op when [verify_signatures] is false. */
        val parallel_verify: Boolean = true,
        /**
         * NIP-50 full-text search. On by default. When off, no FTS index
         * is created or maintained (inserts skip tokenization — a
         * measurable share of ingest cost), NIP-11 stops advertising
         * NIP-50, and REQ filters carrying a `search` term match nothing.
         * Turn off when search isn't needed, or to compare against relays
         * that don't offer NIP-50 at all (strfry, for example).
         */
        val full_text_search: Boolean = true,
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
        /**
         * Keep an always-current in-memory `(created_at, id)` set so
         * full-corpus NEG-OPENs skip the table scan + seal (strfry
         * parity). ~140 B per stored event of heap; on by default. Only
         * built once the first full-corpus NEG-OPEN arrives, and only
         * when the corpus fits `max_sync_events` (an over-cap corpus
         * answers NEG-ERR from a capped scan instead).
         */
        val live_index: Boolean = true,
    )

    data class AuthorizationSection(
        val pubkey_whitelist: List<String> = emptyList(),
        val pubkey_blacklist: List<String> = emptyList(),
        val kind_whitelist: List<Int> = emptyList(),
        val kind_blacklist: List<Int> = emptyList(),
    )

    /**
     * One upstream relay to mirror, declared as a `[[mirror]]` TOML array
     * entry. The relay dials [url] itself, subscribes to everything newer
     * than `now - backfill_seconds`, and ingests the stream through the
     * same group-commit writer as client publishes (reconnects and
     * re-subscribes automatically).
     *
     * [trusted] is the relay-to-relay trust switch (strfry's model):
     * `true` skips Schnorr signature verification for events arriving on
     * this connection — sound only when the upstream verifies its own
     * ingest, which is why it defaults to `false` (mirror-but-verify).
     * The identity being trusted is the URL this relay dialed (TLS-
     * authenticated for `wss://`), never anything a peer claims.
     */
    data class MirrorSection(
        val url: String,
        val trusted: Boolean = false,
        /** How far back the initial subscription reaches. 0 = live-only. */
        val backfill_seconds: Long = 0L,
        /**
         * Optional NIP-01 filter as a JSON object string (strfry-router
         * parity), e.g. `'{"kinds":[0,1,3],"#t":["nostr"]}'`. Scopes what
         * this upstream is asked for AND what it is allowed to deliver —
         * every received event is re-checked against it before ingest, so
         * even a trusted upstream can't push events outside the declared
         * scope. `since` is managed by the mirror (see [backfill_seconds])
         * and `limit` is transport-level, so both are ignored if present.
         * Omitted = mirror everything. For several disjoint filters, add
         * several `[[mirror]]` entries with the same url.
         */
        val filter: String? = null,
        /**
         * Flow direction, strfry-router's `dir`: `"down"` (pull from the
         * upstream — the default), `"up"` (push this relay's matching
         * events to it), or `"both"`. Both-way mirrors suppress echoes
         * (an event pulled down is not pushed straight back).
         */
        val dir: String = "down",
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

    /**
     * Boot-time sanity check for values the TOML types can't constrain.
     * Throws [IllegalArgumentException] (fail-loud at startup) rather
     * than letting a nonsensical knob degrade the running relay — a zero
     * reader pool hangs every query, a non-positive optimize interval
     * busy-loops the writer. Call once after parsing, before building
     * the store.
     */
    fun validate() {
        database.readers?.let {
            require(it >= 1) { "[database].readers must be >= 1 (got $it); a 0/negative pool can never answer a query" }
        }
        database.optimize_interval_seconds?.let {
            require(it > 0) {
                "[database].optimize_interval_seconds must be > 0 (got $it); a non-positive interval busy-loops PRAGMA optimize under the writer mutex"
            }
        }
    }

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
