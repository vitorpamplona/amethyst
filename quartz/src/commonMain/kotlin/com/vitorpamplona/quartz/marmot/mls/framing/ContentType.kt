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
package com.vitorpamplona.quartz.marmot.mls.framing

/**
 * MLS ContentType (RFC 9420 Section 6.1).
 */
enum class ContentType(
    val value: Int,
) {
    APPLICATION(1),
    PROPOSAL(2),
    COMMIT(3),
    ;

    companion object {
        fun fromValue(value: Int): ContentType =
            entries.find { it.value == value }
                ?: throw IllegalArgumentException("Unknown ContentType: $value")
    }
}

/**
 * MLS WireFormat (RFC 9420 Section 6).
 */
enum class WireFormat(
    val value: Int,
) {
    PUBLIC_MESSAGE(1),
    PRIVATE_MESSAGE(2),
    WELCOME(3),
    GROUP_INFO(4),
    KEY_PACKAGE(5),
    ;

    companion object {
        fun fromValue(value: Int): WireFormat =
            entries.find { it.value == value }
                ?: throw IllegalArgumentException("Unknown WireFormat: $value")
    }
}

/**
 * MLS SenderType (RFC 9420 Section 6.1).
 */
enum class SenderType(
    val value: Int,
) {
    MEMBER(1),
    EXTERNAL(2),
    NEW_MEMBER_PROPOSAL(3),
    NEW_MEMBER_COMMIT(4),
    ;

    companion object {
        fun fromValue(value: Int): SenderType =
            entries.find { it.value == value }
                ?: throw IllegalArgumentException("Unknown SenderType: $value")
    }
}

/**
 * MLS Sender (RFC 9420 Section 6.1).
 */
data class Sender(
    val senderType: SenderType,
    val leafIndex: Int = 0,
)
