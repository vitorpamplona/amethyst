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
package com.vitorpamplona.quartz.nip01Core.jackson

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.utils.EventFactory

class EventDeserializer : StdDeserializer<Event>(Event::class.java) {
    val tagsDeserializer = TagArrayDeserializer()

    val emptyArray = emptyArray<Array<String>>()

    override fun deserialize(
        p: JsonParser,
        ctxt: DeserializationContext,
    ): Event {
        var id: HexKey = ""
        var pubKey: HexKey = ""
        var createdAt: Long = 0
        var kind: Kind = 0
        var tags: TagArray = emptyArray
        var content: String = ""
        var sig: HexKey = ""

        while (p.nextToken() != JsonToken.END_OBJECT) {
            val fieldName = p.currentName()
            p.nextToken()

            when (fieldName.hashCode()) {
                3355 -> id = p.text.intern()
                -977424830 -> pubKey = p.text.intern()
                1369680106 -> createdAt = p.longValue
                3292052 -> kind = p.intValue
                3552281 -> tags = tagsDeserializer.deserialize(p, ctxt)
                951530617 -> content = p.text
                113873 -> sig = p.text
                else -> p.skipChildren()
            }
        }

        // NPE on purpose. If the object isn't fully filled, it should throw.
        return EventFactory.create(id, pubKey, createdAt, kind, tags, content, sig)
    }
}
