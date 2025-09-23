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
package com.vitorpamplona.quartz.nip55AndroidSigner

import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vitorpamplona.quartz.nip01Core.jackson.InliningTagArrayPrettyPrinter
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.results.IntentResult
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.results.IntentResultJsonDeserializer
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.results.IntentResultJsonSerializer
import com.vitorpamplona.quartz.nip55AndroidSigner.api.permission.Permission
import com.vitorpamplona.quartz.nip55AndroidSigner.api.permission.PermissionDeserializer
import com.vitorpamplona.quartz.nip55AndroidSigner.api.permission.PermissionSerializer
import java.io.InputStream
import kotlin.jvm.java

object JsonMapperNip55 {
    val defaultMapper: ObjectMapper =
        jacksonObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature())
            .setDefaultPrettyPrinter(InliningTagArrayPrettyPrinter())
            .registerModule(
                SimpleModule()
                    .addDeserializer(IntentResult::class.java, IntentResultJsonDeserializer())
                    .addSerializer(IntentResult::class.java, IntentResultJsonSerializer())
                    .addDeserializer(Permission::class.java, PermissionDeserializer())
                    .addSerializer(Permission::class.java, PermissionSerializer()),
            )

    inline fun <reified T> fromJsonTo(json: String): T = defaultMapper.readValue(json, T::class.java)

    inline fun <reified T> fromJsonTo(json: InputStream): T = defaultMapper.readValue(json, T::class.java)

    fun toJson(event: ArrayNode): String = defaultMapper.writeValueAsString(event)

    fun toJson(event: ObjectNode?): String = defaultMapper.writeValueAsString(event)

    fun toJson(value: Any): String = defaultMapper.writeValueAsString(value)
}
