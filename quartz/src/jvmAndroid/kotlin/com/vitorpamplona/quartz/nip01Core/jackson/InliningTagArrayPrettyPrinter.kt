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

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.core.util.Separators

class InliningTagArrayPrettyPrinter : DefaultPrettyPrinter {
    companion object {
        val MY_SEPARATORS =
            DEFAULT_SEPARATORS
                .withObjectFieldValueSpacing(Separators.Spacing.AFTER)
    }

    init {
        indentArraysWith(DefaultIndenter("  ", "\n"))
    }

    constructor(separators: Separators? = MY_SEPARATORS) : super(separators)

    constructor(base: InliningTagArrayPrettyPrinter) : super(base)

    override fun createInstance(): DefaultPrettyPrinter = InliningTagArrayPrettyPrinter(this)

    override fun writeStartArray(g: JsonGenerator) {
        if (!_arrayIndenter.isInline) {
            ++_nesting
        }
        g.writeRaw('[')
    }

    override fun beforeArrayValues(g: JsonGenerator) {
        if (_nesting < 3) {
            _arrayIndenter.writeIndentation(g, _nesting)
        }
    }

    override fun writeArrayValueSeparator(g: JsonGenerator) {
        g.writeRaw(_arrayValueSeparator)
        if (_nesting < 3) {
            _arrayIndenter.writeIndentation(g, _nesting)
        } else {
            g.writeRaw(' ')
        }
    }

    override fun writeEndArray(
        g: JsonGenerator,
        nrOfValues: Int,
    ) {
        if (!_arrayIndenter.isInline) {
            --_nesting
        }
        if (nrOfValues > 0) {
            if (_nesting < 2) {
                _arrayIndenter.writeIndentation(g, _nesting)
            }
        }
        g.writeRaw(']')
    }
}
