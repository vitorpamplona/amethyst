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
package com.vitorpamplona.jsonbench

import com.vitorpamplona.jsonbench.parsers.DslJsonBytes
import com.vitorpamplona.jsonbench.parsers.DslJsonReflect
import com.vitorpamplona.jsonbench.parsers.Fastjson2Dom
import com.vitorpamplona.jsonbench.parsers.Fastjson2Reader
import com.vitorpamplona.jsonbench.parsers.Fastjson2ReflectBytes
import com.vitorpamplona.jsonbench.parsers.Fastjson2ReflectString
import com.vitorpamplona.jsonbench.parsers.GsonReflect
import com.vitorpamplona.jsonbench.parsers.GsonStreaming
import com.vitorpamplona.jsonbench.parsers.GsonTree
import com.vitorpamplona.jsonbench.parsers.JacksonStreamingBytes
import com.vitorpamplona.jsonbench.parsers.JacksonStreamingString
import com.vitorpamplona.jsonbench.parsers.JacksonTree
import com.vitorpamplona.jsonbench.parsers.JsonSmartDom
import com.vitorpamplona.jsonbench.parsers.JsoniterIterator
import com.vitorpamplona.jsonbench.parsers.JsoniterScala
import com.vitorpamplona.jsonbench.parsers.KotlinxElement
import com.vitorpamplona.jsonbench.parsers.KotlinxStream
import com.vitorpamplona.jsonbench.parsers.KotlinxTyped
import com.vitorpamplona.jsonbench.parsers.MinimalJsonDom
import com.vitorpamplona.jsonbench.parsers.MoshiStreaming
import com.vitorpamplona.jsonbench.parsers.OrgJsonDom
import com.vitorpamplona.jsonbench.parsers.ParssonStreaming
import com.vitorpamplona.jsonbench.parsers.QuartzJackson
import com.vitorpamplona.jsonbench.parsers.QuartzKotlinx
import com.vitorpamplona.jsonbench.parsers.QuartzLoop
import com.vitorpamplona.jsonbench.parsers.QuartzLoopNoIntern
import com.vitorpamplona.jsonbench.parsers.QuartzLoopPlain
import com.vitorpamplona.jsonbench.parsers.SimdjsonDom
import com.vitorpamplona.jsonbench.parsers.SimdjsonSchema
import com.vitorpamplona.jsonbench.parsers.Utf8Decode
import com.vitorpamplona.jsonbench.parsers.Utf8Encode
import java.util.Properties

/** Library versions, generated at build time from gradle/libs.versions.toml. */
object Versions {
    private val props =
        Properties().apply {
            Versions::class.java.getResourceAsStream("/jsonbench-versions.properties")?.use { load(it) }
        }

    operator fun get(key: String): String = props.getProperty(key) ?: "unknown"
}

/**
 * Everything the report needs to know about a parser without loading its library: the
 * parent process lists and labels parsers from this table, and only the child JVM that
 * benchmarks a parser calls [create].
 */
class ParserSpec(
    val id: String,
    val library: String,
    val versionKey: String,
    val license: String,
    val mode: String,
    val input: String,
    val notes: String,
    val create: () -> BenchParser<*>,
) {
    val version: String
        get() =
            when (versionKey) {
                "jdk" -> System.getProperty("java.runtime.version") ?: System.getProperty("java.version")
                else -> Versions[versionKey]
            }
}

private const val APACHE = "Apache-2.0"
private const val JDK_LICENSE = "GPL-2.0-only WITH Classpath-exception-2.0"
private const val SIMDJSON_NOTE =
    "Needs the incubating Vector API (--add-modules jdk.incubator.vector, HotSpot 16+); " +
        "jdk.incubator.vector does not exist on Android/ART, so this cannot ship in the app."

