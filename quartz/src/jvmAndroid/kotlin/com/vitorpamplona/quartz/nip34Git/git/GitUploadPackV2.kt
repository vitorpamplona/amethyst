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
package com.vitorpamplona.quartz.nip34Git.git

import java.io.ByteArrayOutputStream

/**
 * Pure (transport-free) encoders and decoders for the git `git-upload-pack`
 * protocol-v2 exchanges. Kept separate from [GitSmartHttpTransport] so the wire
 * formats can be unit-tested against captured server bytes without any network.
 */
object GitUploadPackV2 {
    fun lsRefsRequest(objectFormat: String): ByteArray =
        PktLineCodec.build {
            write(PktLineCodec.dataLine("command=ls-refs\n"))
            write(PktLineCodec.dataLine("object-format=$objectFormat\n"))
            write(PktLineCodec.DELIM)
            write(PktLineCodec.dataLine("symrefs\n"))
            write(PktLineCodec.dataLine("peel\n"))
            write(PktLineCodec.dataLine("ref-prefix HEAD\n"))
            write(PktLineCodec.dataLine("ref-prefix refs/heads/\n"))
            write(PktLineCodec.dataLine("ref-prefix refs/tags/\n"))
            write(PktLineCodec.FLUSH)
        }

    fun fetchRequest(
        objectFormat: String,
        wants: List<String>,
        deepen: Int?,
        filterBlobNone: Boolean,
    ): ByteArray =
        PktLineCodec.build {
            write(PktLineCodec.dataLine("command=fetch\n"))
            write(PktLineCodec.dataLine("object-format=$objectFormat\n"))
            write(PktLineCodec.DELIM)
            write(PktLineCodec.dataLine("no-progress\n"))
            wants.forEach { write(PktLineCodec.dataLine("want $it\n")) }
            if (deepen != null) write(PktLineCodec.dataLine("deepen $deepen\n"))
            if (filterBlobNone) write(PktLineCodec.dataLine("filter blob:none\n"))
            write(PktLineCodec.dataLine("done\n"))
            write(PktLineCodec.FLUSH)
        }

    fun parseCapabilities(lines: List<PktLine>): GitCapabilities {
        var agent: String? = null
        var objectFormat = "sha1"
        var supportsLsRefs = false
        var fetchFeatures = emptySet<String>()
        var hadFetch = false

        for (line in lines) {
            if (line !is PktLine.Data) continue
            val text = line.text()
            if (text.startsWith("# service=")) continue
            val eq = text.indexOf('=')
            val key = if (eq >= 0) text.substring(0, eq) else text
            val value = if (eq >= 0) text.substring(eq + 1) else ""
            when (key) {
                "agent" -> agent = value
                "object-format" -> if (value.isNotBlank()) objectFormat = value.trim()
                "ls-refs" -> supportsLsRefs = true
                "fetch" -> {
                    hadFetch = true
                    fetchFeatures =
                        value
                            .split(' ')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .toSet()
                }
            }
        }

        return GitCapabilities(agent, objectFormat, supportsLsRefs, fetchFeatures).also { it.rawHadFetch = hadFetch }
    }

    fun parseRefs(lines: List<PktLine>): List<GitRef> {
        val refs = ArrayList<GitRef>()
        for (line in lines) {
            if (line !is PktLine.Data) continue
            val text = line.text()
            if (text.isEmpty()) continue
            val parts = text.split(' ')
            if (parts.size < 2) continue
            var symref: String? = null
            for (i in 2 until parts.size) {
                val attr = parts[i]
                if (attr.startsWith("symref-target:")) symref = attr.substringAfter("symref-target:")
            }
            refs.add(GitRef(parts[0], parts[1], symref))
        }
        return refs
    }

    /** Demuxes the sideband-64k `packfile` section of a fetch response into raw pack bytes. */
    fun extractPack(lines: List<PktLine>): ByteArray {
        val pack = ByteArrayOutputStream()
        val errors = StringBuilder()
        var inPack = false
        for (line in lines) {
            when (line) {
                is PktLine.Flush -> if (inPack) break
                is PktLine.Delim -> {} // section separator
                is PktLine.ResponseEnd -> if (inPack) break
                is PktLine.Data -> {
                    if (!inPack) {
                        if (line.text() == "packfile") inPack = true
                        continue
                    }
                    val payload = line.payload
                    if (payload.isEmpty()) continue
                    when (payload[0].toInt() and 0xFF) {
                        1 -> pack.write(payload, 1, payload.size - 1) // pack data
                        2 -> {} // progress
                        3 -> errors.append(payload.decodeToString(1, payload.size)) // fatal
                    }
                }
            }
        }
        if (errors.isNotEmpty()) throw GitHttpException("git server error: $errors")
        val bytes = pack.toByteArray()
        if (bytes.size < 4) throw GitHttpException("empty packfile in fetch response")
        return bytes
    }
}
