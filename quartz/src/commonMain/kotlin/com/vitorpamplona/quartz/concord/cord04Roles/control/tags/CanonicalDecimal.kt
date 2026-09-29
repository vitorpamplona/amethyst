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
package com.vitorpamplona.quartz.concord.cord04Roles.control.tags

/**
 * The strict tag-number form CORD-01 §5 fixes for the Control Plane's machinery tags
 * (`vsk`, `ev`, a `vac` version): decimal with no sign and no leading zeros — `0`, `4`,
 * `12`, never `04`, `+4`, `0x4` or `1e2`.
 *
 * `String.toLongOrNull()` accepts `+4` and `04`, so two clients reading the same edition
 * could disagree about which version it is. The reference client (Armada `isTagDecimal`)
 * refuses anything else, and so do we.
 */
object CanonicalDecimal {
    fun isCanonical(s: String): Boolean {
        if (s.isEmpty()) return false
        if (s.length > 1 && s[0] == '0') return false
        for (c in s) if (c !in '0'..'9') return false
        return true
    }

    /** [s] as a non-negative Long when it is canonical and fits, else null. */
    fun parse(s: String): Long? = if (isCanonical(s)) s.toLongOrNull() else null
}
