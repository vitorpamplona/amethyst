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
package com.vitorpamplona.quartz.nip01Core.core

import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.KotlinSerializationMapper
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.Rumor
import kotlinx.serialization.SerializationException

actual object OptimizedJsonMapper {
    actual fun fromJson(json: String): Event =
        try {
            KotlinSerializationMapper.fromJson(json)
        } catch (e: SerializationException) {
            throw IllegalArgumentException(e.message, e)
        }

    actual fun toJson(event: Event): String = KotlinSerializationMapper.toJson(event)

    actual fun fromJsonToMessage(json: String): Message =
        try {
            KotlinSerializationMapper.fromJsonToMessage(json)
        } catch (e: SerializationException) {
            throw IllegalArgumentException(e.message, e)
        }

    actual fun fromJsonToCommand(json: String): Command =
        try {
            KotlinSerializationMapper.fromJsonToCommand(json)
        } catch (e: SerializationException) {
            throw IllegalArgumentException(e.message, e)
        }

    actual fun fromJsonToTagArray(json: String): Array<Array<String>> =
        try {
            KotlinSerializationMapper.fromJsonToTagArray(json)
        } catch (e: SerializationException) {
            throw IllegalArgumentException(e.message, e)
        }

    actual fun fromJsonToEventTemplate(json: String): EventTemplate<Event> =
        try {
            KotlinSerializationMapper.fromJsonToEventTemplate(json)
        } catch (e: SerializationException) {
            throw IllegalArgumentException(e.message, e)
        }

    actual fun fromJsonToEventList(json: String): List<Event> =
        try {
            KotlinSerializationMapper.fromJsonToEventList(json)
        } catch (e: SerializationException) {
            throw IllegalArgumentException(e.message, e)
        }

    actual fun fromJsonToRumor(json: String): Rumor =
        try {
            KotlinSerializationMapper.fromJsonToRumor(json)
        } catch (e: SerializationException) {
            throw IllegalArgumentException(e.message, e)
        }

    actual fun toJson(tags: Array<Array<String>>): String = KotlinSerializationMapper.toJson(tags)

    actual inline fun <reified T : OptimizedSerializable> fromJsonTo(json: String): T =
        try {
            KotlinSerializationMapper.fromJsonTo<T>(json)
        } catch (e: SerializationException) {
            throw IllegalArgumentException(e.message, e)
        }

    actual fun toJson(value: OptimizedSerializable): String = KotlinSerializationMapper.toJson(value)
}
