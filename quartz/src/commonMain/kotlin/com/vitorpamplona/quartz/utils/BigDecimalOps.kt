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
package com.vitorpamplona.quartz.utils

operator fun BigDecimal.plus(other: BigDecimal): BigDecimal = add(other)

operator fun BigDecimal.minus(other: BigDecimal): BigDecimal = subtract(other)

/**
 * Truncate to a Long, the way Number.toLong() does on every platform.
 *
 * It has to be an expect *function* rather than a member of `expect class
 * BigDecimal`: every actual is already a Number and so already has toLong(),
 * but java.math.BigDecimal leaves toByte()/toShort() abstract, which makes
 * `expect class BigDecimal : Number` impossible to actualize with the JVM
 * typealias. Without this, `amount.toLong()` in shared code resolves only in
 * the platform compilations and breaks `compileCommonMainKotlinMetadata`.
 */
expect fun BigDecimal.toLongValue(): Long
