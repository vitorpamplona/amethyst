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
package com.vitorpamplona.amethyst.cli

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Stdout/stderr emitter for amy.
 *
 * Default mode is human-readable text. Pass `--json` on the command line
 * to switch to the machine contract: a single JSON object on stdout per
 * successful command, JSON `{"error": ..., "detail": ...}` on stderr for
 * failures. The JSON shape is the public API — the text shape is not.
 *
 * The text renderer:
 *   - aligns sibling keys at each indentation level (YAML-ish columns),
 *   - rewrites unix-second timestamps under `*_at` keys to a readable
 *     ISO + relative form,
 *   - rewrites byte counts under `*_bytes` keys to KiB / MiB / GiB,
 *   - rewrites booleans to yes / no,
 *   - paints the result with ANSI colour when stdout is a TTY (disabled
 *     by `NO_COLOR`, forced on by `CLICOLOR_FORCE`).
 */
object Output {
    enum class Mode { TEXT, JSON }

    @Volatile var mode: Mode = Mode.TEXT

    val mapper: ObjectMapper = jacksonObjectMapper()

    fun emit(result: Any?) {
        when (mode) {
            Mode.JSON -> println(mapper.writeValueAsString(result))
            Mode.TEXT -> println(renderText(result))
        }
    }

    fun error(
        code: String,
        detail: String? = null,
    ): Int {
        when (mode) {
            Mode.JSON -> {
                val payload = mutableMapOf<String, Any>("error" to code)
                if (detail != null) payload["detail"] = detail
                System.err.println(mapper.writeValueAsString(payload))
            }

            Mode.TEXT -> {
                val color = Ansi.forStream(isStderr = true)
                val prefix = color.bold(color.red("error"))
                val codePart = color.yellow(code)
                System.err.println(if (detail != null) "$prefix: $codePart: $detail" else "$prefix: $codePart")
            }
        }
        return 1
    }

    private fun renderText(value: Any?): String {
        val color = Ansi.forStream(isStderr = false)
        val out = StringBuilder()
        when (val v = unwrap(value)) {
            null -> {}

            is Map<*, *> -> {
                renderMapBody(out, v, "", color)
            }

            is List<*> -> {
                renderListBody(out, v, "", color)
            }

            else -> {
                out.append(v.toString()).append('\n')
            }
        }
        return out.toString().trimEnd('\n')
    }

    /**
     * Convert any embedded Jackson [JsonNode] into plain Java types
     * (`LinkedHashMap` / `ArrayList` / boxed primitives) so the generic
     * renderer can descend into it. Plain Maps / Lists / scalars are
     * returned unchanged. Walks recursively because callers commonly
     * mix a JsonNode subtree into a hand-built Map (e.g. profile show
     * stuffing the parsed kind:0 content under a `metadata` key).
     */
    private fun unwrap(value: Any?): Any? =
        when (value) {
            is JsonNode -> mapper.convertValue(value, Any::class.java)
            is Map<*, *> -> value.mapValues { (_, v) -> unwrap(v) }
            is List<*> -> value.map { unwrap(it) }
            else -> value
        }

    private fun renderMapBody(
        out: StringBuilder,
        map: Map<*, *>,
        prefix: String,
        color: Ansi,
    ) {
        val entries = map.entries.filter { it.value != null }
        if (entries.isEmpty()) return
        val keyWidth = entries.maxOf { it.key.toString().length }
        for ((k, v) in entries) {
            val key = k.toString()
            val coloredKey = color.bold(key)
            val padding = " ".repeat(keyWidth - key.length)
            when (v) {
                is Map<*, *> -> {
                    if (v.isEmpty()) {
                        out
                            .append(prefix)
                            .append(coloredKey)
                            .append(":")
                            .append(padding)
                        out.append(' ').append(color.dim("(empty)")).append('\n')
                    } else {
                        out.append(prefix).append(coloredKey).append(":\n")
                        renderMapBody(out, v, "$prefix  ", color)
                    }
                }

                is List<*> -> {
                    if (v.isEmpty()) {
                        out
                            .append(prefix)
                            .append(coloredKey)
                            .append(":")
                            .append(padding)
                        out.append(' ').append(color.dim("(none)")).append('\n')
                    } else {
                        out.append(prefix).append(coloredKey).append(":\n")
                        renderListBody(out, v, "$prefix  ", color)
                    }
                }

                else -> {
                    out
                        .append(prefix)
                        .append(coloredKey)
                        .append(":")
                        .append(padding)
                        .append(' ')
                        .append(formatScalar(key, v, color))
                        .append('\n')
                }
            }
        }
    }

