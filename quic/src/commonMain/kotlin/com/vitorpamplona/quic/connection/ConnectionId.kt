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
package com.vitorpamplona.quic.connection

import com.vitorpamplona.quartz.utils.RandomInstance

/**
 * QUIC connection identifier per RFC 9000 §5.1.
 *
 * Length must be 0..20. Zero-length CIDs are legal but only the peer that
 * issued them ever uses them; we always issue at least 8 bytes for our source
 * IDs so the server can route packets back even after path migration (which
 * we don't initiate, but they do happen on roaming clients).
 */
class ConnectionId(
    val bytes: ByteArray,
) {
    val length: Int get() = bytes.size

    init {
        require(bytes.size in 0..20) { "CID length out of range: ${bytes.size}" }
    }

    fun toHex(): String = bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    override fun equals(other: Any?): Boolean = other is ConnectionId && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "ConnectionId(${toHex()})"

    companion object {
        /** Generate a random CID of the given length using [RandomInstance]. */
        fun random(length: Int = 8): ConnectionId {
            require(length in 0..20) { "CID length out of range: $length" }
            return ConnectionId(RandomInstance.bytes(length))
        }

        val EMPTY = ConnectionId(ByteArray(0))
    }
}
