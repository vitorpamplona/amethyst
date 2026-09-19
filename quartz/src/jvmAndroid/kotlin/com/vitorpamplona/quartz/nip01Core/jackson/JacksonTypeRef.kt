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
package com.vitorpamplona.quartz.nip01Core.jackson

import com.fasterxml.jackson.core.type.TypeReference

/**
 * A [TypeReference] carrying the full generic type, e.g. `List<Event>`.
 *
 * This is the one thing the app used jackson-module-kotlin for that plain
 * jackson-databind has no equivalent of — and it is a one-liner, because `reified`
 * substitutes the concrete type into the anonymous subclass before erasure can
 * lose it. `T::class.java` cannot replace it: for `List<Event>` that erases to
 * `List`, and every element comes back a LinkedHashMap.
 *
 * Copied here rather than kept as a dependency so the Kotlin module — and
 * kotlin-reflect behind it, ~1,000 classes — can leave the app. The CLI still
 * depends on the module directly, because it genuinely binds reflectively and is
 * never minified.
 */
inline fun <reified T> jacksonTypeRefOf(): TypeReference<T> = object : TypeReference<T>() {}
