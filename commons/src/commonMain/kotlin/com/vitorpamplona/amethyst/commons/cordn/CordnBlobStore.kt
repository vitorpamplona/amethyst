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
package com.vitorpamplona.amethyst.commons.cordn

/**
 * A content-addressed store for sealed migration documents (§12).
 *
 * Injected rather than depended on directly because the only Blossom uploader
 * in this codebase needs an Android `Context`, and the migration service has to
 * stay runnable from `amy` and from a test with no Android at all. The same
 * reason `CordnCoordinatorLinkFactory` is injected.
 *
 * ## The one requirement that is not about bytes
 *
 * §12: a Blossom upload's BUD-01 authorization event **MUST be signed by an
 * ephemeral key, never the owner `npub`** — consistent with the tip. Signing as
 * the owner would publish, in the clear on the storage server, that this
 * account is uploading blobs right now, which is exactly the linkage the
 * opaque tip exists to prevent. An implementation that authorizes as the owner
 * satisfies this interface's types and defeats its purpose.
 */
interface CordnBlobStore {
    /**
     * Uploads [blob] and returns the servers that accepted it.
     *
     * The address is `sha256(blob)` and the caller already knows it, so there
     * is nothing to return but reachability: a blob nobody hosts is a document
     * the other phone cannot fetch, and the tip's `server` list is built from
     * this.
     */
    suspend fun put(blob: ByteArray): List<String>

    /**
     * Fetches the blob at [address], trying [servers] in order.
     *
     * Returns null when no server served it. Verifying that the bytes hash to
     * [address] is the **caller's** job (`CordnDocumentSeal.verifyAddress`) —
     * an implementation is a transport and is not trusted to self-certify.
     */
    suspend fun get(
        address: String,
        servers: List<String>,
    ): ByteArray?
}
