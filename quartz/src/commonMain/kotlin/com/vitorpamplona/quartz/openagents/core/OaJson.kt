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
package com.vitorpamplona.quartz.openagents.core

import kotlinx.serialization.json.Json

/**
 * The JSON instance OpenAgents bodies are read and written with.
 *
 * Reading ignores unknown keys, like every content parser in Quartz: a client that renders and
 * indexes a record is not the place that admits it (the contracts' strict reader refuses unknown
 * fields before any EFFECT, and Amethyst performs none). Writing drops absent optional fields
 * instead of writing `null`, because the contracts treat omission and `null` as different values
 * and a strict reader refuses a `null` where none is specified. The one field that must be an
 * explicit `null` (a hidden listing's `release`) is written by hand.
 */
object OaJson {
    val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }

    /** The record version every v1 body carries. */
    const val VERSION = 1
}
