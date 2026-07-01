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
package com.vitorpamplona.quartz.nip5aStaticWebsites.resolver

import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag

/*
 * Request-path lookup for NIP-5A static-website / napplet manifests.
 *
 * A manifest (`RootSiteEvent` kind 15128 / `NamedSiteEvent` kind 35128) maps request
 * paths to content-addressed blobs through `path` tags (`PathTag` = request path + the
 * blob's lowercase sha256). The same event shape backs both nsites and napplets
 * (NIP-5D web projection), so the lookup here is deliberately runtime-agnostic.
 *
 * These helpers apply the usual static-host path conventions before matching:
 *  - the query string (`?…`) and fragment (`#…`) are dropped,
 *  - a leading `/` (or `./`) is irrelevant to the match,
 *  - an empty path or a directory request (trailing `/`) resolves to `index.html`.
 *
 * The match itself stays strict and content-addressed — there is no SPA "serve
 * index.html for any unknown route" fallback here. That is a host/shell policy
 * decision (it weakens the path→hash guarantee) and belongs in the shell, not in
 * the protocol layer.
 */

/** The implicit document served for the site root and for directory requests. */
const val STATIC_SITE_INDEX = "index.html"

/**
 * Normalises a raw request path to the canonical form used for manifest matching:
 * strips the query/fragment and any leading `/` or `./`, and expands a root or
 * directory request to its `index.html` document.
 */
fun normalizeStaticPath(requestPath: String): String {
    val withoutQuery = requestPath.substringBefore('?').substringBefore('#')
    val trimmed = withoutQuery.removePrefix("./").removePrefix("/")
    return when {
        trimmed.isEmpty() -> STATIC_SITE_INDEX
        trimmed.endsWith('/') -> trimmed + STATIC_SITE_INDEX
        else -> trimmed
    }
}

/** Canonical form of a manifest-declared path, so `/app.js` and `app.js` compare equal. */
private fun PathTag.canonicalPath() = path.removePrefix("./").removePrefix("/")

/**
 * Finds the [PathTag] that serves [requestPath], applying [normalizeStaticPath] to the
 * request and to each declared path before comparing. Returns `null` when the path is
 * not declared in the manifest.
 */
fun List<PathTag>.resolvePath(requestPath: String): PathTag? {
    val target = normalizeStaticPath(requestPath)
    return firstOrNull { it.canonicalPath() == target }
}

/**
 * Best-effort `Content-Type` for a manifest path, derived from its file extension.
 * Blossom serves blobs untyped (content-addressed), so the host must label them; this
 * covers the common web-runtime asset types and falls back to `application/octet-stream`.
 */
fun guessStaticContentType(path: String): String {
    val ext = path.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "html", "htm" -> "text/html; charset=utf-8"
        "js", "mjs" -> "text/javascript; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "json" -> "application/json; charset=utf-8"
        "wasm" -> "application/wasm"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "avif" -> "image/avif"
        "ico" -> "image/x-icon"
        "txt", "md" -> "text/plain; charset=utf-8"
        "xml" -> "application/xml"
        "woff2" -> "font/woff2"
        "woff" -> "font/woff"
        "ttf" -> "font/ttf"
        "map" -> "application/json"
        else -> "application/octet-stream"
    }
}

/** The fallback type [guessStaticContentType] returns when the extension is unknown. */
const val GENERIC_CONTENT_TYPE = "application/octet-stream"

/**
 * Best-effort content sniff from the leading magic bytes, used **only** when the path extension
 * yields no type ([GENERIC_CONTENT_TYPE]). Deliberately conservative: it recognises well-known
 * binary signatures and never returns a text/markup type, so HTML detection (and the shell's shim
 * injection) keeps keying off the extension. Returns `null` when nothing matches.
 */
fun sniffContentType(bytes: ByteArray): String? {
    fun match(vararg sig: Int): Boolean {
        if (bytes.size < sig.size) return false
        for (i in sig.indices) if ((bytes[i].toInt() and 0xFF) != sig[i]) return false
        return true
    }

    fun at(
        offset: Int,
        vararg sig: Int,
    ): Boolean {
        if (bytes.size < offset + sig.size) return false
        for (i in sig.indices) if ((bytes[offset + i].toInt() and 0xFF) != sig[i]) return false
        return true
    }

    return when {
        match(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> "image/png"
        match(0xFF, 0xD8, 0xFF) -> "image/jpeg"
        match(0x47, 0x49, 0x46, 0x38) -> "image/gif" // GIF8
        match(0x52, 0x49, 0x46, 0x46) && at(8, 0x57, 0x45, 0x42, 0x50) -> "image/webp" // RIFF….WEBP
        match(0x25, 0x50, 0x44, 0x46) -> "application/pdf" // %PDF
        match(0x00, 0x61, 0x73, 0x6D) -> "application/wasm" // \0asm
        match(0x1F, 0x8B) -> "application/gzip"
        match(0x42, 0x4D) -> "image/bmp"
        match(0x4F, 0x67, 0x67, 0x53) -> "audio/ogg" // OggS
        at(4, 0x66, 0x74, 0x79, 0x70) -> "video/mp4" // ….ftyp
        else -> null
    }
}
