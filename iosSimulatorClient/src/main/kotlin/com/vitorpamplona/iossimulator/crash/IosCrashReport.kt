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
package com.vitorpamplona.iossimulator.crash

import com.vitorpamplona.devicecore.crash.DeviceCrash
import com.vitorpamplona.iossimulator.idb.IdbCrash
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * An iOS crash report as the device panel and the agents read a crash: what stopped the app and
 * the crashed thread's frames, from the `.ips` file idb hands over.
 *
 * Since iOS 15 a report is two JSON documents, one per line and then the rest: a header
 * (`app_name`, `bundleID`, `timestamp`) and the body (`exception` {type, signal, codes},
 * `asi` (what the app or the Swift runtime said: "Fatal error: Index out of range"),
 * `termination`, `threads` [{triggered, frames [{imageIndex, symbol, symbolLocation,
 * imageOffset}]}], `usedImages` [{name}], `faultingThread`). Anything else (an older text
 * report) is shown as its first lines.
 */
object IosCrashReport {
    private val json = Json { ignoreUnknownKeys = true }
    private val TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

    fun parse(
        info: IdbCrash,
        contents: String,
    ): DeviceCrash {
        val time = TIME.format(Instant.ofEpochSecond(info.timestamp))
        val app = info.bundleId.ifEmpty { info.processName }.ifEmpty { null }
        val body = body(contents)
        if (body == null) {
            val lines =
                contents
                    .lineSequence()
                    .map { it.trimEnd() }
                    .filter { it.isNotBlank() }
                    .toList()
            val headline = lines.firstOrNull { it.startsWith("Exception Type:") }?.substringAfter(':')?.trim() ?: lines.firstOrNull().orEmpty()
            return DeviceCrash(info.name, DeviceCrash.Kind.IOS, time, info.pid.toInt(), app, headline, lines.take(40))
        }
        val exception = body["exception"] as? JsonObject
        val type = exception?.str("type")
        val signal = exception?.str("signal")
        val said = asi(body)
        val termination = (body["termination"] as? JsonObject)?.let { t -> t.str("indicator") ?: t.str("reasons") }
        val headline =
            listOfNotNull(
                listOfNotNull(type, signal?.let { "($it)" }).joinToString(" ").ifEmpty { null },
                said ?: termination,
            ).joinToString(": ").ifEmpty { "crashed" }
        return DeviceCrash(info.name, DeviceCrash.Kind.IOS, time, info.pid.toInt(), app, headline, frames(body))
    }

    /** The report's body: the document after the header line, or the whole when it is one. */
    private fun body(contents: String): JsonObject? {
        val text = contents.trimStart()
        if (!text.startsWith("{")) return null
        val newline = text.indexOf('\n')
        val rest = if (newline > 0) text.substring(newline + 1).trimStart() else ""
        return (if (rest.startsWith("{")) parse(rest) else null) ?: parse(text)
    }

    private fun parse(text: String): JsonObject? = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()

    /** What the process said before it went: `asi` maps each library to its lines. */
    private fun asi(body: JsonObject): String? =
        (body["asi"] as? JsonObject)
            ?.values
            ?.flatMap { (it as? JsonArray).orEmpty() }
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
            ?.filter { it.isNotEmpty() }
            ?.joinToString(" ")
            ?.ifEmpty { null }
            ?.let { if (it.length > 300) it.take(297) + "…" else it }

    /** The crashed thread's frames: `#0 MyApp  ContentView.body.getter + 120`. */
    private fun frames(body: JsonObject): List<String> {
        val images = (body["usedImages"] as? JsonArray).orEmpty().map { (it as? JsonObject)?.str("name") ?: "?" }
        val threads = (body["threads"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val faulting = (body["faultingThread"] as? JsonPrimitive)?.intOrNull
        val thread = threads.firstOrNull { (it["triggered"] as? JsonPrimitive)?.contentOrNull == "true" } ?: faulting?.let { threads.getOrNull(it) } ?: return emptyList()
        return (thread["frames"] as? JsonArray).orEmpty().mapIndexedNotNull { i, f ->
            val frame = f as? JsonObject ?: return@mapIndexedNotNull null
            val image = (frame["imageIndex"] as? JsonPrimitive)?.intOrNull?.let { images.getOrNull(it) } ?: "?"
            val symbol = frame.str("symbol")
            val at = (frame["symbolLocation"] as? JsonPrimitive)?.longOrNull
            val offset = (frame["imageOffset"] as? JsonPrimitive)?.longOrNull
            "#$i $image  " + (symbol?.let { s -> at?.let { "$s + $it" } ?: s } ?: offset?.let { "0x%x".format(it) } ?: "?")
        }
    }

    private fun JsonObject.str(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}
