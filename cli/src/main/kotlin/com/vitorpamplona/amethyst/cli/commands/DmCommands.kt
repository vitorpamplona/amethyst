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
import com.vitorpamplona.amethyst.cli.AwaitTimeout
import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Json
import com.vitorpamplona.amethyst.commons.relayClient.nip17Dm.filterGiftWrapsToPubkey
import com.vitorpamplona.amethyst.commons.relayClient.nip17Dm.unwrapAndUnsealOrNull
import com.vitorpamplona.amethyst.commons.service.upload.UploadOrchestrator
import com.vitorpamplona.quartz.marmot.RecipientRelayFetcher
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip17Dm.NIP17Factory
import com.vitorpamplona.quartz.nip17Dm.base.BaseDMGroupEvent
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import kotlinx.coroutines.delay

/**
 * NIP-17 direct-message verbs. All verbs reuse the Quartz gift-wrap pipeline
 * (NIP-17 chat message → NIP-59 seal → NIP-59 gift wrap via NIP-44) — this
 * file is pure orchestration over `quartz/` and `commons/`.
 *
 * Both kind:14 (text) and kind:15 (encrypted file header) are recognised on
 * receive; `dm send-file` publishes kind:15 with the AES-GCM key + nonce
 * carried in tags per NIP-17.
 *
 * NIP-17 says clients "shouldn't try" to send when the recipient hasn't
 * published a kind:10050. By default we honour that: an empty kind:10050
 * is a hard error. `--allow-fallback` opts back into the kind:10002 read
 * marker → bootstrap chain for the cases (interop tests, brand-new
 * accounts) where strict mode is too strict.
 */
object DmCommands {
    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int {
        if (tail.isEmpty()) return Json.error("bad_args", "dm <send|send-file|list|await> …")
        val rest = tail.drop(1).toTypedArray()
        return when (tail[0]) {
            "send" -> send(dataDir, rest)
            "send-file" -> sendFile(dataDir, rest)
            "list" -> list(dataDir, rest)
            "await" -> await(dataDir, rest)
            else -> Json.error("bad_args", "dm ${tail[0]}")
        }
    }

