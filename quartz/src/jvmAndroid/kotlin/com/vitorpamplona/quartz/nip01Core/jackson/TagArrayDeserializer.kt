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
package com.vitorpamplona.quartz.nip01Core.jackson

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.limits.EventLimits

class TagArrayDeserializer : StdDeserializer<TagArray>(TagArray::class.java) {
    // Needs to be very fast.
    override fun deserialize(
        p: JsonParser,
        ctxt: DeserializationContext,
    ): TagArray {
        val outerList = ArrayList<Array<String>>()
        while (p.nextToken() != JsonToken.END_ARRAY) {
            require(outerList.size < EventLimits.MAX_TAG_COUNT) {
                "Event has more than ${EventLimits.MAX_TAG_COUNT} tags"
            }
            val innerList = ArrayList<String>(5)
            var index = 0
            while (p.nextToken() != JsonToken.END_ARRAY) {
                require(innerList.size < EventLimits.MAX_TAG_ELEMENTS_PER_TAG) {
                    "Tag has more than ${EventLimits.MAX_TAG_ELEMENTS_PER_TAG} elements"
                }
                if (p.currentToken == JsonToken.VALUE_NULL) {
                    innerList.add("")
                } else {
                    val text = p.text
                    require(text.length <= EventLimits.MAX_TAG_VALUE_LENGTH) {
                        "Tag value length ${text.length} exceeds max ${EventLimits.MAX_TAG_VALUE_LENGTH}"
                    }
                    // Intern only the tag key (index 0) — protocol-defined finite set ("p", "e", "a", …).
                    // Tag values at index >= 1 are attacker-controlled (pubkeys, event ids, URLs, free text)
                    // and must not be promoted to the JVM StringTable: hostile relays could flood unique
                    // values to grow it without bound (security review 2026-04-24 §2.3).
                    innerList.add(if (index == 0) text.intern() else text)
                }
                index++
            }

            outerList.add(innerList.toArray(arrayOfNulls<String>(innerList.size)))
        }
        return outerList.toArray(arrayOfNulls<Array<String>>(outerList.size))
    }
}
