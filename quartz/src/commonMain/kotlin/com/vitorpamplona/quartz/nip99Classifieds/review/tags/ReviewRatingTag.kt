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
package com.vitorpamplona.quartz.nip99Classifieds.review.tags

import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.experimental.ratings.tags.RatingTag
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * An Open Markets review score, `["rating", <0..1>, <category>]`. The primary score's category is
 * [THUMB]; the rest are optional criteria such as [VALUE], [QUALITY], [DELIVERY], [COMMUNICATION],
 * or custom ones. Scores outside 0..1 are refused.
 */
@Stable
data class ReviewRatingTag(
    val score: Double,
    val category: String,
) {
    fun isThumb() = category == THUMB

    fun toTagArray() = assemble(score, category)

    companion object {
        const val TAG_NAME = "rating"

        fun isTag(tag: Array<String>) = parse(tag) != null

        const val THUMB = "thumb"
        const val VALUE = "value"
        const val QUALITY = "quality"
        const val DELIVERY = "delivery"
        const val COMMUNICATION = "communication"

        fun parse(tag: Array<String>): ReviewRatingTag? {
            ensure(tag.has(2)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[2].isNotBlank()) { return null }
            val score = tag[1].trim().toDoubleOrNull() ?: return null
            ensure(score in 0.0..1.0) { return null }
            return ReviewRatingTag(score, tag[2])
        }

        fun assemble(
            score: Double,
            category: String,
        ) = arrayOf(TAG_NAME, RatingTag.format3(score), category)
    }
}