    private suspend fun send(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.size < 2) return Json.error("bad_args", "dm send <recipient> <text> [--allow-fallback]")
        val text = rest[1]
        val args = Args(rest.drop(2).toTypedArray())
        val allowFallback = args.bool("allow-fallback")

        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            val recipient = ctx.requireUserHex(rest[0])
            val template = ChatMessageEvent.build(text, listOf(PTag(recipient)))
            val result = NIP17Factory().createMessageNIP17(template, ctx.signer)
            return publishWraps(ctx, result, allowFallback)
        } finally {
            ctx.close()
        }
    }

    /**
     * Two modes:
     *
     *  - **Upload mode** (`--file PATH --server URL`): generate a random
     *    AES-GCM cipher, encrypt the local file, upload the ciphertext to
     *    the Blossom server, then publish a kind:15 referencing the
     *    returned URL. The auto-detected hash, size, dimensions, mime
     *    type, and blurhash from the upload are folded into the event.
     *
     *  - **Reference mode** (positional URL + `--key HEX --nonce HEX`):
     *    the file is already uploaded somewhere; just publish a kind:15
     *    pointing at it. Caller-supplied flags fill in the metadata.
     *
     * Mode selection: the `--file` flag turns on upload mode. Otherwise
     * reference mode is required.
     */
    private suspend fun sendFile(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.isEmpty()) return Json.error("bad_args", USAGE_SEND_FILE)
        val args = Args(rest.drop(1).toTypedArray())
        val allowFallback = args.bool("allow-fallback")
        val recipientInput = rest[0]

        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            val recipient = ctx.requireUserHex(recipientInput)

            val (template, summary) =
                if (args.flag("file") != null) {
                    buildUploadModeTemplate(ctx, recipient, args)
                        ?: return 1
                } else {
                    buildReferenceModeTemplate(args, recipient)
                        ?: return 1
                }

            val result = NIP17Factory().createEncryptedFileNIP17(template, ctx.signer)
            return publishWraps(ctx, result, allowFallback, extra = summary)
        } finally {
            ctx.close()
        }
    }

    private suspend fun buildUploadModeTemplate(
        ctx: Context,
        recipient: com.vitorpamplona.quartz.nip01Core.core.HexKey,
        args: Args,
    ): Pair<com.vitorpamplona.quartz.nip01Core.signers.EventTemplate<ChatMessageEncryptedFileHeaderEvent>, Map<String, Any?>>? {
        val file = java.io.File(args.requireFlag("file"))
        if (!file.exists()) {
            Json.error("bad_args", "file does not exist: ${file.absolutePath}")
            return null
        }
        val server = args.requireFlag("server")
        val cipher =
            com.vitorpamplona.quartz.utils.ciphers
                .AESGCM()
        val orchestrator = UploadOrchestrator()
        val uploaded = orchestrator.uploadEncrypted(file, cipher, server, ctx.signer)
        val uploadedUrl =
            uploaded.blossom.url ?: run {
                Json.error("upload_failed", "Blossom server $server returned no URL")
                return null
            }
        val mimeType = args.flag("mime-type") ?: uploaded.metadata.mimeType
        val dimension =
            uploaded.metadata.width?.let { w ->
                uploaded.metadata.height?.let { h ->
                    com.vitorpamplona.quartz.nip94FileMetadata.tags
                        .DimensionTag(w, h)
                }
            }
        val template =
            ChatMessageEncryptedFileHeaderEvent.build(
                to = listOf(PTag(recipient)),
                url = uploadedUrl,
                cipher = cipher,
                mimeType = mimeType,
                hash = uploaded.encryptedHash,
                size = uploaded.encryptedSize,
                dimension = dimension,
                blurhash = uploaded.metadata.blurhash,
                originalHash = uploaded.metadata.sha256,
            )
        // Surface the cipher material on stdout so callers can re-share
        // or republish the same encrypted blob without re-uploading.
        val summary =
            mapOf(
                "url" to uploadedUrl,
                "encryption_key" to cipher.keyBytes.toHexKey(),
                "encryption_nonce" to cipher.nonce.toHexKey(),
                "encrypted_hash" to uploaded.encryptedHash,
                "encrypted_size" to uploaded.encryptedSize,
                "original_hash" to uploaded.metadata.sha256,
                "mime_type" to mimeType,
            )
        return template to summary
    }

    private fun buildReferenceModeTemplate(
        args: Args,
        recipient: com.vitorpamplona.quartz.nip01Core.core.HexKey,
    ): Pair<com.vitorpamplona.quartz.nip01Core.signers.EventTemplate<ChatMessageEncryptedFileHeaderEvent>, Map<String, Any?>>? {
        val url =
            args.positionalOrNull(0) ?: run {
                Json.error("bad_args", USAGE_SEND_FILE)
                return null
            }
        val keyHex = args.requireFlag("key")
        val nonceHex = args.requireFlag("nonce")
        val keyBytes =
            runCatching { keyHex.hexToByteArray() }.getOrElse {
                Json.error("bad_args", "--key must be hex (got ${keyHex.length} chars)")
                return null
            }
        val nonceBytes =
            runCatching { nonceHex.hexToByteArray() }.getOrElse {
                Json.error("bad_args", "--nonce must be hex (got ${nonceHex.length} chars)")
                return null
            }
        val mimeType = args.flag("mime-type")
        val hash = args.flag("hash")
        val originalHash = args.flag("original-hash")
        val size = args.flags["size"]?.toIntOrNull()
        val blurhash = args.flag("blurhash")
        val dimension =
            args.flag("dim")?.let { raw ->
                val match =
                    Regex("^(\\d+)x(\\d+)$").matchEntire(raw)
                        ?: run {
                            Json.error("bad_args", "--dim must be WxH (got '$raw')")
                            return null
                        }
                com.vitorpamplona.quartz.nip94FileMetadata.tags
                    .DimensionTag(match.groupValues[1].toInt(), match.groupValues[2].toInt())
            }
        val cipher =
            com.vitorpamplona.quartz.utils.ciphers
                .AESGCM(keyBytes, nonceBytes)
        val template =
            ChatMessageEncryptedFileHeaderEvent.build(
                to = listOf(PTag(recipient)),
                url = url,
                cipher = cipher,
                mimeType = mimeType,
                hash = hash,
                size = size,
                dimension = dimension,
                blurhash = blurhash,
                originalHash = originalHash,
            )
        return template to emptyMap()
    }

    private const val USAGE_SEND_FILE: String =
        "dm send-file <recipient> [--file PATH --server URL | URL --key HEX --nonce HEX] " +
            "[--mime-type M] [--hash HEX] [--original-hash HEX] [--size N] [--dim WxH] [--blurhash S] [--allow-fallback]"

    private suspend fun publishWraps(
        ctx: Context,
        result: NIP17Factory.Result,
        allowFallback: Boolean,
        extra: Map<String, Any?> = emptyMap(),
    ): Int {
        val recipientsOut = mutableListOf<Map<String, Any?>>()
        for (wrap in result.wraps) {
            val target = wrap.recipientPubKey() ?: continue
            val resolution = resolveDmRelays(ctx, target, allowFallback)
            if (resolution.relays.isEmpty()) {
                return Json.error(
                    "no_dm_relays",
                    "$target has no kind:10050; pass --allow-fallback to use NIP-65 read or bootstrap",
                )
            }
            val ack = ctx.publish(wrap, resolution.relays)
            recipientsOut.add(
                mapOf(
                    "pubkey" to target,
                    "wrap_id" to wrap.id,
                    "published_to" to ack.filterValues { it }.keys.map { it.url },
                    "relays_tried" to resolution.relays.map { it.url },
                    "relay_source" to resolution.source,
                ),
            )
        }
        val out =
            buildMap {
                put("event_id", result.msg.id)
                put("kind", result.msg.kind)
                putAll(extra)
                put("recipients", recipientsOut)
            }
        Json.writeLine(out)
        return 0
    }

    private suspend fun list(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val peerInput = args.flag("peer")
        val sinceFlag = args.flags["since"]?.toLongOrNull()
        val limit = args.intFlag("limit", Int.MAX_VALUE)
        val timeoutSecs = args.longFlag("timeout", 8)

        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            val peerHex = peerInput?.let { ctx.requireUserHex(it) }

            val inbox =
                ctx
                    .inboxRelays()
                    .ifEmpty { ctx.outboxRelays() }
                    .ifEmpty { ctx.bootstrapRelays() }
            if (inbox.isEmpty()) return Json.error("no_inbox_relays", "configure relays or bootstrap defaults first")

            val advanceCursor = peerInput == null && sinceFlag == null
            val since = sinceFlag ?: ctx.state.giftWrapSince

            val filters =
                inbox
                    .flatMap { filterGiftWrapsToPubkey(it, ctx.signer.pubKey, since) }
                    .groupBy { it.relay }
                    .mapValues { (_, v) -> v.map { it.filter } }

            val raw = ctx.drain(filters, timeoutMs = timeoutSecs * 1000)

            val messages = decryptDms(ctx, raw, peerHex)
            val out =
                messages
                    .sortedBy { it.createdAt }
                    .takeLast(limit)
                    .map { it.toJson() }

            if (advanceCursor) {
                val maxSeen = messages.maxOfOrNull { it.createdAt }
                if (maxSeen != null) ctx.state.giftWrapSince = maxSeen
            }

            Json.writeLine(
                mapOf(
                    "peer" to peerHex,
                    "messages" to out,
                ),
            )
            return 0
        } finally {
            ctx.close()
        }
    }

    private suspend fun await(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val peerInput = args.requireFlag("peer")
        val match = args.requireFlag("match")
        val timeoutSecs = args.longFlag("timeout", 30)

        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            val peerHex = ctx.requireUserHex(peerInput)

            val inbox =
                ctx
                    .inboxRelays()
                    .ifEmpty { ctx.outboxRelays() }
                    .ifEmpty { ctx.bootstrapRelays() }
            if (inbox.isEmpty()) return Json.error("no_inbox_relays", "configure relays or bootstrap defaults first")

            val deadline = System.currentTimeMillis() + timeoutSecs * 1000
            var since = ctx.state.giftWrapSince

            while (System.currentTimeMillis() < deadline) {
                val filters =
                    inbox
                        .flatMap { filterGiftWrapsToPubkey(it, ctx.signer.pubKey, since) }
                        .groupBy { it.relay }
                        .mapValues { (_, v) -> v.map { it.filter } }

                val raw = ctx.drain(filters, timeoutMs = 3_000)
                val messages = decryptDms(ctx, raw, peerHex)
                // Match against the text body for kind:14 and against the URL
                // for kind:15 — both are exposed as `searchText` so callers
                // can grep for either with one --match flag.
                val hit = messages.firstOrNull { match in it.searchText }
                if (hit != null) {
                    Json.writeLine(hit.toJson())
                    return 0
                }
                val maxSeen = messages.maxOfOrNull { it.createdAt }
                if (maxSeen != null && (since == null || maxSeen > since)) since = maxSeen
                delay(2_000)
            }
            throw AwaitTimeout("no DM from $peerHex matching '$match' within ${timeoutSecs}s")
        } finally {
            ctx.close()
        }
    }

    /**
     * Per NIP-17: kind:1059 should only be delivered to relays the recipient
     * has advertised in their kind:10050. When that list is empty:
     *  - strict (default): refuse with no_dm_relays — caller must fix or
     *    explicitly opt into a fallback.
     *  - allowFallback=true: fall through to the NIP-65 read marker and then
     *    to our bootstrap pool.
     */
    private suspend fun resolveDmRelays(
        ctx: Context,
        recipient: HexKey,
        allowFallback: Boolean,
    ): RelaySet {
        val seed = ctx.bootstrapRelays()
        // Cache-first: if Amy has previously seen the recipient's
        // kind:10050 / 10051 / 10002 events, use the local copy and
        // skip the network drain entirely. Falls back to the live
        // fetcher only if the local store has nothing.
        val lists =
            ctx.cachedRelayListsOf(recipient)
                ?: RecipientRelayFetcher.fetchRelayLists(ctx.client, recipient, seed)
        val dmInbox = lists.dmInbox.toSet()
        if (dmInbox.isNotEmpty()) return RelaySet(dmInbox, "kind_10050")
        if (!allowFallback) return RelaySet(emptySet(), "kind_10050")
        val nip65Read = lists.nip65Read().toSet()
        if (nip65Read.isNotEmpty()) return RelaySet(nip65Read, "nip65_read")
        return RelaySet(seed, "bootstrap")
    }

    private data class RelaySet(
        val relays: Set<NormalizedRelayUrl>,
        val source: String,
    )

    private sealed interface DecryptedDm {
        val id: HexKey
        val wrapId: HexKey
        val from: HexKey
        val to: List<HexKey>
        val createdAt: Long
        val relay: String
        val searchText: String

        fun toJson(): Map<String, Any?>
    }

    private data class TextDm(
        override val id: HexKey,
        override val wrapId: HexKey,
        override val from: HexKey,
        override val to: List<HexKey>,
        val content: String,
        override val createdAt: Long,
        override val relay: String,
    ) : DecryptedDm {
        override val searchText: String get() = content

        override fun toJson(): Map<String, Any?> =
            mapOf(
                "type" to "text",
                "id" to id,
                "wrap_id" to wrapId,
                "from" to from,
                "to" to to,
                "content" to content,
                "created_at" to createdAt,
                "relay" to relay,
            )
    }

    private data class FileDm(
        override val id: HexKey,
        override val wrapId: HexKey,
        override val from: HexKey,
        override val to: List<HexKey>,
        val url: String,
        val mimeType: String?,
        val encryptionAlgo: String?,
        val decryptionKey: String?,
        val decryptionNonce: String?,
        val hash: String?,
        val originalHash: String?,
        val size: Int?,
        val dimensions: String?,
        val blurhash: String?,
        override val createdAt: Long,
        override val relay: String,
    ) : DecryptedDm {
        override val searchText: String get() = url

        override fun toJson(): Map<String, Any?> {
            val out =
                mutableMapOf<String, Any?>(
                    "type" to "file",
                    "id" to id,
                    "wrap_id" to wrapId,
                    "from" to from,
                    "to" to to,
                    "url" to url,
                    "created_at" to createdAt,
                    "relay" to relay,
                )
            if (mimeType != null) out["mime_type"] = mimeType
            if (encryptionAlgo != null) out["encryption_algorithm"] = encryptionAlgo
            if (decryptionKey != null) out["decryption_key"] = decryptionKey
            if (decryptionNonce != null) out["decryption_nonce"] = decryptionNonce
            if (hash != null) out["hash"] = hash
            if (originalHash != null) out["original_hash"] = originalHash
            if (size != null) out["size"] = size
            if (dimensions != null) out["dim"] = dimensions
            if (blurhash != null) out["blurhash"] = blurhash
            return out
        }
    }

    private suspend fun decryptDms(
        ctx: Context,
        raw: List<Pair<NormalizedRelayUrl, Event>>,
        peerHex: HexKey?,
    ): List<DecryptedDm> {
        val seen = HashSet<HexKey>()
        val out = mutableListOf<DecryptedDm>()
        for ((relay, event) in raw) {
            if (event !is GiftWrapEvent) continue
            // Quartz's Rumor.mergeWith forces the inner event's pubkey to the
            // seal author's, so the NIP-17 §7 step-3 impersonation check is
            // already enforced upstream — no need to redo it here.
            val inner = event.unwrapAndUnsealOrNull(ctx.signer) ?: continue
            if (inner !is BaseDMGroupEvent) continue
            if (!seen.add(inner.id)) continue
            if (peerHex != null && peerHex !in inner.groupMembers()) continue
            out.add(toDecrypted(inner, event.id, relay.url) ?: continue)
        }
        return out
    }

    private fun toDecrypted(
        inner: BaseDMGroupEvent,
        wrapId: HexKey,
        relayUrl: String,
    ): DecryptedDm? =
        when (inner) {
            is ChatMessageEvent -> {
                TextDm(
                    id = inner.id,
                    wrapId = wrapId,
                    from = inner.pubKey,
                    to = inner.recipientsPubKey(),
                    content = inner.content,
                    createdAt = inner.createdAt,
                    relay = relayUrl,
                )
            }

            is ChatMessageEncryptedFileHeaderEvent -> {
                FileDm(
                    id = inner.id,
                    wrapId = wrapId,
                    from = inner.pubKey,
                    to = inner.recipientsPubKey(),
                    url = inner.url(),
                    mimeType = inner.mimeType(),
                    encryptionAlgo = inner.algo(),
                    decryptionKey = inner.key()?.toHexKey(),
                    decryptionNonce = inner.nonce()?.toHexKey(),
                    hash = inner.hash(),
                    originalHash = inner.originalHash(),
                    size = inner.size(),
                    dimensions = inner.dimensions()?.let { "${it.width}x${it.height}" },
                    blurhash = inner.blurhash(),
                    createdAt = inner.createdAt,
                    relay = relayUrl,
                )
            }

            else -> {
                null
            }
        }
}
