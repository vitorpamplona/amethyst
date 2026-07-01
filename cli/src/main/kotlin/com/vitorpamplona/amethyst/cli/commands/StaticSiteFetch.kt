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

import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.commons.service.upload.BlossomClient
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.StaticSiteResolution
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.StaticSiteResolver
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import java.io.File

/**
 * Shared Blossom resolve-and-emit used by both `amy nsite` (NIP-5A) and
 * `amy napplet` (NIP-5D): given a manifest's `path` + `server` tags, download the
 * requested path's blob, verify its sha256 against the pin, and print the result.
 * All resolution + verification lives in quartz (`StaticSiteResolver`); this is
 * thin glue around it.
 */
internal object StaticSiteFetch {
    fun commaList(value: String?): List<String> =
        value
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    /**
     * Resolves [requestPath] against the manifest's [paths]/[servers], emitting the
     * verified bytes (inlined for small text, or written to [outFile]) merged with
     * the caller's [manifestFields] (kind, ids, requires, aggregate, …), or a
     * structured `no_servers` / `path_not_found` / `unresolvable` error.
     */
    suspend fun resolveAndEmit(
        requestPath: String,
        paths: List<PathTag>,
        servers: List<String>,
        manifestFields: Map<String, Any?>,
        outFile: String?,
        maxInlineBytes: Long,
    ): Int {
        if (servers.isEmpty()) {
            return Output.error("no_servers", "manifest lists no Blossom servers; pass --server URL")
        }

        val blossom = BlossomClient()
        val resolution =
            StaticSiteResolver.resolve(
                requestPath = requestPath,
                paths = paths,
                servers = servers,
                fetch = { url -> blossom.download(url) },
            )

        return when (resolution) {
            is StaticSiteResolution.PathNotInManifest ->
                Output.error(
                    "path_not_found",
                    "manifest declares no such path",
                    mapOf("path" to requestPath, "available_paths" to paths.map { it.path }),
                )

            is StaticSiteResolution.Unresolvable ->
                Output.error(
                    "unresolvable",
                    "no server returned a blob matching the manifest hash",
                    mapOf("path" to requestPath, "sha256" to resolution.hash, "servers" to servers),
                )

            is StaticSiteResolution.Resolved -> {
                emitResolved(resolution, requestPath, manifestFields, outFile, maxInlineBytes)
                0
            }
        }
    }

    private fun emitResolved(
        resolved: StaticSiteResolution.Resolved,
        requestPath: String,
        manifestFields: Map<String, Any?>,
        outFile: String?,
        maxInlineBytes: Long,
    ) {
        val base =
            linkedMapOf<String, Any?>(
                "found" to true,
                "verified" to true,
                "request_path" to requestPath,
                "manifest_path" to resolved.path,
                "sha256" to resolved.hash,
                "content_type" to resolved.contentType,
                "size" to resolved.bytes.size,
                "server" to resolved.server,
            )
        base.putAll(manifestFields)

        if (outFile != null) {
            File(outFile).writeBytes(resolved.bytes)
            base["out"] = outFile
        } else if (isTextual(resolved.contentType) && resolved.bytes.size <= maxInlineBytes) {
            base["content"] = resolved.bytes.decodeToString()
        } else {
            base["note"] = "binary or large blob not inlined; pass --out FILE to save it"
        }

        Output.emit(base)
    }

    private fun isTextual(contentType: String): Boolean =
        contentType.startsWith("text/") ||
            contentType.startsWith("application/json") ||
            contentType.startsWith("application/xml") ||
            contentType.startsWith("image/svg")
}
