/**
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
package com.vitorpamplona.quartz.nip73ExternalIds.movies

import com.vitorpamplona.quartz.nip73ExternalIds.ExternalId
import com.vitorpamplona.quartz.utils.ensure
import kotlin.math.min

class MovieId(
    val isan: String,
    val hint: String? = null,
) : ExternalId {
    override fun toScope() = toScope(isan)

    override fun toKind() = toKind(isan)

    override fun hint() = hint

    companion object {
        const val KIND = "isan"
        const val PREFIX_COLON = "isan:"

        // "isan:0000-0000-401A-0000-7"
        fun toScope(isan: String) =
            PREFIX_COLON +
                isan.lowercase().substring(
                    0,
                    min(21, isan.length),
                )

        fun toKind(isan: String) = KIND

        fun match(
            encoded: String,
            value: String,
        ): Boolean {
            ensure(encoded.startsWith(PREFIX_COLON)) { return false }
            return encoded.indexOf(value, PREFIX_COLON.length) > 0
        }

        fun parse(encoded: String): String? {
            ensure(encoded.startsWith(PREFIX_COLON)) { return null }
            return encoded.substring(PREFIX_COLON.length)
        }

        fun parse(
            encoded: String,
            hint: String?,
        ): MovieId? {
            ensure(encoded.startsWith(PREFIX_COLON)) { return null }
            return MovieId(encoded.substring(PREFIX_COLON.length), hint)
        }
    }
}
