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
package com.vitorpamplona.quartz.nip06KeyDerivation

class KeyPath(
    val path: List<Long>,
) {
    constructor(path: String) : this(computePath(path))

    val lastChildNumber: Long get() = if (path.isEmpty()) 0L else path.last()

    fun derive(number: Long): KeyPath = KeyPath(path + listOf(number))

    fun append(index: Long): KeyPath = KeyPath(path + listOf(index))

    fun append(indexes: List<Long>): KeyPath = KeyPath(path + indexes)

    fun append(that: KeyPath): KeyPath = KeyPath(path + that.path)

    override fun toString(): String = asString('\'')

    fun asString(hardenedSuffix: Char): String = path.map { childNumberToString(it, hardenedSuffix) }.fold("m") { a, b -> "$a/$b" }

    companion object {
        val empty: KeyPath = KeyPath(listOf())

        fun computePath(path: String): List<Long> {
            fun toNumber(value: String): Long =
                if (value.last() == '\'' || value.last() == 'h') {
                    Hardener.hardened(
                        value.dropLast(1).toLong(),
                    )
                } else {
                    value.toLong()
                }

            val path1 = path.removePrefix("m").removePrefix("/")
            return if (path1.isEmpty()) {
                listOf()
            } else {
                path1.split('/').map { toNumber(it) }
            }
        }

        fun fromPath(path: String): KeyPath = KeyPath(path)

        fun childNumberToString(
            childNumber: Long,
            hardenedSuffix: Char = '\'',
        ): String =
            if (Hardener.isHardened(childNumber)) {
                Hardener.unharden(childNumber).toString() + hardenedSuffix
            } else {
                childNumber.toString()
            }
    }
}

object Hardener {
    val hardenedKeyIndex: Long = 0x80000000L

    fun hardened(index: Long): Long = hardenedKeyIndex + index

    fun unharden(index: Long): Long = index - hardenedKeyIndex

    fun isHardened(index: Long): Boolean = index >= hardenedKeyIndex
}
