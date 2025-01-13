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
package com.vitorpamplona.quartz.nip01Core.core

import com.vitorpamplona.quartz.nip01Core.HexKey

typealias TagArray = Array<Array<String>>

fun TagArray.forEachTagged(
    tagName: String,
    onEach: (eventId: HexKey) -> Unit,
) = this.forEach {
    if (it.size > 1 && it[0] == tagName) {
        onEach(it[1])
    }
}

fun TagArray.anyTagged(
    tagName: String,
    onEach: (tagValue: String) -> Boolean,
) = this.any {
    if (it.size > 1 && it[0] == tagName) {
        onEach(it[1])
    } else {
        false
    }
}

fun TagArray.anyTagged(tagName: String) = this.any { it.size > 0 && it[0] == tagName }

fun TagArray.anyTagWithValueStartingWithIgnoreCase(
    tagName: String,
    valuePrefix: String,
): Boolean = this.any { it.size > 1 && it[0] == tagName && it[1].startsWith(valuePrefix, true) }

fun TagArray.hasTagWithContent(tagName: String) = this.any { it.size > 1 && it[0] == tagName }

fun <R> TagArray.mapValueTagged(
    tagName: String,
    map: (tagValue: String) -> R,
) = this.mapNotNull {
    if (it.size > 1 && it[0] == tagName) {
        map(it[1])
    } else {
        null
    }
}

fun <R> TagArray.mapTagged(
    tagName: String,
    map: (tagValue: Array<String>) -> R,
) = this.mapNotNull {
    if (it.size > 1 && it[0] == tagName) {
        map(it)
    } else {
        null
    }
}

fun TagArray.mapValues(tagName: String) =
    this.mapNotNull {
        if (it.size > 1 && it[0] == tagName) {
            it[1]
        } else {
            null
        }
    }

fun <R> TagArray.firstMapTagged(
    tagName: String,
    map: (tagValue: Array<String>) -> R,
) = this.firstNotNullOfOrNull {
    if (it.size > 1 && it[0] == tagName) {
        map(it)
    } else {
        null
    }
}

fun TagArray.filterByTag(tagName: String) = this.filter { it.size > 0 && it[0] == tagName }

fun TagArray.filterByTagWithValue(tagName: String) = this.filter { it.size > 1 && it[0] == tagName }

fun TagArray.firstTag(key: String) = this.firstOrNull { it.size > 1 && it[0] == key }

fun TagArray.firstTagValue(key: String) = this.firstOrNull { it.size > 1 && it[0] == key }?.let { it[1] }

fun TagArray.firstTagValueAsInt(key: String) = this.firstOrNull { it.size > 1 && it[0] == key }?.let { it[1].toIntOrNull() }

fun TagArray.firstTagValueAsLong(key: String) = this.firstOrNull { it.size > 1 && it[0] == key }?.let { it[1].toLongOrNull() }

fun TagArray.firstTagValueFor(vararg key: String) = this.firstOrNull { it.size > 1 && it[0] in key }?.let { it[1] }

fun TagArray.isTagged(
    key: String,
    tag: String,
) = this.any { it.size > 1 && it[0] == key && it[1] == tag }

fun TagArray.isAnyTagged(
    key: String,
    tags: Set<String>,
) = this.any { it.size > 1 && it[0] == key && it[1] in tags }

fun TagArray.matchTag1With(text: String) = this.any { it.size > 1 && it[1].contains(text, true) }
