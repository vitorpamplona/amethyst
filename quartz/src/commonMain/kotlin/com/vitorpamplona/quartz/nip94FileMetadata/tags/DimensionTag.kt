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
package com.vitorpamplona.quartz.nip94FileMetadata.tags

import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure
import kotlinx.serialization.Serializable

@Serializable
@Stable
class DimensionTag(
    val width: Int,
    val height: Int,
) {
    /**
     * The raw width/height ratio, which is only meaningful when [hasSize] is true. Anywhere the
     * result reaches a layout, use [aspectRatioOrNull] instead.
     */
    fun aspectRatio() = width.toFloat() / height.toFloat()

    /**
     * The ratio to lay out with, or null when the tag carries no usable size.
     *
     * [parse] only rejects the literal string `"0x0"`, so an author-supplied `"0.4x0.4"` truncates
     * to 0x0 and slips through — and [aspectRatio] then returns `0/0`, which is `NaN`. Compose's
     * `Modifier.aspectRatio` throws `IllegalArgumentException` on `NaN` and on `0f`, and clamping
     * does not rescue either: every comparison against `NaN` is false, so `coerceAtLeast` returns
     * it unchanged. A tag any relay can carry would otherwise crash the composition around it.
     */
    fun aspectRatioOrNull(): Float? = if (hasSize()) aspectRatio() else null

    fun hasSize() = width > 0 && height > 0

    override fun toString() = "${width}x$height"

    fun toTagArray() = assemble(this)

    companion object {
        const val TAG_NAME = "dim"

        fun parse(tag: Array<String>): DimensionTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return parse(tag[1])
        }

        fun parse(dim: String): DimensionTag? {
            if (dim == "0x0") return null

            val parts = dim.split("x")
            if (parts.size != 2) return null

            return try {
                // Some clients (e.g. Primal) emit floating-point dimensions like "317.0x498.0"
                // in NIP-92 imeta tags. Parse as Double and truncate to keep those tags usable
                // for pre-load layout reservation.
                val width = parts[0].toDouble().toInt()
                val height = parts[1].toDouble().toInt()

                DimensionTag(width, height)
            } catch (e: Exception) {
                null
            }
        }

        fun assemble(dim: DimensionTag) = arrayOf(TAG_NAME, dim.toString())
    }
}
