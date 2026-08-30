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
package androidx.core.app

import androidx.core.graphics.drawable.IconCompat

/**
 * JVM stand-in for androidx.core.app.Person — who sent a message.
 *
 * Pure data, carried faithfully: the name and avatar are what a conversation
 * notification renders, and `isImportant`/`isBot` are what a shade uses to rank
 * it. What varies by platform is whether anything draws them, which is the
 * presenter's problem, not this type's.
 */
class Person private constructor(
    val name: CharSequence?,
    val key: String?,
    val uri: String?,
    val icon: IconCompat?,
    val isBot: Boolean,
    val isImportant: Boolean,
) {
    class Builder {
        private var name: CharSequence? = null
        private var key: String? = null
        private var uri: String? = null
        private var icon: IconCompat? = null
        private var bot = false
        private var important = false

        fun setName(value: CharSequence?) = apply { name = value }

        fun setKey(value: String?) = apply { key = value }

        fun setUri(value: String?) = apply { uri = value }

        fun setIcon(value: IconCompat?) = apply { icon = value }

        fun setBot(value: Boolean) = apply { bot = value }

        fun setImportant(value: Boolean) = apply { important = value }

        fun build() = Person(name, key, uri, icon, bot, important)
    }
}
