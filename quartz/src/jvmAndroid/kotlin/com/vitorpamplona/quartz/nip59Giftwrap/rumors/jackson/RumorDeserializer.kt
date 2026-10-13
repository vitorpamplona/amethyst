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
package com.vitorpamplona.quartz.nip59Giftwrap.rumors.jackson

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.jackson.TagArrayDeserializer
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.Rumor

class RumorDeserializer : StdDeserializer<Rumor>(Rumor::class.java) {
    val tagsDeserializer = TagArrayDeserializer()

    override fun deserialize(
        p: JsonParser,
        ctxt: DeserializationContext,
    ): Rumor {
        var id: HexKey? = null
        var pubKey: HexKey? = null
        var createdAt: Long? = null
        var kind: Int? = null
        var tags: TagArray? = null
        var content: String? = null

        while (true) {
            // nextToken() is null at the end of input: a truncated object must fail, not loop.
            val token = p.nextToken() ?: throw JsonParseException(p, "Unexpected end of input inside an object")
            if (token == JsonToken.END_OBJECT) break
            val fieldName = p.currentName()
            p.nextToken()

            // One switch on the hash, then the name itself: a field that merely shares a hash with
            // one of these ("jE" and "id" do) is skipped, not taken. Jackson interns field names, so
            // the check exits on identity. A `when (fieldName)` adds a second switch (~2 ns a field).
            when (fieldName.hashCode()) {
                3355 -> if (fieldName == "id") id = p.text.intern() else p.skipChildren()
                -977424830 -> if (fieldName == "pubkey") pubKey = p.text.intern() else p.skipChildren()
                1369680106 -> if (fieldName == "created_at") createdAt = p.longValue else p.skipChildren()
                3292052 -> if (fieldName == "kind") kind = p.intValue else p.skipChildren()
                3552281 -> if (fieldName == "tags") tags = tagsDeserializer.deserialize(p, ctxt) else p.skipChildren()
                951530617 -> if (fieldName == "content") content = p.text else p.skipChildren()
                else -> p.skipChildren()
            }
        }

        return Rumor(id, pubKey, createdAt, kind, tags, content)
    }
}
