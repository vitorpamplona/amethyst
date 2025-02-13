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

typealias Tag = Array<String>

fun Tag.name() = this[0]

fun Tag.value() = this[1]

fun Tag.hasValue() = this[1].isNotEmpty()

fun Tag.nameOrNull() = if (size > 0) name() else null

fun Tag.valueOrNull() = if (size > 1) value() else null

fun Tag.isNameUnsafe(name: String): Boolean = name() == name

fun Tag.isValueUnsafe(value: String): Boolean = value() == value

fun Tag.isValueInUnsafe(values: Set<String>): Boolean = value() in values

fun Tag.match(name: String): Boolean = if (size > 0) isNameUnsafe(name) else false

fun Tag.isValue(value: String): Boolean = if (size > 1) isValueUnsafe(value) else false

fun Tag.match(
    name: String,
    value: String,
    minSize: Int,
): Boolean = if (size >= minSize) isNameUnsafe(name) && isValueUnsafe(value) else false

fun Tag.match(
    name: String,
    values: Set<String>,
    minSize: Int,
): Boolean = if (size >= minSize) isNameUnsafe(name) && isValueInUnsafe(values) else false

fun Tag.match(
    name: String,
    minSize: Int,
): Boolean = if (size >= minSize) isNameUnsafe(name) else false

fun Tag.isNotName(
    name: String,
    minSize: Int,
): Boolean = !match(name, minSize)

fun Tag.matchAndHasValue(
    name: String,
    minSize: Int,
): Boolean = if (size >= minSize) isNameUnsafe(name) && hasValue() else false

fun Tag.valueIfMatches(
    name: String,
    minSize: Int,
): String? = if (match(name, minSize)) value() else null

fun Tag.valueToIntIfMatches(
    name: String,
    minSize: Int,
): Int? = if (match(name, minSize)) value().toIntOrNull() else null
