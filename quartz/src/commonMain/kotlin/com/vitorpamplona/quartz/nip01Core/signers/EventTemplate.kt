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
package com.vitorpamplona.quartz.nip01Core.signers

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.core.OptimizedSerializable
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.builder
import com.vitorpamplona.quartz.nip01Core.core.tagArray
import com.vitorpamplona.quartz.utils.TimeUtils

class EventTemplate<T : Event>(
    val createdAt: Long,
    val kind: Int,
    val tags: TagArray,
    val content: String,
) : OptimizedSerializable {
    fun toJson(): String = OptimizedJsonMapper.toJson(this)

    companion object {
        fun fromJson(json: String): EventTemplate<Event> = OptimizedJsonMapper.fromJsonToEventTemplate(json)
    }
}

inline fun <T : Event> eventTemplate(
    kind: Int,
    description: String,
    createdAt: Long = TimeUtils.now(),
    initializer: TagArrayBuilder<T>.() -> Unit = {},
) = EventTemplate<T>(createdAt, kind, tagArray(initializer), description)

fun <T : Event> eventUpdate(
    base: Event,
    createdAt: Long = TimeUtils.now(),
    updater: TagArrayBuilder<T>.() -> Unit = {},
) = EventTemplate<T>(createdAt, base.kind, base.tags.builder(updater), base.content)

fun <T : Event> T.update(
    createdAt: Long = TimeUtils.now(),
    updater: TagArrayBuilder<T>.() -> Unit = {},
) = eventUpdate(this, createdAt, updater)
