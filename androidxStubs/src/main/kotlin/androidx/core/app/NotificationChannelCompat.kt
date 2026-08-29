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

import android.app.NotificationChannel

/**
 * JVM stand-in for androidx.core.app.NotificationChannelCompat.
 *
 * A thin builder over [NotificationChannel], as the real one is. Channels have
 * no desktop counterpart — the OS decides how a notification behaves, not the
 * app — but they are still recorded, because code reads them back to ask
 * whether a channel exists before posting.
 */
class NotificationChannelCompat private constructor(
    val channel: NotificationChannel,
) {
    val id: String get() = channel.id
    val importance: Int get() = channel.importance

    class Builder(
        private val id: String,
        private val importance: Int,
    ) {
        private var name: CharSequence? = null
        private var description: String? = null
        private var group: String? = null

        fun setName(value: CharSequence?) = apply { name = value }

        fun setDescription(value: String?) = apply { description = value }

        fun setGroup(value: String?) = apply { group = value }

        fun setShowBadge(value: Boolean) = apply { }

        fun setSound(
            sound: Any?,
            attributes: Any?,
        ) = apply { }

        fun setLightsEnabled(value: Boolean) = apply { }

        fun setLightColor(value: Int) = apply { }

        fun setVibrationEnabled(value: Boolean) = apply { }

        fun setVibrationPattern(value: LongArray?) = apply { }

        fun build(): NotificationChannelCompat =
            NotificationChannelCompat(
                NotificationChannel(id, name ?: id, importance).also {
                    it.description = description
                    it.group = group
                },
            )
    }
}
