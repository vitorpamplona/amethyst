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
package com.vitorpamplona.fantasyconsole.lua

/**
 * A Lua table: an array part for the keys 1..n and a hash part for the rest. Number keys are
 * the 16:16 bit pattern ([Int]), so `t[1]` is key `0x10000`; [rawget]/[rawset] move between the
 * two parts on their own. Iteration ([keys]) visits the array part in order, then the hash part
 * in insertion order.
 */
class LuaTable(
    arrayCapacity: Int = 0,
) {
    private var array: Array<Any?> = if (arrayCapacity > 0) arrayOfNulls(arrayCapacity) else EMPTY
    private var arraySize = 0
    private var hash: LinkedHashMap<Any, Any?>? = null
    var metatable: LuaTable? = null

    fun rawget(key: Any?): Any? {
        if (key is Int) {
            if (key and 0xffff == 0) {
                val i = key shr 16
                if (i in 1..arraySize) return array[i - 1]
            }
        } else if (key == null) {
            return null
        }
        return hash?.get(key)
    }

    fun rawget(name: String): Any? = hash?.get(name)

    /** `t[i]` for a 1-based integer index. */
    fun rawgetInt(i: Int): Any? = if (i in 1..arraySize) array[i - 1] else hash?.get(Fix.fromInt(i))

    fun rawsetInt(
        i: Int,
        value: Any?,
    ) = rawset(Fix.fromInt(i), value)

    fun rawset(
        key: Any?,
        value: Any?,
    ) {
        if (key == null) throw LuaError("table index is nil")
        if (key is Int && key and 0xffff == 0) {
            val i = key shr 16
            if (i in 1..arraySize) {
                array[i - 1] = value
                if (value == null && i == arraySize) trimArray()
                return
            }
            if (i == arraySize + 1 && value != null) {
                append(value)
                hash?.remove(key)
                migrateFromHash()
                return
            }
        }
        val h = hash
        if (value == null) {
            h?.remove(key)
        } else if (h == null) {
            hash = LinkedHashMap<Any, Any?>().also { it[key] = value }
        } else {
            h[key] = value
        }
    }

    private fun append(value: Any?) {
        if (arraySize == array.size) array = array.copyOf(maxOf(4, arraySize * 2))
        array[arraySize++] = value
    }

    private fun trimArray() {
        while (arraySize > 0 && array[arraySize - 1] == null) arraySize--
    }

    /** After the array grows, the keys that now continue it move out of the hash part. */
    private fun migrateFromHash() {
        val h = hash ?: return
        while (h.isNotEmpty()) {
            val next = Fix.fromInt(arraySize + 1)
            val v = h.remove(next) ?: return
            append(v)
        }
    }

    /** The border `#t`: the array part's length (trailing nils are always trimmed). */
    fun length(): Int = arraySize

    /** Inserts at 1-based [index], shifting the rest up (`add(t, v, i)`). */
    fun insert(
        index: Int,
        value: Any?,
    ) {
        if (index == arraySize + 1) {
            rawsetInt(index, value)
            return
        }
        if (index !in 1..arraySize) {
            rawsetInt(index, value)
            return
        }
        append(null)
        for (j in arraySize - 1 downTo index) array[j] = array[j - 1]
        array[index - 1] = value
        migrateFromHash()
    }

    /** Removes the 1-based [index], shifting the rest down; returns the removed value. */
    fun removeAt(index: Int): Any? {
        if (index !in 1..arraySize) return null
        val v = array[index - 1]
        for (j in index - 1 until arraySize - 1) array[j] = array[j + 1]
        array[--arraySize] = null
        trimArray()
        return v
    }

    /** A snapshot of the keys, so a loop may clear fields while it walks them. */
    fun keys(): Array<Any> {
        val h = hash
        val out = arrayOfNulls<Any>(arraySize + (h?.size ?: 0))
        var n = 0
        for (i in 0 until arraySize) if (array[i] != null) out[n++] = Fix.fromInt(i + 1)
        if (h != null) for (k in h.keys) out[n++] = k
        @Suppress("UNCHECKED_CAST")
        return (if (n == out.size) out else out.copyOf(n)) as Array<Any>
    }

    // The hash keys of the traversal `next` is in, and where it is: each step costs O(1), and a
    // key the loop sets to nil still leads to the one after it, as Lua allows.
    private var walkKeys: Array<Any>? = null
    private var walkAt = 0

    /** Lua's `next`: the key after [key] (null for the first), or null at the end. */
    fun next(key: Any?): Any? {
        var start = 0
        if (key != null) {
            val index = if (key is Int && key and 0xffff == 0) key shr 16 else 0
            if (index in 1..arraySize) {
                start = index
            } else if (index > arraySize && hash?.containsKey(key) != true) {
                // An array slot the loop just cleared, which shrank the array: carry on after it.
                start = arraySize
            } else {
                return nextHashKey(key)
            }
        }
        for (i in start until arraySize) if (array[i] != null) return Fix.fromInt(i + 1)
        return nextHashKey(null)
    }

    private fun nextHashKey(after: Any?): Any? {
        val h = hash ?: return null
        var keys = walkKeys
        var i: Int
        if (after == null) {
            keys = h.keys.toTypedArray()
            walkKeys = keys
            i = 0
        } else {
            if (keys == null || walkAt >= keys.size || keys[walkAt] != after) {
                // Not where the last step left off: find the key again (a fresh walk, or a jump).
                keys = h.keys.toTypedArray()
                walkKeys = keys
                walkAt = keys.indexOf(after)
                if (walkAt < 0) return null
            }
            i = walkAt + 1
        }
        while (i < keys.size && h[keys[i]] == null) i++
        if (i >= keys.size) {
            walkKeys = null
            return null
        }
        walkAt = i
        return keys[i]
    }

    override fun toString(): String = "table"

    companion object {
        private val EMPTY = arrayOfNulls<Any?>(0)
    }
}
