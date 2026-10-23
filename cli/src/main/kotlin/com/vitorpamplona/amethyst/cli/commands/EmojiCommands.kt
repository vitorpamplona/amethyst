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
import com.vitorpamplona.amethyst.commons.actions.EmojiPackActions
import com.vitorpamplona.amethyst.commons.actions.EmojiPackActions.OnConflict
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.selection.EmojiListEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.taggedEmojis

/**
 * `amy emoji …` — NIP-30 custom emoji packs (kind:30030) and the user's emoji list
 * (kind:10030). Every mutation fetches the freshest version of the event from the outbox
 * first and builds on it through commons' [EmojiPackActions], the same code the app runs.
 *
 * PACK is the `d` tag of one of your packs, or for read-only verbs and `use`/`unuse`, any
 * pack as `naddr1…` or `30030:<pubkey>:<d>`.
 */
object EmojiCommands {
    val USAGE: String =
        """
        |amy emoji — NIP-30 custom emoji packs
        |
        |  emoji pack list [USER]                     USER's packs (default: yours)
        |  emoji pack show PACK                       a pack's metadata and emojis (your private ones decrypted)
        |  emoji pack create --title T [--description D] [--image URL] [--d DTAG]
        |  emoji pack edit PACK [--title T] [--description D] [--image URL]
        |                                              change metadata; an empty value clears it
        |  emoji pack delete PACK                     NIP-09 deletion request for your pack
        |  emoji add PACK SHORTCODE URL [--private] [--replace]
        |                                              add one emoji; --replace swaps the url of an existing shortcode
        |  emoji remove PACK SHORTCODE[,SHORTCODE…] [--private]
        |  emoji import PACK SOURCE [--private] [--replace] [--server URL] [--dry-run]
        |                                              bulk add from a folder of images, a .zip of images, or a
        |                                              text list (`shortcode url` per line). Images are uploaded
        |                                              to --server or your first kind:10063 Blossom server, and
        |                                              named after their file (`party parrot.gif` → party_parrot).
        |  emoji list                                 the packs in your emoji list (kind:10030)
        |  emoji use PACK / emoji unuse PACK          add / remove a pack to / from your emoji list
        |
        |  Every verb takes --timeout SECS (default 8) for the relay fetch.
        """.trimMargin()

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "emoji",
            tail,
            "emoji <pack|add|remove|import|list|use|unuse>",
            help = USAGE,
            routes =
                mapOf(
                    "pack" to { rest -> pack(dataDir, rest) },
                    "add" to { rest -> add(dataDir, rest) },
                    "remove" to { rest -> remove(dataDir, rest) },
                    "import" to { rest -> EmojiImportCommand.run(dataDir, rest) },
                    "list" to { rest -> list(dataDir, rest) },
                    "use" to { rest -> use(dataDir, rest, add = true) },
                    "unuse" to { rest -> use(dataDir, rest, add = false) },
                ),
        )

    private suspend fun pack(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "emoji pack",
            tail,
            "emoji pack <list|show|create|edit|delete>",
            help = USAGE,
            routes =
                mapOf(
                    "list" to { rest -> packList(dataDir, rest) },
                    "show" to { rest -> packShow(dataDir, rest) },
                    "create" to { rest -> packCreate(dataDir, rest) },
                    "edit" to { rest -> packEdit(dataDir, rest) },
                    "delete" to { rest -> packDelete(dataDir, rest) },
                ),
        )

    private suspend fun packList(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val userArg = args.positionalOrNull(0)
        val timeoutMs = args.timeoutMs(8)
        args.rejectUnknown()

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val author = userArg?.let { ctx.requireUserHex(it) } ?: ctx.identity.pubKeyHex
            val filter = Filter(kinds = listOf(EmojiPackEvent.KIND), authors = listOf(author))
            ctx.drain(readRelays(ctx, author).associateWith { listOf(filter) }, timeoutMs)
            val packs =
                ctx.store
                    .query<EmojiPackEvent>(filter)
                    .sortedBy { (it.titleOrName() ?: it.dTag()).lowercase() }
            Output.emit(
                mapOf(
                    "pubkey" to author,
                    "count" to packs.size,
                    "packs" to packs.map { packSummary(ctx, it) },
                ),
            )
            return 0
        }
    }

    private suspend fun packShow(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val packArg = args.positional(0, "pack")
        val timeoutMs = args.timeoutMs(8)
        args.rejectUnknown()

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val address = resolvePack(ctx, packArg) ?: return badPack(packArg)
            val pack = fetchPack(ctx, address, timeoutMs) ?: return Output.error("not_found", "no emoji pack at ${address.toValue()}")
            val private = if (pack.pubKey == ctx.identity.pubKeyHex) pack.privateEmojis(ctx.signer).orEmpty() else emptyList()
            Output.emit(
                packSummary(ctx, pack) +
                    mapOf(
                        "emojis" to pack.publicEmojis().map { emojiMap(it, false) } + private.map { emojiMap(it, true) },
                    ),
            )
            return 0
        }
    }

    private suspend fun packCreate(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val title = args.requireFlag("title")
        val description = args.flag("description")
        val image = args.flag("image")
        val dTag = args.flag("d")
        val timeoutMs = args.timeoutMs(8)
        args.rejectUnknown()
        if (title.isBlank()) return Output.error("bad_args", "--title cannot be blank")

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val outbox = requireOutbox(ctx) ?: return Output.error("no_relays", NO_RELAYS)
            if (dTag != null) {
                val existing = fetchPack(ctx, Address(EmojiPackEvent.KIND, ctx.identity.pubKeyHex, dTag), timeoutMs)
                if (existing != null) return Output.error("exists", "you already have a pack with d tag '$dTag'")
            }
            val event =
                if (dTag != null) {
                    EmojiPackActions.create(ctx.signer, title, description, image, dTag)
                } else {
                    EmojiPackActions.create(ctx.signer, title, description, image)
                }
            return publish(ctx, event, outbox, packSummary(ctx, event))
        }
    }

    private suspend fun packEdit(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val packArg = args.positional(0, "pack")
        val title = args.flag("title")
        val description = args.flag("description")
        val image = args.flag("image")
        val timeoutMs = args.timeoutMs(8)
        args.rejectUnknown()
        if (title == null && description == null && image == null) return Output.error("bad_args", "emoji pack edit needs --title, --description or --image")
        if (title != null && title.isBlank()) return Output.error("bad_args", "--title cannot be blank")

        return withOwnPack(dataDir, packArg, timeoutMs) { ctx, pack, outbox ->
            val event =
                EmojiPackActions.updateMetadata(
                    ctx.signer,
                    pack,
                    title ?: pack.titleOrName() ?: pack.dTag(),
                    description ?: pack.description(),
                    image ?: pack.image(),
                )
            publish(ctx, event, outbox, packSummary(ctx, event) + mapOf("based_on" to pack.id))
        }
    }

    private suspend fun packDelete(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val packArg = args.positional(0, "pack")
        val timeoutMs = args.timeoutMs(8)
        args.rejectUnknown()

        return withOwnPack(dataDir, packArg, timeoutMs) { ctx, pack, outbox ->
            val event = ctx.signer.sign(DeletionRequestEvent.build(listOf(pack)))
            publish(ctx, event, outbox, mapOf("d_tag" to pack.dTag(), "deleted_event_id" to pack.id))
        }
    }

    private suspend fun add(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val packArg = args.positional(0, "pack")
        val code = args.positional(1, "shortcode").removePrefix(":").removeSuffix(":")
        val url = args.positional(2, "url")
        val isPrivate = args.bool("private")
        val replace = args.bool("replace")
        val timeoutMs = args.timeoutMs(8)
        args.rejectUnknown()
        if (!EmojiUrlTag.isValidShortcode(code)) return Output.error("bad_args", "invalid shortcode '$code' (letters, digits, '-' and '_' only)")

        return withOwnPack(dataDir, packArg, timeoutMs) { ctx, pack, outbox ->
            val result = EmojiPackActions.addEmojis(ctx.signer, pack, listOf(EmojiUrlTag(code, url)), isPrivate, if (replace) OnConflict.REPLACE else OnConflict.SKIP)
            emitAddResult(ctx, pack, result, outbox, isPrivate)
        }
    }

    private suspend fun remove(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val packArg = args.positional(0, "pack")
        val codes =
            args
                .positional(1, "shortcode")
                .split(',')
                .map { it.trim().removePrefix(":").removeSuffix(":") }
                .filter { it.isNotEmpty() }
        val isPrivate = args.bool("private")
        val timeoutMs = args.timeoutMs(8)
        args.rejectUnknown()

        return withOwnPack(dataDir, packArg, timeoutMs) { ctx, pack, outbox ->
            val result = EmojiPackActions.removeEmojis(ctx.signer, pack, codes, isPrivate)
            val fields =
                mapOf(
                    "d_tag" to pack.dTag(),
                    "private" to isPrivate,
                    "changed" to (result.event != null),
                    "removed" to result.removed,
                    "missing" to result.missing,
                    "based_on" to pack.id,
                )
            val event = result.event
            if (event == null) {
                Output.emit(fields)
                0
            } else {
                publish(ctx, event, outbox, fields)
            }
        }
    }

    private suspend fun list(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val timeoutMs = args.timeoutMs(8)
        args.rejectUnknown()

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val self = ctx.identity.pubKeyHex
            val selection = fetchEmojiList(ctx, self, timeoutMs)
            Output.emit(
                mapOf(
                    "event_id" to selection?.id,
                    "packs" to selection?.emojiPacks().orEmpty().map { mapOf("address" to it.toValue(), "naddr" to NAddress.createOrNull(it.kind, it.pubKeyHex, it.dTag, null)) },
                    "emojis" to selection?.taggedEmojis().orEmpty().map { emojiMap(it, false) },
                ),
            )
            return 0
        }
    }

    private suspend fun use(
        dataDir: DataDir,
        rest: Array<String>,
        add: Boolean,
    ): Int {
        val args = Args(rest)
        val packArg = args.positional(0, "pack")
        val timeoutMs = args.timeoutMs(8)
        args.rejectUnknown()

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val outbox = requireOutbox(ctx) ?: return Output.error("no_relays", NO_RELAYS)
            val address = resolvePack(ctx, packArg) ?: return badPack(packArg)
            val pack = fetchPack(ctx, address, timeoutMs) ?: return Output.error("not_found", "no emoji pack at ${address.toValue()}")
            val selection = fetchEmojiList(ctx, ctx.identity.pubKeyHex, timeoutMs)
            val listed = selection?.hasEmojiPack(pack.addressTag()) == true
            val fields = mapOf("address" to pack.addressTag(), "op" to if (add) "use" else "unuse", "based_on" to selection?.id)

            if (listed == add) {
                Output.emit(fields + mapOf("changed" to false))
                return 0
            }
            val event =
                when {
                    !add -> ctx.signer.sign(EmojiListEvent.remove(selection!!, pack))
                    selection == null -> ctx.signer.sign(EmojiListEvent.build(listOf(EventHintBundle(pack))))
                    else -> ctx.signer.sign(EmojiListEvent.add(selection, EventHintBundle(pack)))
                }
            return publish(ctx, event, outbox, fields + mapOf("changed" to true))
        }
    }

    // ---- shared helpers (also used by EmojiImportCommand) ----

    const val NO_RELAYS = "no outbox relays configured; run `amy relay add` or `amy create`"

    /**
     * Opens a context, resolves [packArg] to one of the user's own packs at its freshest
     * version, and runs [block] with it. Fails with `not_found` when there is no such pack.
     */
    suspend fun withOwnPack(
        dataDir: DataDir,
        packArg: String,
        timeoutMs: Long,
        block: suspend (Context, EmojiPackEvent, Set<NormalizedRelayUrl>) -> Int,
    ): Int {
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val outbox = requireOutbox(ctx) ?: return Output.error("no_relays", NO_RELAYS)
            val address = resolvePack(ctx, packArg) ?: return badPack(packArg)
            if (address.pubKeyHex != ctx.identity.pubKeyHex) return Output.error("bad_args", "${address.toValue()} is not your pack")
            val pack = fetchPack(ctx, address, timeoutMs) ?: return Output.error("not_found", "you have no emoji pack '${address.dTag}'")
            return block(ctx, pack, outbox)
        }
    }

    suspend fun emitAddResult(
        ctx: Context,
        pack: EmojiPackEvent,
        result: EmojiPackActions.AddResult,
        outbox: Set<NormalizedRelayUrl>,
        isPrivate: Boolean,
        extra: Map<String, Any?> = emptyMap(),
    ): Int {
        val fields =
            mapOf(
                "d_tag" to pack.dTag(),
                "private" to isPrivate,
                "changed" to (result.event != null),
                "added" to result.added,
                "replaced" to result.replaced,
                "skipped" to result.skipped,
                "invalid" to result.invalid,
                "based_on" to pack.id,
            ) + extra
        val event = result.event
        if (event == null) {
            Output.emit(fields)
            return 0
        }
        return publish(ctx, event, outbox, fields)
    }

    private suspend fun publish(
        ctx: Context,
        event: Event,
        outbox: Set<NormalizedRelayUrl>,
        fields: Map<String, Any?>,
    ): Int {
        val ack = ctx.publish(event, outbox)
        RawEventSupport.publishGuard(ack, event.id)?.let { return it }
        Output.emit(fields + mapOf("event_id" to event.id, "created_at" to event.createdAt) + RawEventSupport.ackFields(ack))
        return 0
    }

    private suspend fun requireOutbox(ctx: Context): Set<NormalizedRelayUrl>? = ctx.outboxRelays().ifEmpty { null }

    private fun badPack(packArg: String) = Output.error("bad_args", "not a pack: '$packArg' (expected your pack's d tag, naddr1…, or 30030:<pubkey>:<d>)")

    /** A `d` tag of the user's own pack, an `naddr1…`, or a `30030:<pubkey>:<d>` coordinate. */
    private fun resolvePack(
        ctx: Context,
        input: String,
    ): Address? {
        val trimmed = input.trim().removePrefix("nostr:")
        val address =
            when {
                trimmed.startsWith("naddr1") -> NAddress.parse(trimmed)?.let { Address(it.kind, it.author, it.dTag) }
                trimmed.startsWith("${EmojiPackEvent.KIND}:") -> Address.parse(trimmed)
                else -> Address(EmojiPackEvent.KIND, ctx.identity.pubKeyHex, trimmed)
            }
        return address?.takeIf { it.kind == EmojiPackEvent.KIND }
    }

    /**
     * Where [author]'s events are found: our outbox, plus for someone else their NIP-65 outbox
     * and the index relays. Ours stays in for them too: their list may name only relays amy
     * drops from other people's lists (loopback, private ranges).
     */
    private suspend fun readRelays(
        ctx: Context,
        author: HexKey,
    ): Set<NormalizedRelayUrl> =
        if (author == ctx.identity.pubKeyHex) {
            ctx.outboxRelays()
        } else {
            ctx.outboxRelays() + ctx.relaysOf(author)?.writeRelaysNorm().orEmpty() + ctx.indexRelays()
        }

    /**
     * The freshest version of the pack at [address]: what the relays return merged with the
     * local store (which holds what amy last published), so a stale relay read can't roll an
     * edit back.
     */
    suspend fun fetchPack(
        ctx: Context,
        address: Address,
        timeoutMs: Long,
    ): EmojiPackEvent? {
        val filter = Filter(kinds = listOf(address.kind), authors = listOf(address.pubKeyHex), tags = mapOf("d" to listOf(address.dTag)), limit = 1)
        ctx.drain(readRelays(ctx, address.pubKeyHex).associateWith { listOf(filter) }, timeoutMs)
        return ctx.store.query<EmojiPackEvent>(filter).maxByOrNull { it.createdAt }
    }

    private suspend fun fetchEmojiList(
        ctx: Context,
        self: HexKey,
        timeoutMs: Long,
    ): EmojiListEvent? {
        val filter = Filter(kinds = listOf(EmojiListEvent.KIND), authors = listOf(self), limit = 1)
        ctx.drain(ctx.outboxRelays().associateWith { listOf(filter) }, timeoutMs)
        return ctx.latestReplaceable(self, EmojiListEvent.KIND) as? EmojiListEvent
    }

    private suspend fun packSummary(
        ctx: Context,
        pack: EmojiPackEvent,
    ): Map<String, Any?> {
        val mine = pack.pubKey == ctx.identity.pubKeyHex
        return mapOf(
            "d_tag" to pack.dTag(),
            "address" to pack.addressTag(),
            "naddr" to NAddress.createOrNull(pack.kind, pack.pubKey, pack.dTag(), null),
            "author" to pack.pubKey,
            "title" to (pack.titleOrName() ?: pack.dTag()),
            "description" to pack.description(),
            "image" to pack.image(),
            "public_count" to pack.publicEmojiCodes().size,
            "private_count" to if (mine) pack.privateEmojis(ctx.signer)?.size else null,
            "event_id" to pack.id,
            "created_at" to pack.createdAt,
        )
    }

    private fun emojiMap(
        emoji: EmojiUrlTag,
        isPrivate: Boolean,
    ): Map<String, Any?> = mapOf("shortcode" to emoji.code, "url" to emoji.url, "private" to isPrivate)
}
