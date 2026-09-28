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
package com.vitorpamplona.quartz.nip43RelayMembers.roles

import androidx.compose.runtime.Immutable

/**
 * A NIP-43 role as the relay defines it: the content of a kind 33534
 * [RelayRoleEvent], and the `[id, label, description, color, order]` params
 * of the NIP-86 `createrole` / `editrole` methods.
 *
 * [color] is a hue in `0..360` (see [isValidHue]); [order] is a display-only
 * sort key. Everything but [id] is optional.
 */
@Immutable
data class RelayRole(
    val id: String,
    val label: String? = null,
    val description: String? = null,
    val color: Int? = null,
    val order: Int? = null,
) {
    companion object {
        const val MIN_HUE = 0
        const val MAX_HUE = 360

        fun isValidHue(hue: Int) = hue in MIN_HUE..MAX_HUE
    }
}
