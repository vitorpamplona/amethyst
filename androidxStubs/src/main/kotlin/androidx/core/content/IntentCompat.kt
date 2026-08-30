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
package androidx.core.content

import android.content.Intent

/**
 * JVM stand-in for androidx.core.content.IntentCompat.
 *
 * On Android this exists to paper over the typed-extra API that only arrived in
 * API 33; there is no such split here, so it simply reads the extra back and
 * checks the type the caller asked for. Wrong-typed or missing extras come back
 * null, which is what the platform does and what every call site already
 * handles.
 */
object IntentCompat {
    @JvmStatic
    fun <T> getParcelableExtra(
        intent: Intent,
        name: String,
        clazz: Class<T>,
    ): T? =
        intent.extras
            ?.get(name)
            ?.takeIf { clazz.isInstance(it) }
            ?.let { clazz.cast(it) }

    @JvmStatic
    fun <T> getParcelableArrayListExtra(
        intent: Intent,
        name: String,
        clazz: Class<T>,
    ): ArrayList<T>? {
        val raw = intent.extras?.get(name) as? List<*> ?: return null
        // Same contract as the platform's: an entry of the wrong type makes the
        // whole read fail rather than silently shortening the list, because a
        // share that drops files is worse than one that reports nothing.
        if (raw.any { !clazz.isInstance(it) }) return null
        return ArrayList(raw.map { clazz.cast(it) })
    }
}
