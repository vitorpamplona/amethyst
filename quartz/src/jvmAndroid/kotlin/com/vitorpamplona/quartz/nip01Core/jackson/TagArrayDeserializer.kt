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

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.vitorpamplona.quartz.nip01Core.core.TagArray

class TagArrayDeserializer : StdDeserializer<TagArray>(TagArray::class.java) {
    // Needs to be very fast.
    //
    // Only an array of arrays of scalars is read. Anything else fails here: the loops below
    // end on END_ARRAY, so a value of another shape would otherwise be read past, into the
    // rest of the document.
    override fun deserialize(
        p: JsonParser,
        ctxt: DeserializationContext,
    ): TagArray {
        if (p.currentToken != JsonToken.START_ARRAY) throw JsonParseException(p, "Tags must be an array")

        val outerList = ArrayList<Array<String>>()
        while (true) {
            val tag = p.nextToken() ?: throw JsonParseException(p, "Unexpected end of input inside tags")
            if (tag == JsonToken.END_ARRAY) break
            if (tag != JsonToken.START_ARRAY) throw JsonParseException(p, "Each tag must be an array")

            val innerList = ArrayList<String>(5)
            while (true) {
                val value = p.nextToken() ?: throw JsonParseException(p, "Unexpected end of input inside a tag")
                if (value == JsonToken.END_ARRAY) break
                if (value == JsonToken.VALUE_NULL) {
                    innerList.add("")
                } else if (value.isScalarValue) {
                    innerList.add(p.text)
                } else {
                    throw JsonParseException(p, "A tag holds only strings, numbers, booleans or null")
                }
            }

            outerList.add(innerList.toArray(arrayOfNulls<String>(innerList.size)))
        }
        return outerList.toArray(arrayOfNulls<Array<String>>(outerList.size))
    }
}
