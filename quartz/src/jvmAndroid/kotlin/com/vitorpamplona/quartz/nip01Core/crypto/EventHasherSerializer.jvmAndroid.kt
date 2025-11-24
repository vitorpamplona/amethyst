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
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.util.BufferRecycler
import com.fasterxml.jackson.core.util.ByteArrayBuilder
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import java.io.IOException

actual object EventHasherSerializer {
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

    actual fun makeJsonForId(
        pubKey: HexKey,
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
    ): String = JacksonMapper.toJson(makeJsonObjectForId(pubKey, createdAt, kind, tags, content))

    actual fun fastMakeJsonForId(
        pubKey: HexKey,
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
    ): ByteArray {
        val br: BufferRecycler = JacksonMapper.mapper.factory._getBufferRecycler()
        try {
            ByteArrayBuilder(br).use { bb ->
                val generator = JacksonMapper.mapper.createGenerator(bb, JsonEncoding.UTF8)

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
}
