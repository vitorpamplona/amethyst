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
package com.vitorpamplona.amethyst.cli.commands

import com.vitorpamplona.amethyst.cli.Args
import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRelayListEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrlOrNull
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.toHttp
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip37Drafts.privateOutbox.PrivateOutboxRelayListEvent
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.BlockedRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.BroadcastRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.IndexerRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.ProxyRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.RelayFeedsListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.TrustedRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayInfo
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayType
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * `amy relay <add|remove|set|list|publish-lists|info>` — manage every relay
 * list this account maintains, mirroring Amethyst's relay-settings screen.
 *
 * Source of truth is the local event store (`<data-dir>/events-store/`): the
 * kind:10002 / 10050 / 10051 / 10007 / 10013 / 10006 / 10012 / 10086 / 10087 /
 * 10088 / 10089 events ARE the relay configuration. There is no `relays.json`.
 *
 *   - `relay add URL --type T`   append URL to the T bucket(s), building +
 *                                signing + ingesting a new list event. No
 *                                broadcast — call `publish-lists`.
 *   - `relay remove URL --type T` drop URL from the T bucket(s).
 *   - `relay set --type T [URL…]` replace the whole T bucket (no URLs clears it).
 *   - `relay list [--type T]`    dump the URLs from the local store.
 *   - `relay publish-lists`      broadcast every configured list to the union
 *                                of all the account's relays.
 *   - `relay info URL`           fetch + print a relay's NIP-11 document.
 *
 * The `nip65` bucket carries per-relay read/write markers ([AdvertisedRelayType]);
 * the others are flat URL lists. The private NIP-51 buckets (blocked, trusted,
 * proxy, indexer, broadcast, favorite, private-outbox, and the private half of
 * search) store their relays NIP-44-encrypted, exactly like the app.
 */
object RelayCommands {
    /**
     * A flat (non-NIP-65) relay-list bucket: one Nostr replaceable event kind,
     * a [read] that decodes the current URLs from the local store, and a [build]
     * that signs a fresh event holding exactly the given URLs. `nip65` is NOT in
     * this table — it carries read/write markers and is handled separately.
     */
    private class Bucket(
        val key: String,
        val kind: Int,
        val aliases: Set<String> = emptySet(),
        val read: suspend (Context, HexKey) -> List<NormalizedRelayUrl>,
        val build: suspend (Context, List<NormalizedRelayUrl>) -> Event,
    ) {
        fun matches(type: String) = type == key || type in aliases
    }

    private const val NIP65 = "nip65"

    /** Buckets `--type all` fans out to (the transport-critical public lists). */
    private val ALL_TRANSPORT = listOf(NIP65, "inbox", "key_package")

    private val BUCKETS: List<Bucket> =
        listOf(
            Bucket(
                "inbox",
                ChatMessageRelayListEvent.KIND,
                aliases = setOf("dm"),
                read = { c, pk -> c.dmInboxOf(pk)?.relays().orEmpty() },
                build = { c, r -> ChatMessageRelayListEvent.create(r, c.signer) },
            ),
            Bucket(
                "key_package",
                KeyPackageRelayListEvent.KIND,
                aliases = setOf("keyPackage", "keypackage"),
                read = { c, pk -> c.keyPackageRelaysOf(pk)?.relays().orEmpty() },
                build = { c, r -> KeyPackageRelayListEvent.create(r, c.signer) },
            ),
            Bucket(
                "search",
                SearchRelayListEvent.KIND,
                read = { c, pk -> (c.latestReplaceable(pk, SearchRelayListEvent.KIND) as? SearchRelayListEvent)?.relays(c.signer).orEmpty() },
                build = { c, r -> SearchRelayListEvent.create(r, c.signer) },
            ),
            Bucket(
                "private",
                PrivateOutboxRelayListEvent.KIND,
                aliases = setOf("private_outbox", "nip37"),
                read = { c, pk -> (c.latestReplaceable(pk, PrivateOutboxRelayListEvent.KIND) as? PrivateOutboxRelayListEvent)?.relays(c.signer).orEmpty() },
                build = { c, r -> PrivateOutboxRelayListEvent.create(r, c.signer) },
            ),
            Bucket(
                "blocked",
                BlockedRelayListEvent.KIND,
                read = { c, pk -> (c.latestReplaceable(pk, BlockedRelayListEvent.KIND) as? BlockedRelayListEvent)?.relays(c.signer).orEmpty() },
                build = { c, r -> BlockedRelayListEvent.create(r, c.signer) },
            ),
            Bucket(
                "broadcast",
                BroadcastRelayListEvent.KIND,
                read = { c, pk -> (c.latestReplaceable(pk, BroadcastRelayListEvent.KIND) as? BroadcastRelayListEvent)?.decryptRelays(c.signer).orEmpty() },
                build = { c, r -> BroadcastRelayListEvent.create(r, c.signer) },
            ),
            Bucket(
                "proxy",
                ProxyRelayListEvent.KIND,
                read = { c, pk -> (c.latestReplaceable(pk, ProxyRelayListEvent.KIND) as? ProxyRelayListEvent)?.decryptRelays(c.signer).orEmpty() },
                build = { c, r -> ProxyRelayListEvent.create(r, c.signer) },
            ),
            Bucket(
                "indexer",
                IndexerRelayListEvent.KIND,
                read = { c, pk -> (c.latestReplaceable(pk, IndexerRelayListEvent.KIND) as? IndexerRelayListEvent)?.decryptRelays(c.signer).orEmpty() },
                build = { c, r -> IndexerRelayListEvent.create(r, c.signer) },
            ),
            Bucket(
                "trusted",
                TrustedRelayListEvent.KIND,
                read = { c, pk -> (c.latestReplaceable(pk, TrustedRelayListEvent.KIND) as? TrustedRelayListEvent)?.decryptRelays(c.signer).orEmpty() },
                build = { c, r -> TrustedRelayListEvent.create(r, c.signer) },
            ),
            Bucket(
                "feeds",
                RelayFeedsListEvent.KIND,
                aliases = setOf("favorites", "relay_feeds"),
                read = { c, pk -> (c.latestReplaceable(pk, RelayFeedsListEvent.KIND) as? RelayFeedsListEvent)?.decryptRelays(c.signer).orEmpty() },
                build = { c, r -> RelayFeedsListEvent.create(r, c.signer) },
            ),
        )

