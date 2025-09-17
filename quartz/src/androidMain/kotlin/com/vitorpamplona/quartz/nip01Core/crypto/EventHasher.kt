/**
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

import com.fasterxml.jackson.core.JsonEncoding
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.util.BufferRecycler
import com.fasterxml.jackson.core.util.ByteArrayBuilder
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.jackson.JsonMapper
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.sha256.sha256
import java.io.IOException

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
        ): String = JsonMapper.toJson(makeJsonObjectForId(pubKey, createdAt, kind, tags, content))

        fun fastMakeJsonForId(
            pubKey: HexKey,
            createdAt: Long,
            kind: Int,
            tags: Array<Array<String>>,
            content: String,
        ): ByteArray {
            val br: BufferRecycler = JsonMapper.mapper.factory._getBufferRecycler()
            try {
                ByteArrayBuilder(br).use { bb ->
                    val generator = JsonMapper.mapper.createGenerator(bb, JsonEncoding.UTF8)
                    generator.enable(JsonGenerator.Feature.COMBINE_UNICODE_SURROGATES_IN_UTF8)
                    generator.use {
                        it.writeStartArray()
                        it.writeNumber(0)
                        it.writeString(pubKey)
                        it.writeNumber(createdAt)
                        it.writeNumber(kind)
                        it.writeStartArray()
                        tags.forEach { tag ->
                            it.writeStartArray()
                            tag.forEach { value ->
                                it.writeString(value)
                            }
                            it.writeEndArray()
                        }
                        it.writeEndArray()
                        it.writeString(content)
                        it.writeEndArray()
                    }

                    val result = bb.toByteArray()
                    bb.release()
                    return result
                }
            } catch (e: JsonProcessingException) {
                throw e
            } catch (e: IOException) {
                // shouldn't really happen, but is declared as possibility so:
                throw JsonMappingException.fromUnexpectedIOE(e)
            } finally {
                br.releaseToPool()
            }
        }

        fun hashIdBytes(
            pubKey: HexKey,
            createdAt: Long,
            kind: Int,
            tags: Array<Array<String>>,
            content: String,
        ): ByteArray = sha256(fastMakeJsonForId(pubKey, createdAt, kind, tags, content))

        fun hashId(serializedJsonAsBytes: ByteArray): String = sha256(serializedJsonAsBytes).toHexKey()

        fun hashId(
            pubKey: HexKey,
            createdAt: Long,
            kind: Int,
            tags: Array<Array<String>>,
            content: String,
        ): String = hashIdBytes(pubKey, createdAt, kind, tags, content).toHexKey()

        fun hashIdEquals(
            id: HexKey,
            pubKey: HexKey,
            createdAt: Long,
            kind: Int,
            tags: Array<Array<String>>,
            content: String,
        ): Boolean {
            val outId = hashIdBytes(pubKey, createdAt, kind, tags, content)
            return Hex.isEqual(id, outId)
        }
    }
}
