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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlin.coroutines.cancellation.CancellationException

/**
 * Fetches the raw bytes of a Blossom blob from an absolute URL, or returns `null` when
 * the server is unreachable / responds with an error. Supplied by the host platform
 * (e.g. an OkHttp-backed implementation in `commons`/`amethyst`) so that `quartz` carries
 * no HTTP dependency and the resolver stays Kotlin-Multiplatform-pure.
 */
typealias BlobFetcher = suspend (url: String) -> ByteArray?

/** Outcome of resolving a single request path against a NIP-5A static-website manifest. */
sealed interface StaticSiteResolution {
    /**
     * The request path was declared in the manifest and a Blossom server returned a blob
     * whose sha256 matched the declared [hash]. [bytes] are safe to render.
     */
    data class Resolved(
        val path: String,
        val hash: HexKey,
        val contentType: String,
        val bytes: ByteArray,
        val server: String,
    ) : StaticSiteResolution {
        // ByteArray needs structural equals/hashCode.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Resolved) return false
            return path == other.path &&
                hash == other.hash &&
                contentType == other.contentType &&
                server == other.server &&
                bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int {
            var result = path.hashCode()
            result = 31 * result + hash.hashCode()
            result = 31 * result + contentType.hashCode()
            result = 31 * result + server.hashCode()
            result = 31 * result + bytes.contentHashCode()
            return result
        }
    }

    /** No `path` tag in the manifest matches the request path. */
    data object PathNotInManifest : StaticSiteResolution

    /**
     * The path exists in the manifest but no listed server returned a blob whose hash
     * matched [hash] — every candidate was unreachable, errored, or served tampered bytes.
     */
    data class Unresolvable(
        val hash: HexKey,
    ) : StaticSiteResolution
}

/**
 * Resolves request paths for a NIP-5A static-website / napplet manifest into verified
 * blob bytes, fetched over Blossom.
 *
 * The trust model is the whole point: the **manifest is the authority and the Blossom
 * server is untrusted**. The signed manifest pins each path to a sha256; this resolver
 * downloads the content-addressed blob from each listed server in order and accepts the
 * first one whose recomputed sha256 matches the pin. A server that substitutes, corrupts,
 * or truncates a blob fails [verify] and is silently skipped — it can withhold content but
 * can never forge it. This is what lets a napplet shell run third-party code from an
 * untrusted CDN behind a single signed, content-addressed manifest.
 */
object StaticSiteResolver {
    /** True iff [blob]'s sha256 equals [expectedHash] (case-insensitive hex). */
    fun verify(
        blob: ByteArray,
        expectedHash: HexKey,
    ): Boolean = sha256(blob).toHexKey().equals(expectedHash, ignoreCase = true)

    /**
     * Ordered candidate Blossom URLs for [hash] across [servers]. Blossom addresses blobs
     * by bare sha256 (`<server>/<sha256>`), so the path's extension is irrelevant here.
     */
    fun candidateUrls(
        servers: List<String>,
        hash: HexKey,
    ): List<String> = servers.map { "${it.trimEnd('/')}/$hash" }

    /**
     * Resolves [requestPath] against the manifest's [paths] and [servers], fetching with
     * [fetch] and verifying every downloaded blob's hash before returning it.
     *
     * @param paths   the manifest's `path` tags (`event.paths()`).
     * @param servers the manifest's `server` tags (`event.servers()`), tried in order.
     *                Additional fallbacks (e.g. the author's kind:10063 Blossom list) can
     *                be appended by the caller before invoking this function.
     */
    suspend fun resolve(
        requestPath: String,
        paths: List<PathTag>,
        servers: List<String>,
        fetch: BlobFetcher,
    ): StaticSiteResolution {
        val match = paths.resolvePath(requestPath) ?: return StaticSiteResolution.PathNotInManifest

        for (url in candidateUrls(servers, match.hash)) {
            val bytes =
                try {
                    fetch(url)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                } ?: continue

            if (verify(bytes, match.hash)) {
                return StaticSiteResolution.Resolved(
                    path = match.path,
                    hash = match.hash,
                    contentType = guessStaticContentType(match.path),
                    bytes = bytes,
                    server = url.substringBeforeLast('/'),
                )
            }
        }

        return StaticSiteResolution.Unresolvable(match.hash)
    }
}