    private fun bucketFor(type: String): Bucket? = BUCKETS.firstOrNull { it.matches(type) }

    /** Canonicalize a `--type` token to the buckets it targets, or throw on unknown. */
    private fun resolveTypes(type: String): List<String> =
        when {
            type == "all" -> ALL_TRANSPORT
            type == NIP65 -> listOf(NIP65)
            bucketFor(type) != null -> listOf(bucketFor(type)!!.key)
            else ->
                throw IllegalArgumentException(
                    "unknown relay type: $type (known: nip65, inbox, key_package, search, private, blocked, trusted, proxy, indexer, broadcast, feeds, all)",
                )
        }

    private fun parseMarker(raw: String?): AdvertisedRelayType =
        when (raw?.lowercase()) {
            null, "both", "all", "" -> AdvertisedRelayType.BOTH
            "read", "inbox" -> AdvertisedRelayType.READ
            "write", "outbox" -> AdvertisedRelayType.WRITE
            else -> throw IllegalArgumentException("invalid --marker: $raw (read|write|both)")
        }

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "relay",
            tail,
            "relay <add|remove|set|list|publish-lists|info> …",
            mapOf(
                "add" to { rest -> add(dataDir, Args(rest)) },
                "remove" to { rest -> remove(dataDir, Args(rest)) },
                "rm" to { rest -> remove(dataDir, Args(rest)) },
                "set" to { rest -> set(dataDir, Args(rest)) },
                "list" to { rest -> list(dataDir, Args(rest)) },
                "publish-lists" to { _ -> publishLists(dataDir) },
                // `info` is also intercepted in Main before account resolution
                // (it needs no account); routed here too for when one exists.
                "info" to { rest -> info(rest) },
            ),
        )

    /**
     * `relay info URL` — fetch a relay's NIP-11 information document over
     * HTTP (`Accept: application/nostr+json`) and print it. Local/stateless:
     * no account, no websocket. Parsing lives in quartz
     * ([Nip11RelayInformation.fromJson]); this only does the GET.
     */
    fun info(rest: Array<String>): Int {
        val args = Args(rest)
        val raw = args.positional(0, "relay-url")
        val normalized =
            raw.normalizeRelayUrlOrNull()
                ?: return Output.error("bad_args", "invalid relay url: $raw")
        val httpUrl = normalized.toHttp()

        val request =
            Request
                .Builder()
                .url(httpUrl)
                .header("Accept", Nip11RelayInformation.CONTENT_TYPE)
                .get()
                .build()

        return try {
            OkHttpClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Output.error("http_error", "relay returned HTTP ${response.code} for $httpUrl")
                }
                val body = response.body.string()
                val info = Nip11RelayInformation.fromJson(body)
                Output.emit(
                    mapOf(
                        "relay" to normalized.url,
                        "url" to httpUrl,
                        "info" to Output.mapper.readTree(info.toJson()),
                    ),
                )
                0
            }
        } catch (e: Exception) {
            Output.error("fetch_failed", "could not fetch NIP-11 from $httpUrl: ${e.message}")
        }
    }

    private suspend fun add(
        dataDir: DataDir,
        args: Args,
    ): Int {
        val rawUrl = args.positional(0, "url")
        val type = args.flag("type", "all")!!
        val marker = parseMarker(args.flag("marker"))
        val normalized =
            rawUrl.normalizeRelayUrlOrNull()
                ?: return Output.error("bad_args", "invalid relay url: $rawUrl")

        val targets = resolveTypes(type)
        Context.open(dataDir).use { ctx ->
            val self = ctx.identity.pubKeyHex
            val addedTo = mutableListOf<String>()
            val alreadyPresent = mutableListOf<String>()
            for (t in targets) {
                if (addToBucket(ctx, self, t, normalized, marker)) addedTo.add(t) else alreadyPresent.add(t)
            }
            Output.emit(
                mapOf(
                    "url" to rawUrl,
                    "added_to" to addedTo,
                    "already_present" to alreadyPresent,
                ),
            )
            return 0
        }
    }

    private suspend fun remove(
        dataDir: DataDir,
        args: Args,
    ): Int {
        val rawUrl = args.positional(0, "url")
        val type = args.flag("type", "all")!!
        val normalized =
            rawUrl.normalizeRelayUrlOrNull()
                ?: return Output.error("bad_args", "invalid relay url: $rawUrl")

        val targets = resolveTypes(type)
        Context.open(dataDir).use { ctx ->
            val self = ctx.identity.pubKeyHex
            val removedFrom = mutableListOf<String>()
            val notPresent = mutableListOf<String>()
            for (t in targets) {
                if (removeFromBucket(ctx, self, t, normalized)) removedFrom.add(t) else notPresent.add(t)
            }
            Output.emit(
                mapOf(
                    "url" to rawUrl,
                    "removed_from" to removedFrom,
                    "not_present" to notPresent,
                ),
            )
            return 0
        }
    }

    private suspend fun set(
        dataDir: DataDir,
        args: Args,
    ): Int {
        val type = args.flag("type") ?: return Output.error("bad_args", "set requires --type T")
        if (type == "all") return Output.error("bad_args", "set needs a single --type, not `all`")
        val bucket = if (type == NIP65) null else bucketFor(type) ?: return Output.error("bad_args", "unknown relay type: $type")
        val marker = parseMarker(args.flag("marker"))

        val normalized = mutableListOf<NormalizedRelayUrl>()
        for (raw in args.positional) {
            normalized.add(
                raw.normalizeRelayUrlOrNull()
                    ?: return Output.error("bad_args", "invalid relay url: $raw"),
            )
        }
        // dedupe, preserve order
        val relays = normalized.distinctBy { it.url }

        // Clearing a bucket is destructive, so it must be explicit: `--clear`
        // with no URLs. A bare `set` with no URLs is almost always a shell
        // variable that expanded to nothing — reject it rather than silently
        // wiping the list. (require → IllegalArgumentException → bad_args/exit 2.)
        val clear = args.bool("clear")
        require(relays.isNotEmpty() || clear) { "set needs at least one URL, or --clear to empty the $type list" }
        require(relays.isEmpty() || !clear) { "--clear cannot be combined with relay URLs" }

        Context.open(dataDir).use { ctx ->
            val signed: Event =
                if (type == NIP65) {
                    AdvertisedRelayListEvent.create(relays.map { AdvertisedRelayInfo(it, marker) }, ctx.signer)
                } else {
                    bucket!!.build(ctx, relays)
                }
            ctx.verifyAndStore(signed)
            Output.emit(
                mapOf(
                    "type" to type,
                    "kind" to signed.kind,
                    "event_id" to signed.id,
                    "relays" to relays.map { it.url },
                ),
            )
            return 0
        }
    }

    /**
     * Append [url] to the relay-list event for [type] (creating one if absent).
     * Returns `true` when a new event was written, `false` when [url] was
     * already present. For `nip65`, [marker] sets the read/write role.
     */
    private suspend fun addToBucket(
        ctx: Context,
        self: HexKey,
        type: String,
        url: NormalizedRelayUrl,
        marker: AdvertisedRelayType,
    ): Boolean {
        if (type == NIP65) {
            val existing = ctx.relaysOf(self)?.relays().orEmpty()
            if (existing.any { it.relayUrl.url == url.url }) return false
            val combined = existing + AdvertisedRelayInfo(url, marker)
            ctx.verifyAndStore(AdvertisedRelayListEvent.create(combined, ctx.signer))
            return true
        }
        val bucket = bucketFor(type) ?: throw IllegalArgumentException("unknown relay type: $type")
        val existing = bucket.read(ctx, self)
        if (existing.any { it.url == url.url }) return false
        ctx.verifyAndStore(bucket.build(ctx, existing + url))
        return true
    }

    /**
     * Drop [url] from the relay-list event for [type]. Returns `true` when the
     * URL was present (and a new, shorter event was written), `false` otherwise.
     */
    private suspend fun removeFromBucket(
        ctx: Context,
        self: HexKey,
        type: String,
        url: NormalizedRelayUrl,
    ): Boolean {
        if (type == NIP65) {
            val existing = ctx.relaysOf(self)?.relays().orEmpty()
            if (existing.none { it.relayUrl.url == url.url }) return false
            val remaining = existing.filterNot { it.relayUrl.url == url.url }
            ctx.verifyAndStore(AdvertisedRelayListEvent.create(remaining, ctx.signer))
            return true
        }
        val bucket = bucketFor(type) ?: throw IllegalArgumentException("unknown relay type: $type")
        val existing = bucket.read(ctx, self)
        if (existing.none { it.url == url.url }) return false
        ctx.verifyAndStore(bucket.build(ctx, existing.filterNot { it.url == url.url }))
        return true
    }

    private suspend fun list(
        dataDir: DataDir,
        args: Args,
    ): Int {
        val type = args.flag("type")
        Context.open(dataDir).use { ctx ->
            val self = ctx.identity.pubKeyHex

            if (type != null) {
                // Single-bucket view.
                if (type == NIP65) {
                    val nip65 = ctx.relaysOf(self)
                    Output.emit(
                        mapOf(
                            "type" to NIP65,
                            "kind" to AdvertisedRelayListEvent.KIND,
                            "read" to (nip65?.readRelaysNorm()?.map { it.url } ?: emptyList<String>()),
                            "write" to (nip65?.writeRelaysNorm()?.map { it.url } ?: emptyList<String>()),
                            "relays" to (nip65?.relaysNorm()?.map { it.url } ?: emptyList<String>()),
                        ),
                    )
                    return 0
                }
                val bucket = bucketFor(type) ?: return Output.error("bad_args", "unknown relay type: $type")
                Output.emit(
                    mapOf(
                        "type" to bucket.key,
                        "kind" to bucket.kind,
                        "relays" to bucket.read(ctx, self).map { it.url },
                    ),
                )
                return 0
            }

            // Full view — every bucket. Keys `nip65`/`inbox`/`key_package` are
            // kept as flat URL lists for backwards compatibility; `nip65_read`/
            // `nip65_write` add the marker split, and the private lists follow.
            val nip65 = ctx.relaysOf(self)
            val out = linkedMapOf<String, Any?>()
            out["nip65"] = nip65?.relaysNorm()?.map { it.url } ?: emptyList<String>()
            out["nip65_read"] = nip65?.readRelaysNorm()?.map { it.url } ?: emptyList<String>()
            out["nip65_write"] = nip65?.writeRelaysNorm()?.map { it.url } ?: emptyList<String>()
            for (bucket in BUCKETS) {
                out[bucket.key] = bucket.read(ctx, self).map { it.url }
            }
            Output.emit(out)
            return 0
        }
    }

    private suspend fun publishLists(dataDir: DataDir): Int {
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val self = ctx.identity.pubKeyHex

            // Collect every configured list event: nip65 plus each flat bucket.
            val events = linkedMapOf<String, Event?>()
            events[NIP65] = ctx.relaysOf(self)
            for (bucket in BUCKETS) {
                events[bucket.key] = ctx.latestReplaceable(self, bucket.kind)
            }

            if (events.values.all { it == null }) {
                return Output.error(
                    "no_relays",
                    "no relay lists in the local store; run `amy relay add` first or `amy create` to bootstrap defaults",
                )
            }

            val targets = ctx.anyRelays()
            val eventIds = linkedMapOf<String, String?>()
            val acceptedBy = linkedMapOf<String, List<String>>()
            for ((key, event) in events) {
                eventIds[key] = event?.id
                val result = event?.let { ctx.publish(it, targets) }.orEmpty()
                acceptedBy[key] = result.filterValues { it }.keys.map { it.url }
            }

            Output.emit(
                mapOf(
                    // Legacy keys kept verbatim so existing scripts keep working.
                    "nip65_event_id" to eventIds[NIP65],
                    "inbox_event_id" to eventIds["inbox"],
                    "key_package_list_event_id" to eventIds["key_package"],
                    "event_ids" to eventIds,
                    "accepted_by" to acceptedBy,
                ),
            )
            return 0
        }
    }
}
