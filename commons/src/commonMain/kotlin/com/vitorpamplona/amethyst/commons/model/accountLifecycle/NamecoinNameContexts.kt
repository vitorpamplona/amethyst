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
package com.vitorpamplona.amethyst.commons.model.accountLifecycle

private val NAMECOIN_LABEL = Regex("^(xn--)?[a-z0-9]+(-[a-z0-9]+)*$")

object NamecoinNameContexts {
    fun parse(
        identifier: String,
        subjectOnly: Boolean = false,
    ): NamecoinNameContext {
        require(identifier.isNotEmpty()) { "invalid identifier" }

        if (identifier.startsWith("d/") || identifier.startsWith("id/")) {
            val namespace = if (identifier.startsWith("d/")) LifecycleNameNamespace.D else LifecycleNameNamespace.ID
            val rawName = identifier.substringAfter('/')
            require(rawName.isNotEmpty() && !rawName.contains('/')) { "invalid Namecoin name" }
            val name = rawName.lowercase()
            require(NAMECOIN_LABEL.matches(name)) { "invalid Namecoin name" }
            return NamecoinNameContext(
                namespace = namespace,
                name = "${if (namespace == LifecycleNameNamespace.D) "d" else "id"}/$name",
                host = if (namespace == LifecycleNameNamespace.D) "$name.bit" else null,
                localPart = if (subjectOnly) null else "_",
            )
        }

        val localPart: String
        val rawHost: String
        if (identifier.contains('@')) {
            val parts = identifier.split('@')
            require(parts.size == 2 && parts[0].isNotEmpty() && parts[0] != "*") { "invalid localpart" }
            localPart = parts[0]
            rawHost = parts[1]
        } else {
            localPart = "_"
            rawHost = identifier
        }

        val host = rawHost.lowercase()
        require(host.endsWith(".bit")) { "identifier is not Namecoin" }
        val labels = host.removeSuffix(".bit").split('.')
        require(labels.isNotEmpty() && labels.all { NAMECOIN_LABEL.matches(it) }) { "invalid .bit host" }
        val name = labels.last()
        val mapPath = labels.dropLast(1).reversed()
        return NamecoinNameContext(
            namespace = LifecycleNameNamespace.D,
            name = "d/$name",
            host = host,
            localPart = if (subjectOnly) null else localPart,
            mapPath = mapPath,
        )
    }

    fun identifier(
        context: NamecoinNameContext,
        localPart: String,
    ): String? =
        when (context.namespace) {
            LifecycleNameNamespace.ID -> if (localPart == "_") context.name else null
            LifecycleNameNamespace.D -> {
                val host = requireNotNull(context.host)
                if (localPart == "_") host else "$localPart@$host"
            }
        }

    fun sameContext(
        first: NamecoinNameContext,
        second: NamecoinNameContext,
    ): Boolean = first.namespace == second.namespace && first.name == second.name && first.mapPath == second.mapPath
}
