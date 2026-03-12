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
package com.vitorpamplona.quartz.nip05DnsIdentifiers

import com.vitorpamplona.quartz.nip01Core.core.HexKey

interface INip05Client {
    suspend fun verify(
        nip05: Nip05Id,
        hexKey: HexKey,
    ): Boolean

    suspend fun get(nip05: Nip05Id): Nip05KeyInfo?

    suspend fun load(nip05: Nip05Id): KeyInfoSet?

    suspend fun list(domain: String): KeyInfoSet
}

class EmptyNip05Client : INip05Client {
    override suspend fun verify(
        nip05: Nip05Id,
        hexKey: HexKey,
    ) = true

    override suspend fun get(nip05: Nip05Id): Nip05KeyInfo? = null

    override suspend fun load(nip05: Nip05Id): KeyInfoSet? = null

    override suspend fun list(domain: String): KeyInfoSet = KeyInfoSet(emptyMap(), emptyMap())
}
