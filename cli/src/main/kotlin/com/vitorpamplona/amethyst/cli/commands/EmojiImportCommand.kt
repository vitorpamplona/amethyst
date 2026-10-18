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
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiImport
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.extractEmojiArchive
import com.vitorpamplona.amethyst.commons.service.upload.BlossomAuth
import com.vitorpamplona.amethyst.commons.service.upload.BlossomClient
import com.vitorpamplona.amethyst.commons.service.upload.BlossomPaymentException
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServersEvent
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlinx.coroutines.CancellationException
import java.io.File

/**
 * `amy emoji import PACK SOURCE` — bulk-adds emojis to one of your packs in a single new
 * version of it. SOURCE is a folder of images (searched recursively), a `.zip` of images, a
 * single image, or a text list of `shortcode url` lines. Images are named by
 * [EmojiImport.assignShortcodes] and uploaded to Blossom; a list is added as is.
 *
 * Shortcodes already in the pack are left alone (and not uploaded) unless `--replace`.
 * A failed upload doesn't stop the rest: it is reported under `failed`.
 */
object EmojiImportCommand {
    private class ImageFile(
        val name: String,
        val file: File,
    )

    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val packArg = args.positional(0, "pack")
        val source = File(args.positional(1, "source"))
        val isPrivate = args.bool("private")
        val replace = args.bool("replace")
        val serverFlag = args.flag("server")
        val dryRun = args.bool("dry-run")
        val timeoutMs = args.timeoutMs(8)
        args.rejectUnknown()
        if (!source.exists()) return Output.error("bad_args", "no such file or folder: $source")

        val tempFiles = mutableListOf<File>()
        try {
            val images: List<ImageFile>
            var listed: List<EmojiUrlTag> = emptyList()
            var tooLarge: List<String> = emptyList()
            var badLines: List<Int> = emptyList()
            when {
                source.isDirectory -> {
                    images =
                        source
                            .walkTopDown()
                            .onEnter { it == source || !it.name.startsWith(".") }
                            .filter { it.isFile && !it.name.startsWith(".") && EmojiImport.isImageFileName(it.name) }
                            .sortedBy { it.relativeTo(source).path.lowercase() }
                            .map { ImageFile(it.name, it) }
                            .toList()
                }
                EmojiImport.isZipFileName(source.name) -> {
                    val archive =
                        try {
                            source.inputStream().use { extractEmojiArchive(it) }
                        } catch (e: IllegalArgumentException) {
                            return Output.error("bad_args", "${source.name}: ${e.message}")
                        }
                    archive.files.forEach { tempFiles.add(it.path.toFile()) }
                    images = archive.files.map { ImageFile(it.fileName, it.path.toFile()) }
                    tooLarge = archive.tooLarge
                }
                EmojiImport.isImageFileName(source.name) -> images = listOf(ImageFile(source.name, source))
                else -> {
                    val parsed = EmojiImport.parseList(source.readText())
                    images = emptyList()
                    listed = parsed.emojis
                    badLines = parsed.badLines
                }
            }
            if (images.isEmpty() && listed.isEmpty()) return Output.error("bad_args", "no images or `shortcode url` lines found in $source")

            val codes = EmojiImport.assignShortcodes(images.map { it.name })
            val named = images.zip(codes).mapNotNull { (image, code) -> code?.let { it to image } }
            val unnamed = images.zip(codes).filter { it.second == null }.map { it.first.name }

            return EmojiCommands.withOwnPack(dataDir, packArg, timeoutMs) { ctx, pack, outbox ->
                val existing = existingCodes(ctx, pack, isPrivate)
                val (toUpload, alreadyThere) = named.partition { (code, _) -> replace || code !in existing }

                val extra =
                    mapOf(
                        "source" to source.path,
                        "unnamed" to unnamed,
                        "too_large" to tooLarge,
                        "bad_lines" to badLines,
                    )

                if (dryRun) {
                    Output.emit(
                        mapOf(
                            "d_tag" to pack.dTag(),
                            "private" to isPrivate,
                            "dry_run" to true,
                            "to_upload" to toUpload.map { (code, image) -> mapOf("shortcode" to code, "file" to image.name) },
                            "to_add" to listed.map { mapOf("shortcode" to it.code, "url" to it.url) },
                            "skipped" to alreadyThere.map { it.first },
                        ) + extra,
                    )
                    return@withOwnPack 0
                }

                val uploaded = mutableListOf<EmojiUrlTag>()
                val failed = mutableListOf<Map<String, String>>()
                var server: String? = null
                if (toUpload.isNotEmpty()) {
                    server = serverFlag ?: defaultServer(ctx, timeoutMs) ?: return@withOwnPack Output.error("no_servers", "no --server given and no Blossom server in your kind:10063 list")
                    val client = BlossomClient()
                    toUpload.forEachIndexed { index, (code, image) ->
                        System.err.println("[${index + 1}/${toUpload.size}] uploading ${image.name} as :$code:")
                        try {
                            val bytes = image.file.readBytes()
                            val auth = BlossomAuth.createUploadAuth(sha256(bytes).toHexKey(), bytes.size.toLong(), "Upload emoji $code", ctx.signer, servers = listOf(server))
                            val mime = EmojiImport.imageMimeTypeOf(image.name) ?: "application/octet-stream"
                            val url = client.upload(bytes, mime, server, auth).url
                            if (url.isNullOrBlank()) failed.add(mapOf("file" to image.name, "error" to "server returned no url")) else uploaded.add(EmojiUrlTag(code, url))
                        } catch (e: BlossomPaymentException) {
                            return@withOwnPack Output.error("payment_required", "server $server requires payment: ${e.payment.reason ?: "402"}")
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            failed.add(mapOf("file" to image.name, "error" to (e.message ?: e::class.simpleName ?: "error")))
                        }
                    }
                    if (uploaded.isEmpty() && listed.isEmpty()) {
                        return@withOwnPack Output.error("upload_failed", "every upload to $server failed: ${failed.firstOrNull()?.get("error")}")
                    }
                }

                val onConflict = if (replace) OnConflict.REPLACE else OnConflict.SKIP
                val result = EmojiPackActions.addEmojis(ctx.signer, pack, uploaded + listed, isPrivate, onConflict)
                val alsoSkipped = alreadyThere.map { it.first }
                val merged = EmojiPackActions.AddResult(result.event, result.added, result.replaced, alsoSkipped + result.skipped, result.invalid)
                EmojiCommands.emitAddResult(ctx, pack, merged, outbox, isPrivate, extra + mapOf("server" to server, "uploaded" to uploaded.size, "failed" to failed))
            }
        } finally {
            tempFiles.forEach { it.delete() }
        }
    }

    private suspend fun existingCodes(
        ctx: Context,
        pack: EmojiPackEvent,
        isPrivate: Boolean,
    ): Set<String> =
        if (isPrivate) {
            pack.privateEmojis(ctx.signer)?.map { it.code }?.toSet() ?: emptySet()
        } else {
            pack.publicEmojiCodes().toSet()
        }

    /** The first server of the user's kind:10063 Blossom server list. */
    private suspend fun defaultServer(
        ctx: Context,
        timeoutMs: Long,
    ): String? {
        val self = ctx.identity.pubKeyHex
        val filter = Filter(kinds = listOf(BlossomServersEvent.KIND), authors = listOf(self), limit = 1)
        ctx.drain(ctx.outboxRelays().associateWith { listOf(filter) }, timeoutMs)
        return (ctx.latestReplaceable(self, BlossomServersEvent.KIND) as? BlossomServersEvent)?.servers()?.firstOrNull()
    }
}
