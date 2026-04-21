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

actual object PlatformLog {
    private val formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    private fun time() = LocalTime.now().format(formatter)

    private fun log(
        level: String,
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        // Diagnostics go to stderr so callers that pipe stdout (the CLI uses
        // stdout for its JSON contract, desktop apps may too) aren't corrupted
        // by log interleaving. Consumers that prefer stdout can just redirect
        // 2>&1 at the shell level.
        if (throwable != null) {
            System.err.println("${time()} $level: [$tag] $message. Throwable: ${throwable.message}")
        } else {
            System.err.println("${time()} $level: [$tag] $message")
        }
    }

    actual fun w(
        tag: String,
        message: String,
        throwable: Throwable?,
    ) = log("WARN ", tag, message, throwable)

    actual fun e(
        tag: String,
        message: String,
        throwable: Throwable?,
    ) = log("ERROR", tag, message, throwable)

    actual fun d(
        tag: String,
        message: String,
        throwable: Throwable?,
    ) = log("DEBUG", tag, message, throwable)

    actual fun i(
        tag: String,
        message: String,
        throwable: Throwable?,
    ) = log("INFO ", tag, message, throwable)
}
