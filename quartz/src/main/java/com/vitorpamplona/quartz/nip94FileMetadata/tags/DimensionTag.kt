/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.nip94FileMetadata.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

class DimensionTag(
    val width: Int,
    val height: Int,
) {
    fun aspectRatio() = width.toFloat() / height.toFloat()

    fun hasSize() = width > 0 && height > 0

    override fun toString() = "${width}x$height"

    fun toTagArray() = assemble(this)

    companion object {
        const val TAG_NAME = "dim"

        @JvmStatic
        fun parse(tag: Array<String>): DimensionTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return parse(tag[1])
        }

        @JvmStatic
        fun parse(dim: String): DimensionTag? {
            if (dim == "0x0") return null

            val parts = dim.split("x")
            if (parts.size != 2) return null

            return try {
                val width = parts[0].toInt()
                val height = parts[1].toInt()

                DimensionTag(width, height)
            } catch (e: Exception) {
                null
            }
        }

        @JvmStatic
        fun assemble(dim: DimensionTag) = arrayOf(TAG_NAME, dim.toString())
    }
}
