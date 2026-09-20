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
package com.vitorpamplona.quartz.nip55AndroidSigner

import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.vitorpamplona.quartz.nip01Core.jackson.InliningTagArrayPrettyPrinter
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.results.IntentResult
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.results.IntentResultJsonDeserializer
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.results.IntentResultJsonSerializer
import com.vitorpamplona.quartz.nip55AndroidSigner.api.permission.Permission
import com.vitorpamplona.quartz.nip55AndroidSigner.api.permission.PermissionDeserializer
import com.vitorpamplona.quartz.nip55AndroidSigner.api.permission.PermissionSerializer

object JsonMapperNip55 {
    val defaultMapper: ObjectMapper =
        ObjectMapper()
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

    /**
     * Four named entry points instead of one `inline fun <reified T> fromJsonTo`.
     *
     * The generic version resolved T through a TypeReference, which reads its type
     * argument back off an anonymous subclass's generic superclass. R8 in full mode
     * does not keep that: on device it throws
     *
     *   IllegalArgumentException: Internal error: TypeReference constructed without
     *   actual type information
     *
     * and because these JavaTypes are built in <clinit>, a single failure poisons the
     * whole object — every later touch comes back as NoClassDefFoundError. That is
     * what took out JacksonMapper on the first minified build; this file carries the
     * identical pattern and only escaped because NIP-55 needs an external signer
     * installed to reach.
     *
     * `T::class.java` cannot stand in for all four: List<IntentResult> erases to List
     * and every element comes back a LinkedHashMap. TypeFactory takes Class objects
     * directly and keeps the element type, so there is nothing left for R8 to erase.
     */
    val permissionType: JavaType = defaultMapper.typeFactory.constructType(Permission::class.java)

    val permissionArrayType: JavaType = defaultMapper.typeFactory.constructArrayType(Permission::class.java)

    val intentResultType: JavaType = defaultMapper.typeFactory.constructType(IntentResult::class.java)

    val intentResultListType: JavaType = defaultMapper.typeFactory.constructCollectionType(List::class.java, IntentResult::class.java)

    fun fromJsonToPermission(json: String): Permission = defaultMapper.readValue(json, permissionType)

    fun fromJsonToPermissionArray(json: String): Array<Permission> = defaultMapper.readValue(json, permissionArrayType)

    fun fromJsonToIntentResult(json: String): IntentResult = defaultMapper.readValue(json, intentResultType)

    fun fromJsonToIntentResultList(json: String): List<IntentResult> = defaultMapper.readValue(json, intentResultListType)

    fun toJson(event: ArrayNode): String = defaultMapper.writeValueAsString(event)

    fun toJson(event: ObjectNode?): String = defaultMapper.writeValueAsString(event)

    fun toJson(value: Any): String = defaultMapper.writeValueAsString(value)
}
