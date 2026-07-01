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

/**
 * Cryptographically secure random source shared across Quartz, backed by the
 * platform [SecureRandom]. **Use this — not `kotlin.random.Random`** — for
 * anything security-sensitive: NIP-44 nonces, private keys, gift-wrap timestamps,
 * random `d` tags, subscription ids.
 *
 * ```kotlin
 * val nonce = RandomInstance.bytes(32)      // 32 secure random bytes
 * val dTag = RandomInstance.bytes(16).toHexKey()
 * val subId = RandomInstance.randomChars()  // 16-char [a-zA-Z0-9] id
 * ```
 */
object RandomInstance {
    val randomizer = SecureRandom()

    /** A secure random [Int] across the full 32-bit range (may be negative). */
    fun int() = randomizer.nextInt()

    /** A secure random [Long] across the full 64-bit range (may be negative). */
    fun long() = randomizer.nextLong()

    /** A secure random [Int] in `0 until bound`. */
    fun int(bound: Int) = randomizer.nextInt(bound)

    /** A secure random [Long] in `0 until bound`. */
    fun long(bound: Long) = randomizer.nextLong(bound)

    /** [size] secure random bytes — the go-to for nonces, keys and salts. */
    fun bytes(size: Int) = ByteArray(size).also { randomizer.nextBytes(it) }

    val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')

    /** A single secure random alphanumeric char from [charPool]. */
    fun randomChar() = charPool[randomizer.nextInt(charPool.size)]

    /** A secure random alphanumeric string of [size] chars — handy for subscription ids. */
    fun randomChars(size: Int = 16) = CharArray(size) { randomChar() }.concatToString()
}
