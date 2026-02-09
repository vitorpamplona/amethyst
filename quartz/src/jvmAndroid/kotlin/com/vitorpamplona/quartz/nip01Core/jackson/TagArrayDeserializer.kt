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

class TagArrayDeserializer : StdDeserializer<TagArray>(TagArray::class.java) {
    // Needs to be very fast.
    override fun deserialize(
        p: JsonParser,
        ctxt: DeserializationContext,
    ): TagArray {
        // doesn't check for weird payloads on purpose.
        val outerList = ArrayList<Array<String>>()
        while (p.nextToken() != JsonToken.END_ARRAY) {
            val innerList = ArrayList<String>(5)
            while (p.nextToken() != JsonToken.END_ARRAY) {
                if (p.currentToken == JsonToken.VALUE_NULL) {
                    innerList.add("")
                } else {
                    innerList.add(p.text.intern())
                }
            }

            outerList.add(innerList.toArray(arrayOfNulls<String>(innerList.size)))
        }
        return outerList.toArray(arrayOfNulls<Array<String>>(outerList.size))
    }
}
