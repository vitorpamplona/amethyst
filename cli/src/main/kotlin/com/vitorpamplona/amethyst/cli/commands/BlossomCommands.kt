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
import com.vitorpamplona.amethyst.commons.service.upload.BlossomAuth
import com.vitorpamplona.amethyst.commons.service.upload.BlossomClient
import com.vitorpamplona.amethyst.commons.service.upload.BlossomPaymentException
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip56Reports.ReportType
import com.vitorpamplona.quartz.nipB7Blossom.BlossomReport
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServerUrl
import com.vitorpamplona.quartz.utils.sha256.sha256
import java.io.File
import java.nio.file.Files

/**
 * `amy blossom <upload|media|download|list|delete|check|mirror|report>` — Blossom
 * blob storage (nak's `blossom`, but fuller). Auth'd operations use the active
 * account (BUD-01/02/04/05/09 kind:24242 events); downloads and HEAD checks are
 * public.
 *
 *   upload   --server URL FILE [--mime-type M]
 *   media    --server URL FILE [--mime-type M]      (BUD-05 optimize on upload)
 *   download URL [--out FILE]                        (or: download HASH --server URL)
 *   list     --server URL [USER]                     (USER defaults to the active account)
 *   delete   HASH --server URL
 *   check    --server URL HASH[,HASH]                (BUD-01 HEAD probe)
 *   mirror   --server URL SOURCE-URL                 (BUD-04)
 *   report   --server URL HASH [--type T] [--comment C] [--uploader HEX]
 *
 * Thin assembly only: all HTTP + auth live in commons `BlossomClient` /
 * `BlossomAuth` and quartz `BlossomAuthorizationEvent` / `BlossomReport`; this
 * file wires flags and shapes output. Auth tokens are scoped to `--server` so
 * they can't be replayed elsewhere (BUD-11).
 */
