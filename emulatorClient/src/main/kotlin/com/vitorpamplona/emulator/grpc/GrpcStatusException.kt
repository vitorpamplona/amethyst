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
package com.vitorpamplona.emulator.grpc

import java.io.IOException

/**
 * A gRPC call that ended with a non-OK `grpc-status`, or an HTTP answer that was not gRPC at all
 * (then [status] is [UNKNOWN] and [httpCode] says what came back).
 */
class GrpcStatusException(
    val status: Int,
    val grpcMessage: String?,
    val method: String,
    val httpCode: Int = 200,
) : IOException(
        buildString {
            append(method)
                .append(": gRPC status ")
                .append(status)
                .append(" (")
                .append(statusName(status))
                .append(")")
            if (httpCode != 200) append(", HTTP ").append(httpCode)
            if (!grpcMessage.isNullOrEmpty()) append(": ").append(grpcMessage)
        },
    ) {
    val statusName: String get() = statusName(status)

    companion object {
        const val OK = 0
        const val CANCELLED = 1
        const val UNKNOWN = 2
        const val INVALID_ARGUMENT = 3
        const val DEADLINE_EXCEEDED = 4
        const val NOT_FOUND = 5
        const val ALREADY_EXISTS = 6
        const val PERMISSION_DENIED = 7
        const val RESOURCE_EXHAUSTED = 8
        const val FAILED_PRECONDITION = 9
        const val ABORTED = 10
        const val OUT_OF_RANGE = 11
        const val UNIMPLEMENTED = 12
        const val INTERNAL = 13
        const val UNAVAILABLE = 14
        const val DATA_LOSS = 15
        const val UNAUTHENTICATED = 16

        private val NAMES =
            arrayOf(
                "OK",
                "CANCELLED",
                "UNKNOWN",
                "INVALID_ARGUMENT",
                "DEADLINE_EXCEEDED",
                "NOT_FOUND",
                "ALREADY_EXISTS",
                "PERMISSION_DENIED",
                "RESOURCE_EXHAUSTED",
                "FAILED_PRECONDITION",
                "ABORTED",
                "OUT_OF_RANGE",
                "UNIMPLEMENTED",
                "INTERNAL",
                "UNAVAILABLE",
                "DATA_LOSS",
                "UNAUTHENTICATED",
            )

        fun statusName(status: Int): String = NAMES.getOrNull(status) ?: "CODE_$status"
    }
}
