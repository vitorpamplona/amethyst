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
package com.vitorpamplona.quartz.nip01Core.core

import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.Rumor

actual object OptimizedJsonMapper {
    actual fun fromJson(json: String): Event = TODO("Not yet implemented")

    actual fun toJson(event: Event): String = TODO("Not yet implemented")

    actual fun fromJsonToTagArray(json: String): Array<Array<String>> = TODO("Not yet implemented")

    actual fun fromJsonToEventTemplate(json: String): EventTemplate<Event> = TODO("Not yet implemented")

    actual fun fromJsonToRumor(json: String): Rumor = TODO("Not yet implemented")

    actual fun toJson(tags: Array<Array<String>>): String = TODO("Not yet implemented")

    actual inline fun <reified T : OptimizedSerializable> fromJsonTo(json: String): T = TODO("Not yet implemented")

    actual fun toJson(value: OptimizedSerializable): String = TODO("Not yet implemented")
}
