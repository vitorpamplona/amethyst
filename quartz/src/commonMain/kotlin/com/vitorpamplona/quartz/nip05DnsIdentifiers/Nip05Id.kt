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
package com.vitorpamplona.quartz.nip05DnsIdentifiers

import androidx.compose.runtime.Stable

@Stable
data class Nip05Id(
    val name: String,
    val domain: String,
) {
    fun toValue(): String = assemble(name, domain)

    fun toUserUrl(): String = userUrl(name, domain)

    fun toDomainUrl(): String = domainUrl(domain)

    companion object {
        fun parse(nip05address: String): Nip05Id? {
            val parts = nip05address.trim().lowercase().split("@")

            return when (parts.size) {
                2 -> Nip05Id(parts[0], parts[1])
                1 -> Nip05Id(parts[0], "_")
                else -> null
            }
        }

        fun assemble(
            name: String,
            domain: String,
        ) = "$name@$domain"

        fun userUrl(
            name: String,
            domain: String,
        ) = "https://$domain/.well-known/nostr.json?name=$name"

        fun domainUrl(domain: String) = "https://$domain/.well-known/nostr.json"
    }
}
