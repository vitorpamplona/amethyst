/**
 * Copyright (c) 2024 Vitor Pamplona
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

import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.jackson.EventMapper
import com.vitorpamplona.quartz.utils.sha256.sha256

class EventHasher {
    companion object {
        fun makeJsonObjectForId(
            pubKey: HexKey,
            createdAt: Long,
            kind: Int,
            tags: Array<Array<String>>,
            content: String,
        ): ArrayNode {
            val factory = JsonNodeFactory.instance
            return factory.arrayNode(6).apply {
                add(0)
                add(pubKey)
                add(createdAt)
                add(kind)
                add(
                    factory.arrayNode(tags.size).apply {
                        tags.forEach { tag ->
                            add(
                                factory.arrayNode(tag.size).apply { tag.forEach { add(it) } },
                            )
                        }
                    },
                )
                add(content)
            }
        }

        fun makeJsonForId(
            pubKey: HexKey,
            createdAt: Long,
            kind: Int,
            tags: Array<Array<String>>,
            content: String,
        ): String = EventMapper.toJson(makeJsonObjectForId(pubKey, createdAt, kind, tags, content))

        fun hashIdBytes(
            pubKey: HexKey,
            createdAt: Long,
            kind: Int,
            tags: Array<Array<String>>,
            content: String,
        ): ByteArray = sha256(makeJsonForId(pubKey, createdAt, kind, tags, content).toByteArray())

        fun hashId(serializedJsonAsBytes: ByteArray): String = sha256(serializedJsonAsBytes).toHexKey()

        fun hashId(
            pubKey: HexKey,
            createdAt: Long,
            kind: Int,
            tags: Array<Array<String>>,
            content: String,
        ): String = hashIdBytes(pubKey, createdAt, kind, tags, content).toHexKey()
    }
}
