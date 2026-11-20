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
package com.vitorpamplona.amethyst.commons.service.uploads.gif

/**
 * An int-to-int hash map with open addressing, for the GIF writer's per-pixel color lookups: no
 * boxing, no entry objects. Key 0 is reserved as the empty slot; the writer only stores opaque
 * colors, whose alpha byte is never 0.
 */
internal class IntIntMap(
    expected: Int = 64,
) {
    private var keys: IntArray
    private var values: IntArray
    var size = 0
        private set

    init {
        var capacity = 16
        while (capacity < expected * 2) capacity = capacity shl 1
        keys = IntArray(capacity)
        values = IntArray(capacity)
    }

    /** The value for [key], or [missing] when it has none. */
    fun get(
        key: Int,
        missing: Int = -1,
    ): Int {
        val mask = keys.size - 1
        var i = mix(key) and mask
        while (true) {
            val k = keys[i]
            if (k == key) return values[i]
            if (k == 0) return missing
            i = (i + 1) and mask
        }
    }

    fun put(
        key: Int,
        value: Int,
    ) {
        require(key != 0) { "0 is the empty slot" }
        if ((size + 1) * 2 > keys.size) grow()
        val mask = keys.size - 1
        var i = mix(key) and mask
        while (true) {
            val k = keys[i]
            if (k == key) {
                values[i] = value
                return
            }
            if (k == 0) {
                keys[i] = key
                values[i] = value
                size++
                return
            }
            i = (i + 1) and mask
        }
    }

    /** Adds one to [key]'s count, starting from 0. */
    fun increment(key: Int) = put(key, get(key, 0) + 1)

    /** Calls [action] with every key and its value. */
    inline fun forEach(action: (key: Int, value: Int) -> Unit) {
        for (i in keysArray().indices) {
            val k = keysArray()[i]
            if (k != 0) action(k, valuesArray()[i])
        }
    }

    @PublishedApi
    internal fun keysArray() = keys

    @PublishedApi
    internal fun valuesArray() = values

    private fun grow() {
        val oldKeys = keys
        val oldValues = values
        keys = IntArray(oldKeys.size * 2)
        values = IntArray(oldKeys.size * 2)
        size = 0
        for (i in oldKeys.indices) if (oldKeys[i] != 0) put(oldKeys[i], oldValues[i])
    }

    private fun mix(key: Int): Int {
        val h = key * -0x61c88647
        return h xor (h ushr 16)
    }
}
