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

import java.time.LocalTime
import java.time.format.DateTimeFormatter

actual object Log {
    // Define a formatter for the desired output format (e.g., HH:mm:ss)
    val formatter: DateTimeFormatter? = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    fun time() = LocalTime.now().format(formatter)

    actual fun w(
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        if (throwable != null) {
            println("${time()} WARN : [$tag] $message. Throwable: ${throwable.message}")
        } else {
            println("${time()} WARN : [$tag] $message")
        }
    }

    actual fun e(
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        if (throwable != null) {
            println("${time()} ERROR: [$tag] $message. Throwable: ${throwable.message}")
        } else {
            println("${time()} ERROR: [$tag] $message")
        }
    }

    actual fun d(
        tag: String,
        message: String,
    ) {
        println("${time()} DEBUG: [$tag] $message")
    }

    actual fun i(
        tag: String,
        message: String,
    ) {
        println("${time()} INFO : [$tag] $message")
    }
}