    private fun renderListBody(
        out: StringBuilder,
        list: List<*>,
        prefix: String,
        color: Ansi,
    ) {
        val dash = color.dim("-")
        for (item in list) {
            when (item) {
                null -> {}

                is Map<*, *> -> {
                    val entries = item.entries.filter { it.value != null }
                    if (entries.isEmpty()) {
                        out
                            .append(prefix)
                            .append(dash)
                            .append(' ')
                            .append(color.dim("(empty)"))
                            .append('\n')
                        continue
                    }
                    val keyWidth = entries.maxOf { it.key.toString().length }
                    val first = entries.first()
                    val firstV = first.value
                    if (firstV is Map<*, *> || firstV is List<*>) {
                        out.append(prefix).append(dash).append('\n')
                        renderMapBody(out, item, "$prefix  ", color)
                    } else {
                        val firstKey = first.key.toString()
                        out
                            .append(prefix)
                            .append(dash)
                            .append(' ')
                            .append(color.bold(firstKey))
                            .append(':')
                            .append(" ".repeat(keyWidth - firstKey.length))
                            .append(' ')
                            .append(formatScalar(firstKey, firstV, color))
                            .append('\n')
                        for ((rk, rv) in entries.drop(1)) {
                            val rKey = rk.toString()
                            val rPad = " ".repeat(keyWidth - rKey.length)
                            when (rv) {
                                is Map<*, *> -> {
                                    if (rv.isEmpty()) {
                                        out
                                            .append("$prefix  ")
                                            .append(color.bold(rKey))
                                            .append(':')
                                            .append(rPad)
                                            .append(' ')
                                            .append(color.dim("(empty)"))
                                            .append('\n')
                                    } else {
                                        out.append("$prefix  ").append(color.bold(rKey)).append(":\n")
                                        renderMapBody(out, rv, "$prefix    ", color)
                                    }
                                }

                                is List<*> -> {
                                    if (rv.isEmpty()) {
                                        out
                                            .append("$prefix  ")
                                            .append(color.bold(rKey))
                                            .append(':')
                                            .append(rPad)
                                            .append(' ')
                                            .append(color.dim("(none)"))
                                            .append('\n')
                                    } else {
                                        out.append("$prefix  ").append(color.bold(rKey)).append(":\n")
                                        renderListBody(out, rv, "$prefix    ", color)
                                    }
                                }

                                else -> {
                                    out
                                        .append("$prefix  ")
                                        .append(color.bold(rKey))
                                        .append(':')
                                        .append(rPad)
                                        .append(' ')
                                        .append(formatScalar(rKey, rv, color))
                                        .append('\n')
                                }
                            }
                        }
                    }
                }

                is List<*> -> {
                    out.append(prefix).append(dash).append('\n')
                    renderListBody(out, item, "$prefix  ", color)
                }

                else -> {
                    out
                        .append(prefix)
                        .append(dash)
                        .append(' ')
                        .append(formatScalar("", item, color))
                        .append('\n')
                }
            }
        }
    }

    /**
     * Generic scalar formatting that's safe for any command:
     *   - `*_at` ints  → `2026-04-25 12:30:45Z (2m ago)`
     *   - `*_bytes` ints → `7.0 KiB`
     *   - bools → `yes` / `no`, coloured.
     * The key is the snake_case name from the result map; an empty key
     * means the scalar is an array element.
     */
    private fun formatScalar(
        key: String,
        value: Any?,
        color: Ansi,
    ): String {
        if (value is Boolean) {
            return if (value) color.green("yes") else color.red("no")
        }
        val asLong = (value as? Number)?.toLong()
        if (asLong != null) {
            if (key.endsWith("_at") && asLong > 1_000_000_000L) {
                return formatTimestamp(asLong, color)
            }
            if (key.endsWith("_bytes") || key == "size") {
                return formatBytes(asLong)
            }
        }
        return value.toString()
    }

    private fun formatTimestamp(
        epochSeconds: Long,
        color: Ansi,
    ): String {
        val instant = Instant.ofEpochSecond(epochSeconds)
        val iso = ISO_FORMAT.format(instant)
        val rel = relativeTime(Instant.now().epochSecond - epochSeconds)
        return "$iso ${color.dim("($rel)")}"
    }

    private fun relativeTime(secondsAgo: Long): String {
        val s = if (secondsAgo < 0) -secondsAgo else secondsAgo
        val suffix = if (secondsAgo < 0) "from now" else "ago"
        val unit =
            when {
                s < 60 -> "${s}s"
                s < 3600 -> "${s / 60}m"
                s < 86_400 -> "${s / 3600}h"
                s < 30 * 86_400 -> "${s / 86_400}d"
                s < 365 * 86_400 -> "${s / (30 * 86_400)}mo"
                else -> "${s / (365 * 86_400)}y"
            }
        return "$unit $suffix"
    }

    private fun formatBytes(n: Long): String {
        if (n < 1024) return "$n B"
        val units = arrayOf("KiB", "MiB", "GiB", "TiB", "PiB")
        var v = n.toDouble() / 1024.0
        var i = 0
        while (v >= 1024.0 && i < units.size - 1) {
            v /= 1024.0
            i++
        }
        return "%.1f %s".format(v, units[i])
    }

    private val ISO_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss'Z'").withZone(ZoneOffset.UTC)
}

/**
 * Tiny ANSI helper. The standard contract:
 *   - colour on when stdout (or stderr) is a TTY,
 *   - off when piped or redirected,
 *   - `NO_COLOR=…` (any non-empty value) hard-disables,
 *   - `CLICOLOR_FORCE=1` re-enables even when piped.
 */
internal class Ansi(
    private val enabled: Boolean,
) {
    fun bold(s: String) = wrap(s, "[1m")

    fun dim(s: String) = wrap(s, "[2m")

    fun red(s: String) = wrap(s, "[31m")

    fun green(s: String) = wrap(s, "[32m")

    fun yellow(s: String) = wrap(s, "[33m")

    private fun wrap(
        s: String,
        code: String,
    ): String = if (enabled && s.isNotEmpty()) "$code$s[0m" else s

    companion object {
        private val noColor: Boolean = !System.getenv("NO_COLOR").isNullOrEmpty()
        private val forceColor: Boolean = System.getenv("CLICOLOR_FORCE") == "1"

        fun forStream(isStderr: Boolean): Ansi {
            if (noColor) return Ansi(false)
            if (forceColor) return Ansi(true)
            // System.console() is non-null only when both stdin and stdout are
            // connected to a terminal. Good enough for the common case (interactive
            // shell vs `amy ... | jq`); honor CLICOLOR_FORCE for the rest.
            val tty = System.console() != null
            return Ansi(tty)
        }
    }
}
