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
package com.vitorpamplona.quartz.utils

import com.vitorpamplona.quartz.nip01Core.core.Tag
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.core.hasValue
import com.vitorpamplona.quartz.nip01Core.core.name
import com.vitorpamplona.quartz.nip01Core.core.value
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer

object TagParsingUtils {
    fun validateBasicTag(
        tag: Tag,
        expectedTagName: String,
    ): Boolean = tag.has(1) && tag.name() == expectedTagName && tag.hasValue()

    fun validateHexKeyTag(
        tag: Tag,
        expectedTagName: String,
    ): Boolean {
        if (!validateBasicTag(tag, expectedTagName)) return false
        if (tag.value().length != 64) return false
        return true
    }

    fun parseRelayHint(
        tag: Tag,
        index: Int = 2,
    ): NormalizedRelayUrl? = tag.getOrNull(index)?.let { RelayUrlNormalizer.normalizeOrNull(it) }

    fun matchesTag(
        tag: Tag,
        expectedTagName: String,
    ): Boolean = tag.has(1) && tag.name() == expectedTagName && tag.hasValue()

    fun isTaggedWith(
        tag: Tag,
        expectedTagName: String,
        expectedValue: String,
    ): Boolean = tag.has(1) && tag.name() == expectedTagName && tag.value() == expectedValue

    fun isTaggedWithAny(
        tag: Tag,
        expectedTagName: String,
        expectedValues: Set<String>,
    ): Boolean = tag.has(1) && tag.name() == expectedTagName && tag.value() in expectedValues
}
