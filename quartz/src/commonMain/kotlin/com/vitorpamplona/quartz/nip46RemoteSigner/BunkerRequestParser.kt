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
package com.vitorpamplona.quartz.nip46RemoteSigner

import kotlinx.coroutines.CancellationException

/**
 * Turns a decoded NIP-46 request envelope into its typed [BunkerRequest]. Shared by
 * every JSON backend (kotlinx and Jackson) so they agree on the method table and on
 * how bad params are handled.
 *
 * A known method with bad params never throws: it yields a [BunkerRequestInvalid]
 * so the remote signer can reply with an error the client can correlate. Unknown
 * methods come back as a plain [BunkerRequest], which the signer also answers with
 * an error.
 */
object BunkerRequestParser {
    fun parse(
        id: String,
        method: String,
        params: Array<String>,
    ): BunkerRequest =
        try {
            when (method) {
                BunkerRequestConnect.METHOD_NAME -> BunkerRequestConnect.parse(id, params)
                BunkerRequestGetPublicKey.METHOD_NAME -> BunkerRequestGetPublicKey.parse(id, params)
                BunkerRequestGetRelays.METHOD_NAME -> BunkerRequestGetRelays.parse(id, params)
                BunkerRequestNip04Decrypt.METHOD_NAME -> BunkerRequestNip04Decrypt.parse(id, params)
                BunkerRequestNip04Encrypt.METHOD_NAME -> BunkerRequestNip04Encrypt.parse(id, params)
                BunkerRequestNip44Decrypt.METHOD_NAME -> BunkerRequestNip44Decrypt.parse(id, params)
                BunkerRequestNip44Encrypt.METHOD_NAME -> BunkerRequestNip44Encrypt.parse(id, params)
                BunkerRequestPing.METHOD_NAME -> BunkerRequestPing.parse(id, params)
                BunkerRequestSign.METHOD_NAME -> BunkerRequestSign.parse(id, params)
                else -> BunkerRequest(id, method, params)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BunkerRequestInvalid(id, method, params, e.message ?: e::class.simpleName ?: "malformed params")
        }
}
