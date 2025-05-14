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
package com.vitorpamplona.quartz.nip01Core.core

typealias TagArray = Array<Array<String>>

fun <T : Event> TagArray.builder(initializer: TagArrayBuilder<T>.() -> Unit = {}) = TagArrayBuilder<T>().addAll(this).apply(initializer).build()

/**
 * Performs the given [action] on each tag that matches the given [tagName].
 */
fun TagArray.forEachTagged(
    tagName: String,
    action: (eventId: HexKey) -> Unit,
) = this.forEach {
    if (it.size > 1 && it[0] == tagName) {
        action(it[1])
    }
}

/**
 * Returns `true` if at least one tag matches the given [tagName] and the action.
 */
fun TagArray.anyTagged(
    tagName: String,
    predicate: (tagValue: String) -> Boolean,
) = this.any { it.size > 1 && it[0] == tagName && predicate(it[1]) }

/**
 * Returns `true` if at least one tag matches the given [tagName].
 */
fun TagArray.anyTagged(tagName: String) = this.any { it.size > 0 && it[0] == tagName }

/**
 * Returns `true` if at least one tag matches the given [tagName] and its value starts with a prefix
 */
fun TagArray.anyTagWithValueStartingWith(
    tagName: String,
    valuePrefix: String,
    ignoreCase: Boolean = true,
): Boolean = this.any { it.size > 1 && it[0] == tagName && it[1].startsWith(valuePrefix, ignoreCase) }

/**
 * Returns `true` if at least one tag matches the given name and has a value
 */
fun TagArray.hasTagWithContent(tagName: String) = this.any { it.size > 1 && it[0] == tagName }

/**
 * Returns a list containing only the non-null results of applying the tag value to the given [transform] function
 * to each tag that matches the [tagName]
 */
fun <R> TagArray.mapValueTagged(
    tagName: String,
    transform: (tagValue: String) -> R,
) = this.mapNotNull {
    if (it.size > 1 && it[0] == tagName) {
        transform(it[1])
    } else {
        null
    }
}

/**
 * Returns a list containing only the non-null results of applying the tag array to the given [transform] function
 * to each tag that matches the [tagName]
 */
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

/**
 * Returns a list containing only the non-null tag values that match the [tagName]
 */
fun TagArray.mapValues(tagName: String) =
    this.mapNotNull {
        if (it.size > 1 && it[0] == tagName) {
            it[1]
        } else {
            null
        }
    }

/**
 * Returns the first non-null value produced by [transform] function being applied to all tags
 * that match the [tagName]
 */
fun <R> TagArray.firstMappedTag(
    tagName: String,
    transform: (tagValue: Array<String>) -> R,
) = this.firstNotNullOfOrNull {
    if (it.size > 1 && it[0] == tagName) {
        transform(it)
    } else {
        null
    }
}

/**
 * Returns a list of tags that match the given [tagName].
 */
fun TagArray.filterByTag(tagName: String) = this.filter { it.size > 0 && it[0] == tagName }

/**
 * Returns a list of tags that match the given [tagName] and have a tag value.
 */
fun TagArray.filterByTagWithValue(tagName: String) = this.filter { it.size > 1 && it[0] == tagName }

/**
 * Returns the first tag that match the given [tagName] and have a tag value.
 */
fun TagArray.firstTag(key: String) = this.firstOrNull { it.size > 1 && it[0] == key }

/**
 * Returns the first tag value that match the given [tagName] and have a tag value.
 */
fun TagArray.firstTagValue(key: String) = this.firstOrNull { it.size > 1 && it[0] == key }?.let { it[1] }

/**
 * Returns the first tag value that match the given [tagName] and have a tag value as integer
 */
fun TagArray.firstTagValueAsInt(key: String) = this.firstOrNull { it.size > 1 && it[0] == key }?.let { it[1].toIntOrNull() }

/**
 * Returns the first tag value that match the given [tagName] and have a tag value as long
 */
fun TagArray.firstTagValueAsLong(key: String) = this.firstOrNull { it.size > 1 && it[0] == key }?.let { it[1].toLongOrNull() }

/**
 * Returns the first tag value that match any of the given [tagNames] and have a tag value
 */
fun TagArray.firstTagValueFor(vararg tagNames: String) = this.firstOrNull { it.size > 1 && it[0] in tagNames }?.let { it[1] }

/**
 * Returns `true` if at least one tag matches the given [tagName] and [tagValue]
 */
fun TagArray.isTagged(
    tagName: String,
    tagValue: String,
    ignoreCase: Boolean = false,
) = this.any { it.size > 1 && it[0] == tagName && it[1].equals(tagValue, ignoreCase) }

/**
 * Returns `true` if at least one tag matches the given [tagName] and is in [tagValues]
 */
fun TagArray.isAnyTagged(
    tagName: String,
    tagValues: Set<String>,
) = this.any { it.size > 1 && it[0] == tagName && it[1] in tagValues }

fun <U> TagArray.any(
    predicate: (Array<String>, U) -> Boolean,
    extras: U,
): Boolean {
    for (element in this) if (predicate(element, extras)) return true
    return false
}

public inline fun <T, U, R : Any> Array<out T>.firstNotNullOfOrNull(
    transform: (T, U) -> R?,
    extras: U,
): R? {
    for (element in this) {
        val result = transform(element, extras)
        if (result != null) {
            return result
        }
    }
    return null
}

/**
 * Returns `true` if at least one tag matches the given [tagName] and is in [tagValues]
 */
fun TagArray.firstAnyLowercaseTaggedValue(
    tagName: String,
    tagValues: Set<String>,
) = this.firstOrNull { it.size > 1 && it[0] == tagName && it[1].lowercase() in tagValues }?.getOrNull(1)

/**
 * Returns `true` if at least one tag matches the given [tagName] and is in [tagValues]
 */
fun TagArray.isAnyLowercaseTagged(
    tagName: String,
    tagValues: Set<String>,
) = this.any { it.size > 1 && it[0] == tagName && it[1].lowercase() in tagValues }

/**
 * Returns `true` if at least one tag matches the given [tagName] and is in [tagValues]
 */
fun TagArray.firstAnyTaggedValue(
    tagName: String,
    tagValues: Set<String>,
) = this.firstOrNull { it.size > 1 && it[0] == tagName && it[1] in tagValues }?.getOrNull(1)

/**
 * Returns `true` if at least one tag has value that contains [text]
 */
fun TagArray.tagValueContains(
    text: String,
    ignoreCase: Boolean = false,
) = this.any { it.size > 1 && it[1].contains(text, ignoreCase) }

fun TagArray.containsAllTagNamesWithValues(names: Set<String>): Boolean {
    val remaining = names.toMutableSet()

    this.forEach {
        if (it.size > 1) {
            remaining.remove(it[0])
        }
    }

    return remaining.isEmpty()
}
