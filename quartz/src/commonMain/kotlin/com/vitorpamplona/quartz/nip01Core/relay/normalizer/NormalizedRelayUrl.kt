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
package com.vitorpamplona.quartz.nip01Core.relay.normalizer

import androidx.compose.runtime.Stable

@Stable
data class NormalizedRelayUrl(
    val url: String,
) : Comparable<NormalizedRelayUrl> {
    override fun compareTo(other: NormalizedRelayUrl) = url.compareTo(other.url)
}

fun NormalizedRelayUrl.displayUrl() =
    url
        .removePrefix("wss://")
        .removePrefix("ws://")
        .removeSuffix("/")

fun NormalizedRelayUrl.toHttp() =
    if (url.startsWith("wss://")) {
        "https${url.drop(3)}"
    } else if (url.startsWith("ws://")) {
        "http${url.drop(2)}"
    } else {
        "https://$url"
    }

fun NormalizedRelayUrl.isOnion() = url.contains(".onion/")

fun NormalizedRelayUrl.isLocalHost() = RelayUrlNormalizer.isLocalHost(this.url)
