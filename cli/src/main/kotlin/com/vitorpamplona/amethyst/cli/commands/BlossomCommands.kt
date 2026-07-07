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
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nipB7Blossom.BlossomAuthorizationEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServerUrl
import com.vitorpamplona.quartz.utils.sha256.sha256
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.nio.file.Files

/**
 * `amy blossom <upload|download|list|delete>` — Blossom blob storage
 * (nak's `blossom`). Uploads/lists/deletes are authed with the active
 * account (BUD-01/02/04 kind:24242 events); downloads are public.
 *
 *   upload   --server URL FILE [--mime-type M]
 *   download URL [--out FILE]              (or: download HASH --server URL)
 *   list     --server URL [USER]           (USER defaults to the active account)
 *   delete   HASH --server URL
 *
 * Thin assembly only: HTTP + auth live in commons `BlossomClient` /
 * `BlossomAuth` and quartz `BlossomAuthorizationEvent`; this file wires
 * flags and shapes output. List/delete use OkHttp directly (no client
 * method exists) with the quartz-built auth header.
 */
object BlossomCommands {
    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "blossom",
            tail,
            "blossom <upload|download|list|delete|check|mirror>",
            mapOf(
                "upload" to { rest -> upload(dataDir, rest) },
                "download" to { rest -> download(dataDir, rest) },
                "list" to { rest -> list(dataDir, rest) },
                "delete" to { rest -> delete(dataDir, rest) },
                "check" to { rest -> check(dataDir, rest) },
                "mirror" to { rest -> mirror(dataDir, rest) },
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

        // Read-only HEAD probe — no auth, so it runs anonymously without an account.
        Context.openOrAnonymous(dataDir).use { _ ->
            val http = OkHttpClient()
            val results =
                hashes.map { hash ->
                    val req =
                        Request
                            .Builder()
                            .url(BlossomServerUrl.blob(server, hash))
                            .head()
                            .build()
                    val (found, status) =
                        try {
                            http.newCall(req).execute().use { it.isSuccessful to it.code }
                        } catch (e: Exception) {
                            false to -1
                        }
                    mapOf("sha256" to hash, "found" to found, "status" to status)
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
        val hash = sourceUrl.substringAfterLast('/').substringBefore('.')
        if (hash.length != 64 || hash.any { it !in "0123456789abcdef" }) {
            return Output.error("bad_args", "could not extract a sha256 from the source url '$sourceUrl'")
        }

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val auth = BlossomAuthorizationEvent.createUploadAuth(hash, 0, "Mirror $hash", ctx.signer).toAuthorizationHeader()
            val body = """{"url":${Output.mapper.writeValueAsString(sourceUrl)}}""".toRequestBody("application/json".toMediaType())
            val req =
                Request
                    .Builder()
                    .url(server.removeSuffix("/") + "/mirror")
                    .header("Authorization", auth)
                    .put(body)
                    .build()
            OkHttpClient().newCall(req).execute().use { response ->
                if (!response.isSuccessful) {
                    return Output.error("http_error", "mirror failed: HTTP ${response.code} ${response.headers[BlossomServerUrl.REASON_HEADER] ?: ""}")
                }
                val node = Output.mapper.readTree(response.body.string())
                Output.emit(mapOf("server" to server, "sha256" to hash, "blob" to node))
            }
            return 0
        }
    }

    private suspend fun upload(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val server = args.flag("server") ?: return Output.error("bad_args", "blossom upload requires --server URL")
        val path = args.positional(0, "file")
        val file = File(path)
        if (!file.isFile) return Output.error("bad_args", "no such file: $path")

        val bytes = file.readBytes()
        val hash = sha256(bytes).toHexKey()
        val mime = args.flag("mime-type") ?: runCatching { Files.probeContentType(file.toPath()) }.getOrNull() ?: "application/octet-stream"

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val auth = BlossomAuth.createUploadAuth(hash, file.length(), "Upload ${file.name}", ctx.signer)
            val result = BlossomClient().upload(file, mime, server, auth)
            Output.emit(
                mapOf(
                    "url" to result.url,
                    "sha256" to (result.sha256 ?: hash),
                    "size" to (result.size ?: file.length()),
                    "type" to (result.type ?: mime),
                    "server" to server,
                ),
            )
            return 0
        }
    }

    private suspend fun download(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val target = args.positional(0, "url-or-hash")
        val server = args.flag("server")
        val url = if (server != null && !target.startsWith("http")) BlossomServerUrl.blob(server, target) else target

        // Public download — no auth, so it runs anonymously without an account.
        Context.openOrAnonymous(dataDir).use { ctx ->
            val bytes =
                BlossomClient().download(url)
                    ?: return Output.error("not_found", "server returned no blob for $url")
            val out = args.flag("out")
            if (out != null) {
                File(out).writeBytes(bytes)
            }
            Output.emit(
                mapOf(
                    "url" to url,
                    "size" to bytes.size,
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

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val pubkey = args.positionalOrNull(0)?.let { ctx.requireUserHex(it) } ?: ctx.identity.pubKeyHex
            val auth = BlossomAuthorizationEvent.createListAuth(ctx.signer, "List blobs").toAuthorizationHeader()
            val listUrl = server.removeSuffix("/") + "/list/" + pubkey

            val request =
                Request
                    .Builder()
                    .url(listUrl)
                    .header("Authorization", auth)
                    .get()
                    .build()
            OkHttpClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful) return Output.error("http_error", "server returned HTTP ${response.code} for $listUrl")
                val node = Output.mapper.readTree(response.body.string())
                Output.emit(mapOf("server" to server, "pubkey" to pubkey, "count" to node.size(), "blobs" to node))
            }
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

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val auth = BlossomAuthorizationEvent.createDeleteAuth(hash, "Delete blob", ctx.signer).toAuthorizationHeader()
            val blobUrl = BlossomServerUrl.blob(server, hash)
            val request =
                Request
                    .Builder()
                    .url(blobUrl)
                    .header("Authorization", auth)
                    .delete()
                    .build()
            OkHttpClient().newCall(request).execute().use { response ->
                Output.emit(
                    mapOf(
                        "sha256" to hash,
                        "server" to server,
                        "deleted" to response.isSuccessful,
                        "status" to response.code,
                    ),
                )
                return if (response.isSuccessful) 0 else 1
            }
        }
    }
}
