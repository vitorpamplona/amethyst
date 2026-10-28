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
package com.vitorpamplona.amethyst.commons.napplet

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Current NIP-5D manifest contract. Quartz 2.3's NappletManifest helpers still implement the
 * retired NIP-5A path/aggregate schema; do not use them for execution or publication.
 * Parsing does not verify signatures or fetch bytes: callers must verify both before execution.
 */
data class NappletManifestData(
    val kind: Int,
    val identifier: String,
    val artifactHash: String,
    val description: String,
    val required: List<String>,
    val optional: List<String>,
    val servers: List<String>,
    val title: String?,
    val source: String?,
    val icon: NappletManifestIcon?,
    val archetypes: List<String>,
    val intents: List<List<String>>,
) {
    /** The resolver still consumes paths; this one is derived solely from the signed x tag. */
    val paths: List<PathTag> get() = listOf(PathTag("/index.html", artifactHash))

    /** Optional integrations can be exposed by policy, but never make a launch incompatible. */
    val exposedDomains: List<String>
        get() = (required + optional).distinct().filter { it in NappletCapability.supportedNapDomains }

    val unsupportedRequired: List<String>
        get() = required.filter { it !in NappletCapability.supportedNapDomains }

    companion object {
        fun parse(event: Event): NappletManifestData? = parse(event.kind, event.tags, event.content)

        fun parse(
            kind: Int,
            tags: TagArray,
            content: String,
        ): NappletManifestData? {
            if (kind !in setOf(5129, 15129, 35129) || content.isBlank()) return null

            fun named(name: String) = tags.filter { it.firstOrNull() == name }
            val hashes = named("x")
            val hash = hashes.singleOrNull()?.takeIf { it.size == 2 }?.get(1) ?: return null
            if (!SHA256.matches(hash)) return null
            val identifiers = named("d")
            val identifier =
                if (kind == 35129) {
                    identifiers.singleOrNull()?.takeIf { it.size == 2 }?.get(1) ?: return null
                } else {
                    if (identifiers.isNotEmpty()) return null
                    ""
                }
            for (name in listOf("title", "source")) {
                val values = named(name)
                if (values.size > 1 || values.any { it.size != 2 }) return null
            }
            val source = named("source").firstOrNull()?.get(1)
            if (source != null && !validSource(source)) return null
            for (name in listOf("R", "O")) {
                if (named(name).any { it.size != 2 || !DOMAIN.matches(it[1]) }) return null
            }
            for (name in listOf("a", "A")) {
                val lineage = named(name)
                if (lineage.size > 1 || (kind != 5129 && lineage.isNotEmpty())) return null
            }
            // Icon errors are cosmetic and MUST NOT stop an otherwise valid napplet loading.
            val icon =
                named("icon")
                    .singleOrNull()
                    ?.takeIf {
                        it.size == 3 && SHA256.matches(it[1]) && it[2] in ICON_MIMES
                    }?.let { NappletManifestIcon(it[1], it[2]) }
            return NappletManifestData(
                kind,
                identifier,
                hash,
                content,
                named("R").map { it[1] }.distinct(),
                named("O").map { it[1] }.distinct(),
                named("server").mapNotNull { it.getOrNull(1) }.distinct(),
                named("title").firstOrNull()?.get(1),
                source,
                icon,
                named("z").mapNotNull { it.getOrNull(1) },
                named("i").map { it.drop(1) },
            )
        }

        /** Builds only the current single-artifact schema; never emits path/requires/description tags. */
        fun build(
            artifactHash: String,
            description: String,
            identifier: String? = null,
            required: List<String> = emptyList(),
            optional: List<String> = emptyList(),
            servers: List<String> = emptyList(),
            title: String? = null,
            source: String? = null,
            icon: NappletManifestIcon? = null,
            archetypes: List<String> = emptyList(),
            intents: List<List<String>> = emptyList(),
            createdAt: Long = TimeUtils.now(),
        ): EventTemplate<Event> {
            val kind = if (identifier == null) 15129 else 35129
            val tags =
                buildList {
                    identifier?.let { add(arrayOf("d", it)) }
                    add(arrayOf("x", artifactHash))
                    required.distinct().forEach { add(arrayOf("R", it)) }
                    optional.distinct().forEach { add(arrayOf("O", it)) }
                    servers.distinct().forEach { add(arrayOf("server", it)) }
                    title?.let { add(arrayOf("title", it)) }
                    source?.let { add(arrayOf("source", it)) }
                    icon?.let { add(arrayOf("icon", it.hash, it.mime)) }
                    archetypes.forEach { add(arrayOf("z", it)) }
                    intents.forEach { add((listOf("i") + it).toTypedArray()) }
                }.toTypedArray()
            requireNotNull(parse(kind, tags, description)) { "Invalid NIP-5D manifest" }
            require(icon == null || (SHA256.matches(icon.hash) && icon.mime in ICON_MIMES)) { "Invalid NIP-5D icon" }
            return EventTemplate(createdAt, kind, tags, description)
        }

        private val SHA256 = Regex("^[0-9a-f]{64}$")
        private val DOMAIN = Regex("^[a-z][a-z0-9-]*$")
        private val ICON_MIMES = setOf("image/png", "image/jpeg", "image/webp")
        private val SOURCE_URL = Regex("^(https?|ssh|git)://[^/\\s]+(?:/[^\\s]*)?$")
        private val SOURCE_SCP = Regex("^(?:[^@:/\\s]+@)?[A-Za-z0-9][A-Za-z0-9.-]+:[^\\s]+$")

        fun validSource(value: String): Boolean =
            SOURCE_URL.matches(value) ||
                (SOURCE_SCP.matches(value) && value.substringBefore(':').lowercase() !in setOf("file", "javascript", "data", "vbscript"))
    }
}

data class NappletManifestIcon(
    val hash: String,
    val mime: String,
)