object BlossomCommands {
    val USAGE: String =
        """
        |Blossom blobs (NIP-B7 / BUD-01/02/04/05/09):
        |  blossom upload --server URL FILE             upload a file (authed); prints the blob URL.
        |          [--mime-type M]
        |  blossom media --server URL FILE              BUD-05: upload + let the server optimize the
        |          [--mime-type M]                       media (transcode/strip); prints the blob URL.
        |  blossom download URL [--out FILE]            download a blob (public). Accepts a full URL,
        |  blossom download HASH --server URL            or a HASH plus --server.
        |  blossom list --server URL [USER]             list a user's blobs (defaults to self)
        |  blossom delete HASH --server URL             delete a blob you own
        |  blossom check --server URL HASH[,HASH]       HEAD-check blobs exist (fails if any missing)
        |  blossom mirror --server URL SOURCE-URL       ask the server to mirror a blob (BUD-04)
        |  blossom report --server URL HASH             PUT a signed NIP-56 blob report (BUD-09);
        |          [--type T] [--comment C]              --type is a NIP-56 code (spam, illegal,
        |          [--uploader HEX]                      nudity, malware, …; default: other)
        """.trimMargin()

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "blossom",
            tail,
            "blossom <upload|media|download|list|delete|check|mirror|report>",
            help = USAGE,
            routes =
                mapOf(
                    "upload" to { rest -> upload(dataDir, rest, media = false) },
                    "media" to { rest -> upload(dataDir, rest, media = true) },
                    "download" to { rest -> download(dataDir, rest) },
                    "list" to { rest -> list(dataDir, rest) },
                    "delete" to { rest -> delete(dataDir, rest) },
                    "check" to { rest -> check(dataDir, rest) },
                    "mirror" to { rest -> mirror(dataDir, rest) },
                    "report" to { rest -> report(dataDir, rest) },
                ),
        )

    /** `blossom check --server URL HASH[,HASH]` — HEAD each blob; fail if any is missing (BUD-01). */
    private suspend fun check(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val server = args.flag("server") ?: return Output.error("bad_args", "blossom check requires --server URL")
        val hashes =
            args
                .positional(0, "sha256")
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        args.rejectUnknown()

        // Read-only HEAD probe — no auth, so it runs anonymously without an account.
        Context.openOrAnonymous(dataDir).use { _ ->
            val client = BlossomClient()
            val results =
                hashes.map { hash ->
                    val found = client.has(hash, server)
                    mapOf("sha256" to hash, "found" to found)
                }
            val allFound = results.all { it["found"] == true }
            Output.emit(mapOf("server" to server, "all_found" to allFound, "results" to results))
            return if (allFound) 0 else 1
        }
    }

    /**
     * `blossom mirror --server URL SOURCE-URL` — ask the server to mirror the
     * blob at SOURCE-URL (BUD-04). The sha256 (last path segment of the source
     * URL) is signed into the upload auth.
     */
    private suspend fun mirror(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val server = args.flag("server") ?: return Output.error("bad_args", "blossom mirror requires --server URL")
        val sourceUrl = args.positional(0, "source-url")
        val hash =
            sourceUrl
                .substringBefore('?')
                .substringBefore('#')
                .substringAfterLast('/')
                .substringBefore('.')
        if (hash.length != 64 || hash.any { it !in "0123456789abcdef" }) {
            return Output.error("bad_args", "could not extract a sha256 from the source url '$sourceUrl'")
        }
        args.rejectUnknown()

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val auth = BlossomAuth.createUploadAuth(hash, 0, "Mirror $hash", ctx.signer, servers = listOf(server))
            return withPayment(server) {
                val node = BlossomClient().mirror(sourceUrl, server, auth)
                Output.emit(mapOf("server" to server, "sha256" to hash, "blob" to node))
                0
            }
        }
    }

    private suspend fun upload(
        dataDir: DataDir,
        rest: Array<String>,
        media: Boolean,
    ): Int {
        val args = Args(rest)
        val verb = if (media) "media" else "upload"
        val server = args.flag("server") ?: return Output.error("bad_args", "blossom $verb requires --server URL")
        val path = args.positional(0, "file")
        val file = File(path)
        if (!file.isFile) return Output.error("bad_args", "no such file: $path")

        val bytes = file.readBytes()
        val hash = sha256(bytes).toHexKey()
        val mime = args.flag("mime-type") ?: runCatching { Files.probeContentType(file.toPath()) }.getOrNull() ?: "application/octet-stream"
        args.rejectUnknown()

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val client = BlossomClient()
            val auth =
                if (media) {
                    BlossomAuth.createMediaAuth(hash, file.length(), "Optimize ${file.name}", ctx.signer, servers = listOf(server))
                } else {
                    BlossomAuth.createUploadAuth(hash, file.length(), "Upload ${file.name}", ctx.signer, servers = listOf(server))
                }
            return withPayment(server) {
                val result = if (media) client.media(file, mime, server, auth) else client.upload(file, mime, server, auth)
                Output.emit(
                    mapOf(
                        "url" to result.url,
                        "sha256" to (result.sha256 ?: hash),
                        "ox" to result.ox,
                        "size_bytes" to (result.size ?: file.length()),
                        "type" to (result.type ?: mime),
                        "server" to server,
                    ),
                )
                0
            }
        }
    }

    private suspend fun download(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val target = args.positional(0, "url-or-hash")
        val server = args.flag("server")
        val out = args.flag("out")
        args.rejectUnknown()
        val url = if (server != null && !target.startsWith("http")) BlossomServerUrl.blob(server, target) else target

        // Public download — no auth, so it runs anonymously without an account.
        Context.openOrAnonymous(dataDir).use { _ ->
            val bytes =
                BlossomClient().download(url)
                    ?: return Output.error("not_found", "server returned no blob for $url")
            if (out != null) {
                File(out).writeBytes(bytes)
            }
            Output.emit(
                mapOf(
                    "url" to url,
                    "size_bytes" to bytes.size,
                    "sha256" to sha256(bytes).toHexKey(),
                    "saved_to" to out,
                ),
            )
            return 0
        }
    }

    private suspend fun list(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val server = args.flag("server") ?: return Output.error("bad_args", "blossom list requires --server URL")
        args.rejectUnknown()

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val pubkey = args.positionalOrNull(0)?.let { ctx.requireUserHex(it) } ?: ctx.identity.pubKeyHex
            val auth = BlossomAuth.createListAuth("List blobs", ctx.signer, servers = listOf(server))
            val blobs = BlossomClient().list(server, pubkey, auth)
            Output.emit(mapOf("server" to server, "pubkey" to pubkey, "count" to blobs.size, "blobs" to blobs))
            return 0
        }
    }

    private suspend fun delete(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val server = args.flag("server") ?: return Output.error("bad_args", "blossom delete requires --server URL")
        val hash = args.positional(0, "sha256")
        args.rejectUnknown()

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val auth = BlossomAuth.createDeleteAuth(hash, "Delete blob", ctx.signer, servers = listOf(server))
            val deleted = BlossomClient().delete(hash, server, auth)
            Output.emit(mapOf("sha256" to hash, "server" to server, "deleted" to deleted))
            return if (deleted) 0 else 1
        }
    }

    /**
     * `blossom report --server URL HASH [--type T] [--comment C] [--uploader HEX]`
     * — PUT a signed NIP-56 (kind 1984) blob report to the server's /report
     * endpoint (BUD-09). [type] is a NIP-56 report code (spam, illegal, nudity,
     * malware, …), defaulting to `other`.
     */
    private suspend fun report(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val server = args.flag("server") ?: return Output.error("bad_args", "blossom report requires --server URL")
        val hash = args.positional(0, "sha256")
        val type =
            args.flag("type")?.let { code ->
                ReportType.entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
                    ?: return Output.error("bad_args", "unknown --type '$code' (use ${ReportType.entries.joinToString("|") { it.code }})")
            } ?: ReportType.OTHER
        val comment = args.flag("comment") ?: ""
        val uploader = args.flag("uploader")
        args.rejectUnknown()

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val event = ctx.signer.sign(BlossomReport.build(hash, type, uploader, comment))
            val ok = BlossomClient().report(server, event.toJson())
            Output.emit(mapOf("server" to server, "sha256" to hash, "type" to type.code, "reported" to ok))
            return if (ok) 0 else 1
        }
    }

    /** Runs [block], turning a BUD-07 402 into a clean payment-required error. */
    private inline fun withPayment(
        server: String,
        block: () -> Int,
    ): Int =
        try {
            block()
        } catch (e: BlossomPaymentException) {
            Output.error(
                "payment_required",
                "server $server requires payment: ${e.payment.reason ?: "402"}" +
                    (e.payment.cashu?.let { " (cashu available)" } ?: "") +
                    (e.payment.lightning?.let { " (lightning invoice available)" } ?: ""),
            )
        }
}
