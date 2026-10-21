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
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiArchiveLimits
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiImport
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.extractEmojiArchive
import com.vitorpamplona.amethyst.commons.service.upload.BlossomAuth
import com.vitorpamplona.amethyst.commons.service.upload.BlossomClient
import com.vitorpamplona.amethyst.commons.service.upload.BlossomPaymentException
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServersEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

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
    private const val TAG = "EmojiImportCommand"
    private const val PARALLEL_UPLOADS = 4

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
            val tooLarge = mutableListOf<String>()
            var truncated = false
            var badLines: List<Int> = emptyList()
            when {
                source.isDirectory -> {
                    val found =
                        source
                            .walkTopDown()
                            .onEnter { it == source || !it.name.startsWith(".") }
                            .filter { it.isFile && !it.name.startsWith(".") && EmojiImport.isImageFileName(it.name) }
                            .sortedBy { it.relativeTo(source).path.lowercase() }
                            .toList()
                    // The limits a .zip gets: each file is read whole into memory to hash and upload it.
                    val (small, big) = found.partition { it.length() <= EmojiArchiveLimits.MAX_ENTRY_BYTES }
                    big.mapTo(tooLarge) { it.name }
                    truncated = small.size > EmojiArchiveLimits.MAX_IMAGES
                    images = small.take(EmojiArchiveLimits.MAX_IMAGES).map { ImageFile(it.name, it) }
                }
                EmojiImport.isZipFileName(source.name) -> {
                    if (source.length() > EmojiArchiveLimits.MAX_ARCHIVE_BYTES) {
                        return Output.error("bad_args", "${source.name} is over ${EmojiArchiveLimits.MAX_ARCHIVE_BYTES / (1024 * 1024)} MB")
                    }
                    val archive =
                        try {
                            source.inputStream().use { extractEmojiArchive(it) }
                        } catch (e: IllegalArgumentException) {
                            return Output.error("bad_args", "${source.name}: ${e.message}")
                        }
                    archive.files.forEach { tempFiles.add(it.path.toFile()) }
                    images = archive.files.map { ImageFile(it.fileName, it.path.toFile()) }
                    tooLarge.addAll(archive.tooLarge)
                    truncated = archive.truncated
                }
                EmojiImport.isImageFileName(source.name) -> {
                    if (source.length() > EmojiArchiveLimits.MAX_ENTRY_BYTES) {
                        return Output.error("bad_args", "${source.name} is over ${EmojiArchiveLimits.MAX_ENTRY_BYTES / (1024 * 1024)} MB")
                    }
                    images = listOf(ImageFile(source.name, source))
                }
                else -> {
                    val parsed = EmojiImport.parseList(source.readText())
                    images = emptyList()
                    listed = parsed.emojis
                    badLines = parsed.badLines
                }
            }
            if (images.isEmpty() && listed.isEmpty()) return Output.error("bad_args", "no images or `shortcode url` lines found in $source")

            return EmojiCommands.withOwnPack(dataDir, packArg, timeoutMs) { ctx, pack, outbox ->
                // Decrypted before any upload: failing afterwards would strand them all.
                val existing =
                    existingCodes(ctx, pack, isPrivate)
                        ?: return@withOwnPack Output.error("decrypt_failed", "could not decrypt the private emojis of '${pack.dTag()}'")
                val plan = EmojiImport.plan(images.map { it.name }, existing, replace)

                val extra =
                    mapOf(
                        "source" to source.path,
                        "unnamed" to plan.unnamed,
                        "too_large" to tooLarge,
                        "truncated" to truncated,
                        "bad_lines" to badLines,
                    )

                if (dryRun) {
                    Output.emit(
                        mapOf(
                            "d_tag" to pack.dTag(),
                            "private" to isPrivate,
                            "dry_run" to true,
                            "to_upload" to plan.toUpload.map { (index, code) -> mapOf("shortcode" to code, "file" to images[index].name) },
                            "to_add" to listed.map { mapOf("shortcode" to it.code, "url" to it.url) },
                            "skipped" to plan.skipped,
                        ) + extra,
                    )
                    return@withOwnPack 0
                }

                val uploads = arrayOfNulls<EmojiUrlTag>(plan.toUpload.size)
                val failed = ConcurrentLinkedQueue<Map<String, String>>()
                var server: String? = null
                if (plan.toUpload.isNotEmpty()) {
                    val target = serverFlag ?: defaultServer(ctx, timeoutMs) ?: return@withOwnPack Output.error("no_servers", "no --server given and no Blossom server in your kind:10063 list")
                    server = target
                    val paymentRequired = AtomicReference<BlossomPaymentException?>(null)
                    uploadAll(ctx, target, plan.toUpload.map { (index, code) -> code to images[index] }, uploads, failed, paymentRequired)
                    paymentRequired.get()?.let { e ->
                        return@withOwnPack Output.error("payment_required", "server $target requires payment: ${e.payment.reason ?: "402"}")
                    }
                    if (uploads.all { it == null } && listed.isEmpty()) {
                        return@withOwnPack Output.error("upload_failed", "every upload to $target failed: ${failed.firstOrNull()?.get("error")}")
                    }
                }
                val uploaded = uploads.filterNotNull()

                val onConflict = if (replace) OnConflict.REPLACE else OnConflict.SKIP
                val result = EmojiPackActions.addEmojis(ctx.signer, pack, uploaded + listed, isPrivate, onConflict)
                val merged = EmojiPackActions.AddResult(result.event, result.added, result.replaced, plan.skipped + result.skipped, result.invalid)
                EmojiCommands.emitAddResult(ctx, pack, merged, outbox, isPrivate, extra + mapOf("server" to server, "uploaded" to uploaded.size, "failed" to failed.toList()))
            }
        } finally {
            tempFiles.forEach { if (!it.delete() && it.exists()) Log.w(TAG) { "could not delete temp file $it" } }
        }
    }

    /**
     * Uploads each `(shortcode, image)` of [jobs] to [server], [PARALLEL_UPLOADS] at a time, into
     * the same index of [uploads] (so the pack keeps the batch's order). A 402 stops the rest.
     */
    private suspend fun uploadAll(
        ctx: Context,
        server: String,
        jobs: List<Pair<String, ImageFile>>,
        uploads: Array<EmojiUrlTag?>,
        failed: ConcurrentLinkedQueue<Map<String, String>>,
        paymentRequired: AtomicReference<BlossomPaymentException?>,
    ) {
        val client = BlossomClient()
        val slots = Semaphore(PARALLEL_UPLOADS)
        val started = AtomicInteger(0)
        coroutineScope {
            jobs.forEachIndexed { i, (code, image) ->
                launch(Dispatchers.IO) {
                    slots.withPermit {
                        if (paymentRequired.get() != null) return@withPermit
                        System.err.println("[${started.incrementAndGet()}/${jobs.size}] uploading ${image.name} as :$code:")
                        try {
                            val bytes = image.file.readBytes()
                            val auth = BlossomAuth.createUploadAuth(sha256(bytes).toHexKey(), bytes.size.toLong(), "Upload emoji $code", ctx.signer, servers = listOf(server))
                            val mime = EmojiImport.imageMimeTypeOf(image.name) ?: "application/octet-stream"
                            val url = client.upload(bytes, mime, server, auth).url
                            if (url.isNullOrBlank()) failed.add(mapOf("file" to image.name, "error" to "server returned no url")) else uploads[i] = EmojiUrlTag(code, url)
                        } catch (e: BlossomPaymentException) {
                            paymentRequired.compareAndSet(null, e)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            failed.add(mapOf("file" to image.name, "error" to (e.message ?: e::class.simpleName ?: "error")))
                        }
                    }
                }
            }
        }
    }

    /** The shortcodes already in the bucket being added to; null when the private one can't be decrypted. */
    private suspend fun existingCodes(
        ctx: Context,
        pack: EmojiPackEvent,
        isPrivate: Boolean,
    ): Set<String>? =
        if (isPrivate) {
            pack.privateEmojis(ctx.signer)?.mapTo(HashSet()) { it.code }
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
