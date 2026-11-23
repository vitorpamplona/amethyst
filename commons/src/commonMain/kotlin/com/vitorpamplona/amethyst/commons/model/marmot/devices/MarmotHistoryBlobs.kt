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
package com.vitorpamplona.amethyst.commons.model.marmot.devices

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder

/**
 * Where history bundles too long to travel inline go (`account-sync.md` "History deliveries",
 * Blossom): the sender uploads each bundle's ciphertext with BUD-02 to Blossom servers of its own
 * choosing, the account's (kind 10063), and the receiver fetches it back by its hash with BUD-01.
 * The bytes are ChaCha20-Poly1305 ciphertext whose key travels sealed to the receiving device, so a
 * server sees an opaque blob, its size and who uploaded it.
 */
interface MarmotHistoryBlobs {
    /**
     * Uploads [ciphertext] to up to four of the account's Blossom servers and returns the base URLs
     * of the ones that hold it now, or null when none took it (the history then stays owed).
     */
    suspend fun upload(ciphertext: ByteArray): List<String>?

    /** The blob at [url], read up to [maxBytes]; null when it cannot be fetched now. */
    suspend fun fetch(
        url: String,
        maxBytes: Long,
    ): ByteArray?
}

/** The platform's [MarmotHistoryBlobs] over the account's HTTP routes, or null where there is no Blossom client. */
expect fun marmotHistoryBlobsOf(
    account: Account,
    httpClients: IRoleBasedHttpClientBuilder,
): MarmotHistoryBlobs?
