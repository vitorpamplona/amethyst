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
package com.vitorpamplona.quartz.nip01Core.crypto

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.NostrJsonWriter
import com.vitorpamplona.quartz.utils.Hex

/**
 * This class converts event properties into the JSON and UTF-8 byte array of that json according to NIP-01.
 *
 * Every target writes the same bytes, through [NostrJsonWriter]; see it for the escaping rule.
 */
object EventHasherSerializer {
    fun makeJsonForId(
        pubKey: HexKey,
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
    ): String = NostrJsonWriter.idPreimageJson(pubKey, createdAt, kind, tags, content)

    fun fastMakeJsonForId(
        pubKey: HexKey,
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
    ): ByteArray = NostrJsonWriter.idPreimageBytes(pubKey, createdAt, kind, tags, content)

    /** Hashes the preimage straight from the writer's reused buffer, then compares it to [id] without hex-encoding it. */
    fun makeJsonForIdHashAndCheck(
        id: HexKey,
        pubKey: HexKey,
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
    ): Boolean = Hex.isEqual(id, NostrJsonWriter.idHash(pubKey, createdAt, kind, tags, content))
}
