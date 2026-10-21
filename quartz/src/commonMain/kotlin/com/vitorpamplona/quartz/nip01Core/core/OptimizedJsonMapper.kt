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
import kotlinx.serialization.json.Json

interface OptimizedSerializable

/**
 * Quartz's JSON entry points, the same on every target.
 *
 * Everything Nostr-shaped (events, tags, templates, rumors, filters, every relay message and
 * client command, NIP-46 messages) goes through Quartz's own [NostrJsonReader] and
 * [NostrJsonWriter]. Every other [OptimizedSerializable] (NIP-47, CLINK) goes through kotlinx,
 * whose serializers name every field as a string literal, so R8 has nothing to keep and a field
 * of the wrong shape from someone else's wallet reads as absent rather than failing the message.
 */
object OptimizedJsonMapper {
    fun fromJson(json: String): Event = NostrJsonReader.readEvent(json)

    fun toJson(event: Event): String = NostrJsonWriter.toJson(event)

    fun fromJsonToMessage(json: String): Message = NostrJsonReader.readMessage(json)

    fun fromJsonToCommand(json: String): Command = NostrJsonReader.readCommand(json)

    fun fromJsonToTagArray(json: String): Array<Array<String>> = NostrJsonReader.readTagArray(json)

    fun fromJsonToEventTemplate(json: String): EventTemplate<Event> = NostrJsonReader.readEventTemplate(json)

    fun fromJsonToEventList(json: String): List<Event> = NostrJsonReader.readEventList(json)

    fun fromJsonToRumor(json: String): Rumor = NostrJsonReader.readRumor(json)

    fun toJson(tags: Array<Array<String>>): String = NostrJsonWriter.toJson(tags)

    inline fun <reified T : OptimizedSerializable> fromJsonTo(json: String): T =
        readKnownType(T::class, json) as T? ?: try {
            KotlinSerializationMapper.fromJsonTo<T>(json)
        } catch (e: SerializationException) {
            throw IllegalArgumentException(e.message, e)
        }

    fun toJson(value: OptimizedSerializable): String = NostrJsonWriter.toJsonOrNull(value) ?: KotlinSerializationMapper.toJson(value)
}

object JsonMapper {
    val jsonInstance =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    inline fun <reified T> fromJson(json: String): T = jsonInstance.decodeFromString<T>(json)

    inline fun <reified T : Any> toJson(obj: T): String = jsonInstance.encodeToString(obj)
}