val PARSERS: List<ParserSpec> =
    listOf(
        ParserSpec(
            "quartz-jackson",
            "jackson-databind via Quartz JacksonMapper",
            "jackson",
            "Apache-2.0 AND MIT",
            "typed",
            "string",
            "Amethyst's production path: JacksonMapper.fromJson(String) -> Quartz EventDeserializer " +
                "(String.intern() on id, pubkey and tag values; EventFactory builds the kind's Event subclass). The baseline.",
        ) { QuartzJackson() },
        ParserSpec(
            "quartz-kotlinx",
            "kotlinx-serialization-json via Quartz KotlinSerializationMapper",
            "kotlinx-serialization",
            "Apache-2.0 AND MIT",
            "typed",
            "string",
            "Quartz's native/iOS path: KotlinSerializationMapper.fromJson(String) -> EventKSerializer -> EventFactory.",
        ) { QuartzKotlinx() },
        ParserSpec(
            "quartz-loop",
            "jackson-core + Quartz's EventDeserializer loop",
            "jackson",
            "Apache-2.0 AND MIT",
            "typed",
            "string",
            "Breakdown row: Quartz's EventDeserializer loop as shipped (String.intern() on id, pubkey and " +
                "every tag value + EventFactory) on a bare jackson-core parser. quartz-jackson minus this = " +
                "the cost of going through ObjectMapper.readValue.",
        ) { QuartzLoop() },
        ParserSpec(
            "quartz-loop-nointern",
            "jackson-core + Quartz's EventDeserializer loop",
            "jackson",
            "Apache-2.0 AND MIT",
            "typed",
            "string",
            "Breakdown row: quartz-loop without String.intern(). Speed difference = what interning costs; " +
                "retained-heap difference = what it saves.",
        ) { QuartzLoopNoIntern() },
        ParserSpec(
            "quartz-loop-plain",
            "jackson-core + Quartz's EventDeserializer loop",
            "jackson",
            "Apache-2.0 AND MIT",
            "typed",
            "string",
            "Breakdown row: as quartz-loop-nointern but builds a plain Event instead of calling EventFactory. " +
                "The difference is EventFactory's cost.",
        ) { QuartzLoopPlain() },
        ParserSpec(
            "jackson-streaming-bytes",
            "jackson-core",
            "jackson",
            APACHE,
            "typed",
            "string→bytes",
            "JsonFactory.createParser(byte[]) + hand-written field loop (Quartz's loop minus intern/EventFactory).",
        ) { JacksonStreamingBytes() },
        ParserSpec(
            "jackson-streaming-string",
            "jackson-core",
            "jackson",
            APACHE,
            "typed",
            "string",
            "JsonFactory.createParser(String) + hand-written field loop (Quartz's loop minus intern/EventFactory).",
        ) { JacksonStreamingString() },
        ParserSpec(
            "jackson-tree",
            "jackson-databind",
            "jackson",
            APACHE,
            "dom",
            "string→bytes",
            "ObjectMapper.readTree(byte[]) -> JsonNode.",
        ) { JacksonTree() },
        ParserSpec(
            "kotlinx-typed",
            "kotlinx-serialization-json",
            "kotlinx-serialization",
            APACHE,
            "typed",
            "string",
            "Json.decodeFromString into a plugin-generated @Serializable class with List<List<String>> tags.",
        ) { KotlinxTyped() },
        ParserSpec(
            "kotlinx-stream",
            "kotlinx-serialization-json",
            "kotlinx-serialization",
            APACHE,
            "typed",
            "string→bytes",
            "Json.decodeFromStream(ByteArrayInputStream) into the same @Serializable class.",
        ) { KotlinxStream() },
        ParserSpec(
            "kotlinx-element",
            "kotlinx-serialization-json",
            "kotlinx-serialization",
            APACHE,
            "dom",
            "string",
            "Json.parseToJsonElement(String) -> JsonObject.",
        ) { KotlinxElement() },
        ParserSpec(
            "gson-streaming",
            "gson",
            "gson",
            APACHE,
            "typed",
            "string",
            "com.google.gson.stream.JsonReader(StringReader) + hand-written field loop.",
        ) { GsonStreaming() },
        ParserSpec("gson-tree", "gson", "gson", APACHE, "dom", "string", "JsonParser.parseString -> JsonObject.") { GsonTree() },
        ParserSpec(
            "gson-reflect",
            "gson",
            "gson",
            APACHE,
            "typed",
            "string",
            "Gson().fromJson(String, Pojo) (reflective field binding; String[][] tags).",
        ) { GsonReflect() },
        ParserSpec(
            "moshi-streaming",
            "moshi",
            "moshi",
            APACHE,
            "typed",
            "string (okio writeUtf8)",
            "com.squareup.moshi.JsonReader over an okio Buffer (the byte[] -> Buffer copy is timed) + " +
                "JsonReader.Options field selection, as Moshi's generated adapters do. okio is Apache-2.0.",
        ) { MoshiStreaming() },
        ParserSpec(
            "fastjson2-reflect-bytes",
            "fastjson2",
            "fastjson2",
            APACHE,
            "typed",
            "string→bytes",
            "JSON.parseObject(byte[], Pojo) (fastjson2's ASM-generated reader; String[][] tags).",
        ) { Fastjson2ReflectBytes() },
        ParserSpec(
            "fastjson2-reflect-string",
            "fastjson2",
            "fastjson2",
            APACHE,
            "typed",
            "string",
            "JSON.parseObject(String, Pojo) (fastjson2's ASM-generated reader; String[][] tags).",
        ) { Fastjson2ReflectString() },
        ParserSpec(
            "fastjson2-reader",
            "fastjson2",
            "fastjson2",
            APACHE,
            "typed",
            "string→bytes",
            "JSONReader.of(byte[]) + hand-written loop dispatching on readFieldNameHashCode().",
        ) { Fastjson2Reader() },
        ParserSpec(
            "fastjson2-dom",
            "fastjson2",
            "fastjson2",
            APACHE,
            "dom",
            "string→bytes",
            "JSON.parseObject(byte[]) -> JSONObject.",
        ) { Fastjson2Dom() },
        ParserSpec(
            "dsljson-bytes",
            "dsl-json",
            "dsl-json",
            "BSD-3-Clause",
            "typed",
            "string→bytes",
            "Hand-written loop over a reused com.dslplatform.json.JsonReader, dispatching on fillName() hashes " +
                "(what @CompiledJson's annotation processor would generate; no processor used).",
        ) { DslJsonBytes() },
        ParserSpec(
            "dsljson-reflect",
            "dsl-json",
            "dsl-json",
            "BSD-3-Clause",
            "typed",
            "string→bytes",
            "DslJson(Settings.withRuntime().includeServiceLoader()).deserialize(Pojo, byte[], len): " +
                "runtime reflection analysis, no annotation processor.",
        ) { DslJsonReflect() },
        ParserSpec(
            "simdjson-java-dom",
            "simdjson-java",
            "simdjson-java",
            APACHE,
            "dom",
            "string→bytes-padded",
            "SimdJsonParser.parse(byte[], len) -> JsonValue over a reused tape (invalidated by the next parse). " +
                "0.3.0: 0.4.0 needs Java 24. $SIMDJSON_NOTE",
        ) { SimdjsonDom() },
        ParserSpec(
            "simdjson-java-schema",
            "simdjson-java",
            "simdjson-java",
            APACHE,
            "typed",
            "string→bytes-padded",
            "SimdJsonParser.parse(byte[], len, Record.class): schema-based parse into a record with String[][] tags. " +
                "0.3.0 calls ClassResolver.reset() on every parse, so it re-resolves the record by reflection per event " +
                "(the bulk of its time and allocation). " +
                "0.3.0: 0.4.0 needs Java 24. $SIMDJSON_NOTE",
        ) { SimdjsonSchema() },
        ParserSpec(
            "orgjson-dom",
            "org.json",
            "org.json",
            "LicenseRef-Public-Domain",
            "dom",
            "string",
            "new JSONObject(String). Stand-in for Android's built-in org.json (a different, older " +
                "implementation on ART). License: the JSON-java LICENSE file is a public-domain dedication (no SPDX id).",
        ) { OrgJsonDom() },
        ParserSpec(
            "jsoniter-iterator",
            "jsoniter (Java)",
            "jsoniter",
            "MIT",
            "typed",
            "string→bytes",
            "com.jsoniter.JsonIterator iterator API (reset(byte[]) on one reused iterator) + hand-written loop; no codegen.",
        ) { JsoniterIterator() },
        ParserSpec(
            "jsoniter-scala",
            "jsoniter-scala",
            "jsoniter-scala",
            "MIT",
            "typed",
            "string (readFromString)",
            "readFromArray with a JsonCodecMaker.make codec (Scala 3 macro, compiled by Gradle's scala plugin) " +
                "into a case class with Array[Array[String]] tags; the timed loop is Scala. " +
                "Pulls in scala3-library/scala-library (Apache-2.0).",
        ) { JsoniterScala() },
        ParserSpec(
            "parsson-streaming",
            "Eclipse Parsson (Jakarta JSON-P)",
            "parsson",
            "EPL-2.0 OR GPL-2.0-only WITH Classpath-exception-2.0",
            "typed",
            "string",
            "jakarta.json.stream.JsonParser over a StringReader from a reused JsonParserFactory + hand-written loop.",
        ) { ParssonStreaming() },
        ParserSpec(
            "minimaljson-dom",
            "minimal-json",
            "minimal-json",
            "MIT",
            "dom",
            "string",
            "com.eclipsesource.json.Json.parse(String) -> JsonObject.",
        ) { MinimalJsonDom() },
        ParserSpec(
            "jsonsmart-dom",
            "json-smart",
            "json-smart",
            APACHE,
            "dom",
            "string",
            "JSONParser(MODE_RFC4627).parse(String) -> JSONObject (a HashMap).",
        ) { JsonSmartDom() },
        ParserSpec(
            "baseline-utf8-decode",
            "JDK",
            "jdk",
            JDK_LICENSE,
            "baseline",
            "bytes",
            "new String(bytes, UTF_8): what the WebSocket client already paid to hand every parser its String (not added to any row).",
        ) { Utf8Decode() },
        ParserSpec(
            "baseline-utf8-encode",
            "JDK",
            "jdk",
            JDK_LICENSE,
            "baseline",
            "string",
            "String.encodeToByteArray(): the encode every string→bytes row already includes in its time.",
        ) { Utf8Encode() },
    )
