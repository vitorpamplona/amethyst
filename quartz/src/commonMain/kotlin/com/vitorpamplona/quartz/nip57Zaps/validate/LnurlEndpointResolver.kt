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
package com.vitorpamplona.quartz.nip57Zaps.validate

/**
 * Resolves an LNURL-pay URL to its [LnurlEndpointInfo]. Implementations should
 * consult [LnurlEndpointCache] first and only fall through to a network fetch
 * on a miss. Lives in quartz so that pure-logic verifiers (e.g.
 * [LnZapReceiptValidator]) can be wired against an abstract interface without
 * depending on OkHttp.
 */
fun interface LnurlEndpointResolver {
    /**
     * @param lnurlpUrl the full `/.well-known/lnurlp/<user>` URL. Caller must
     * resolve lud16 or bech32 LNURL to this form before calling.
     * @return the parsed endpoint info, or null if it could not be fetched.
     */
    suspend fun resolve(lnurlpUrl: String): LnurlEndpointInfo?
}
